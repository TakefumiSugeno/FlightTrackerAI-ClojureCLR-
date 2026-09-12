;; Playwright .NET 依存アセンブリの自動ロード
(let [curr (System.IO.Directory/GetCurrentDirectory)
      base (.. System.AppDomain -CurrentDomain -BaseDirectory)
      combine (fn [& parts] (System.IO.Path/Combine (into-array String (map str parts))))
      candidates [(combine curr "src" "FlightTrackerAI.Infrastructure" "bin" "Debug" "net10.0")
                  (combine base "src" "FlightTrackerAI.Infrastructure" "bin" "Debug" "net10.0")
                  (combine base "..")
                  base]]
  (doseq [dir candidates]
    (when (System.IO.Directory/Exists dir)
      (let [playwright-dll (System.IO.Path/Combine dir "Microsoft.Playwright.dll")]
        (when (System.IO.File/Exists playwright-dll)
          (try (System.Reflection.Assembly/LoadFrom playwright-dll) (catch System.Exception _ nil)))))))

(ns flight-tracker-ai.infrastructure.skyscanner-scraper
  (:require [flight-tracker-ai.infrastructure.app-logger :as logger]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset TimeSpan DateOnly Nullable Math String]
           [System.IO Path]
           [Microsoft.Playwright IPage PageGotoOptions WaitUntilState PageScreenshotOptions]))

(defn build-search-url [task-item]
  (let [origin-str (.ToLowerInvariant ^String (domain/iata-code-value (:origin task-item)))
        dest-str (.ToLowerInvariant ^String (domain/iata-code-value (:destination task-item)))
        trip (:trip-type task-item)
        ob ^DateOnly (or (:outbound trip) (:outbound-date trip))
        ib ^DateOnly (or (:inbound trip) (:inbound-date trip))]
    (if (= (:kind trip) :round-trip)
      (let [ob-date (.ToString ob "yyMMdd")
            ib-date (.ToString ib "yyMMdd")]
        (str "https://www.skyscanner.jp/transport/flights/" origin-str "/" dest-str "/" ob-date "/" ib-date
             "/?adultsv2=1&cabinclass=economy&currency=JPY"))
      (let [ob-date (.ToString ob "yyMMdd")]
        (str "https://www.skyscanner.jp/transport/flights/" origin-str "/" dest-str "/" ob-date
             "/?adultsv2=1&cabinclass=economy&currency=JPY")))))

(defn parse-offer-element
  [^Guid task-id ^Guid run-log-id ^String booking-url price-text airlines-text times-text duration-text stops-text ^DateTimeOffset captured-at]
  (if-let [price (scraper-common/parse-price-jpy price-text)]
    (let [total-duration (scraper-common/parse-duration-minutes duration-text)
          stops-str (str stops-text)
          stops-count (cond
                        (or (.Contains stops-str "直行") (.Contains stops-str "0")) 0
                        (.Contains stops-str "1") 1
                        (.Contains stops-str "2") 2
                        :else 1)
          airlines-summary (.Trim (str airlines-text))
          segment {:leg-index 0
                   :segment-index 0
                   :departure-airport ""
                   :arrival-airport ""
                   :marketing-airline airlines-summary
                   :operating-airline nil
                   :flight-number nil
                   :departure-time captured-at
                   :arrival-time (.AddMinutes captured-at (double total-duration))
                   :flight-duration-minutes total-duration
                   :layover-minutes-next nil}]
      {:id (Guid/NewGuid)
       :task-id task-id
       :run-log-id run-log-id
       :provider :skyscanner
       :airlines-summary airlines-summary
       :departure-time captured-at
       :arrival-time (.AddMinutes captured-at (double total-duration))
       :total-duration-minutes total-duration
       :stops-count stops-count
       :segments [segment]
       :price-jpy price
       :booking-url booking-url
       :captured-at captured-at})
    nil))

(defn detect-bot-challenge [title content px-element-found?]
  (let [title-lower (when title (str/lower-case (str title)))
        content-lower (when content (str/lower-case (str content)))]
    (boolean
     (or (true? px-element-found?)
         (and title-lower (or (.Contains ^String title-lower "robot")
                              (.Contains ^String title-lower "person or a robot")))
         (and content-lower (.Contains ^String content-lower "press & hold"))))))

(defn scrape-async [page task-item ^Guid run-log-id]
  (if-not page
    []
    (try
      (let [url (build-search-url task-item)]
        ;; 1. トップページ事前ウォームアップ
        (try
          (logger/info "Scraper" "[Skyscanner] セッション確立のためトップページへ初期アクセスします...")
          (let [top-opts (PageGotoOptions.)]
            (set! (.WaitUntil top-opts) WaitUntilState/DOMContentLoaded)
            (set! (.Timeout top-opts) (float 20000.0))
            (scraper-common/await-task (.GotoAsync page "https://www.skyscanner.jp/" top-opts))
            (scraper-common/await-task (.WaitForTimeoutAsync page (float 1500.0)))

            ;; 自然なマウス動作のエミュレーション
            (scraper-common/await-task (.MoveAsync (.Mouse page) (float 150.0) (float 250.0) nil))
            (scraper-common/await-task (.MoveAsync (.Mouse page) (float 350.0) (float 450.0) nil))

            ;; Cookie 同意バナーがあれば受諾
            (when-let [cookie-btn (scraper-common/await-task
                                   (.QuerySelectorAsync page "#accept-cookie-button, button[id*='accept'], button[aria-label*='同意'], button[data-testid*='cookie-policy-accept'], button[data-testid*='accept-button'], button[id*='accept-all']"))]
              (scraper-common/await-task (.ClickAsync cookie-btn nil))
              (scraper-common/await-task (.WaitForTimeoutAsync page (float 500.0)))))
          (catch Exception ex
            (logger/warn "Scraper" (str "[Skyscanner] トップページ初期アクセススキップ: " (.Message ex)))))

        ;; 2. Referer をトップページに設定して検索ページへアクセス
        (logger/info "Scraper" (str "[Skyscanner] 検索ページへアクセスします: " url))
        (let [search-opts (PageGotoOptions.)]
          (set! (.WaitUntil search-opts) WaitUntilState/DOMContentLoaded)
          (set! (.Timeout search-opts) (float 60000.0))
          (set! (.Referer search-opts) "https://www.skyscanner.jp/")
          (scraper-common/await-task (.GotoAsync page url search-opts))
          (scraper-common/await-task (.WaitForTimeoutAsync page (float 3000.0))))

        ;; 3. 検索結果カードの表示待機 & Bot検知ポーリング (loop/recur)
        (let [selector-query "div[data-testid='flight-card'], [data-testid='itinerary-card'], div[class*='FlightCard_'], div[class*='FlightsResults_dayViewItems__'] > div, [role='listitem']"
              list-items
              (loop [attempts-remaining 25
                     has-warned-bot false]
                (let [items (scraper-common/await-task (.QuerySelectorAllAsync page selector-query))]
                  (if (> (.Count items) 0)
                    (do
                      (when has-warned-bot
                        (logger/success "Scraper" "[Skyscanner] Bot検証の突破に成功し、検索結果の読み込みを完了しました。"))
                      items)
                    (if (<= attempts-remaining 0)
                      items
                      (let [bot-check (try
                                        (let [title (scraper-common/await-task (.TitleAsync page))
                                              px-el (scraper-common/await-task
                                                     (.QuerySelectorAsync page "#px-captcha, .px-captcha, #px-captcha-container, div[id*='px-captcha'], [class*='press-and-hold'], iframe[src*='captcha']"))
                                              content (scraper-common/await-task (.ContentAsync page))
                                              detected (detect-bot-challenge title content (some? px-el))]
                                          [detected px-el])
                                        (catch Exception _ [false nil]))
                            is-bot (first bot-check)
                            px-el (second bot-check)
                            [next-attempts warned]
                            (if (and is-bot (not has-warned-bot))
                              (do
                                (if (:is-headless task-item)
                                  (logger/warn "Scraper" "[Skyscanner] Bot検証画面を検知しました。自動長押しを試行中... 解除されない場合は画面上部の「🌐 ブラウザを開いて巡回」で一度手動認証を行ってください。")
                                  (logger/warn "Scraper" "[Skyscanner] Bot検証画面 (PerimeterX: PRESS & HOLD) を検知しました。有頭ブラウザ画面でボタンを長押しして認証を解除してください（最大60秒待機中...）。"))

                                ;; Bot検証画面キャプチャ保存
                                (try
                                  (let [screenshot-dir (scraper-common/get-screenshot-dir)
                                        timestamp (.ToString (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0)) "yyyyMMdd-HHmmss")
                                        task-id-short (if (and (:id task-item) (not= (:id task-item) Guid/Empty))
                                                        (.Substring (.ToString (:id task-item) "N") 0 8)
                                                        "test")
                                        bot-ss-path (Path/Combine screenshot-dir (str timestamp "_Skyscanner_BotChallenge_" task-id-short ".png"))
                                        ss-opts (PageScreenshotOptions.)]
                                    (set! (.Path ss-opts) bot-ss-path)
                                    (set! (.FullPage ss-opts) false)
                                    (scraper-common/await-task (.ScreenshotAsync page ss-opts))
                                    (logger/info "Scraper" (str "[Skyscanner] Bot検証画面キャプチャを保存しました: " bot-ss-path)))
                                  (catch Exception _ nil))

                                ;; 自動長押し試行
                                (when px-el
                                  (try
                                    (when-let [box (scraper-common/await-task (.BoundingBoxAsync px-el))]
                                      (let [cx (+ (.X box) (/ (.Width box) 2.0))
                                            cy (+ (.Y box) (/ (.Height box) 2.0))]
                                        (scraper-common/await-task (.MoveAsync (.Mouse page) (float cx) (float cy) nil))
                                        (scraper-common/await-task (.DownAsync (.Mouse page) nil))
                                        (scraper-common/await-task (.WaitForTimeoutAsync page (float 5500.0)))
                                        (scraper-common/await-task (.UpAsync (.Mouse page) nil))
                                        (logger/info "Scraper" "[Skyscanner] 自動長押し解除アクションを実行しました。")))
                                    (catch Exception ex
                                      (logger/warn "Scraper" (str "[Skyscanner] 自動長押し試行スキップ: " (.Message ex))))))

                                (let [target-attempts (if (:is-headless task-item) 10 60)]
                                  [(Math/Max (long attempts-remaining) (long target-attempts)) true]))
                              [(dec attempts-remaining) has-warned-bot])]

                        (scraper-common/await-task (.WaitForTimeoutAsync page (float 1000.0)))
                        (recur next-attempts warned))))))]

          ;; スクリーンショットキャプチャ保存 (doc/work/screenshots/)
          (try
            (let [screenshot-dir (scraper-common/get-screenshot-dir)
                  timestamp (.ToString (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0)) "yyyyMMdd-HHmmss")
                  task-id-short (if (and (:id task-item) (not= (:id task-item) Guid/Empty))
                                  (.Substring (.ToString (:id task-item) "N") 0 8)
                                  "test")
                  screenshot-path (Path/Combine screenshot-dir (str timestamp "_Skyscanner_" task-id-short ".png"))
                  ss-opts (PageScreenshotOptions.)]
              (set! (.Path ss-opts) screenshot-path)
              (set! (.FullPage ss-opts) false)
              (scraper-common/await-task (.ScreenshotAsync page ss-opts))
              (logger/success "Scraper" (str "[Skyscanner] 検索画面キャプチャを保存しました: " screenshot-path)))
            (catch Exception ex
              (logger/warn "Scraper" (str "[Skyscanner] キャプチャ保存スキップ: " (.Message ex)))))

          (let [captured-at (DateTimeOffset/UtcNow)
                offers (atom [])]
            (doseq [item list-items]
              (try
                (let [price-el (scraper-common/await-task (.QuerySelectorAsync item "span[data-testid='price'], .Price_mainPriceContainer__, span[class*='Price_mainPrice'], [class*='price']"))
                      airline-el (scraper-common/await-task (.QuerySelectorAsync item "img[alt], .LogoImage_container__ span, [class*='LogoImage_'], [class*='airline']"))
                      duration-el (scraper-common/await-task (.QuerySelectorAsync item "span[data-testid='duration'], .Duration_duration__, [class*='Duration_duration'], [class*='duration']"))
                      stops-el (scraper-common/await-task (.QuerySelectorAsync item "span[data-testid='stops'], .Stops_stops__, [class*='Stops_stops'], [class*='stops']"))
                      times-el (scraper-common/await-task (.QuerySelectorAsync item "span[data-testid='departure-time'], span[data-testid='arrival-time'], .DepartArrivalTime_time__, [class*='LegDetails_times'], [class*='departTime']"))
                      price-text (if price-el (scraper-common/await-task (.InnerTextAsync price-el)) "")
                      airline-text (if-not airline-el
                                     "航空会社情報なし"
                                     (let [alt (scraper-common/await-task (.GetAttributeAsync airline-el "alt"))]
                                       (if (not (str/blank? alt))
                                         alt
                                         (scraper-common/await-task (.InnerTextAsync airline-el)))))
                      times-text (if times-el (scraper-common/await-task (.InnerTextAsync times-el)) "")
                      duration-text (if duration-el (scraper-common/await-task (.InnerTextAsync duration-el)) "")
                      stops-text (if stops-el (scraper-common/await-task (.InnerTextAsync stops-el)) "直行便")]
                  (when-let [offer (parse-offer-element (:id task-item) run-log-id url price-text airline-text times-text duration-text stops-text captured-at)]
                    (swap! offers conj offer)))
                (catch Exception _ nil)))
            @offers)))
      (catch Exception ex
        (logger/error-ex "Skyscanner" "スクレイピング例外" ex)
        []))))
