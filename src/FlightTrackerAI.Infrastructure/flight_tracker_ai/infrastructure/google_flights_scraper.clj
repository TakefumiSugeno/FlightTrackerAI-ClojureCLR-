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

(ns flight-tracker-ai.infrastructure.google-flights-scraper
  (:require [flight-tracker-ai.infrastructure.app-logger :as logger]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset TimeSpan DateOnly Nullable]
           [System.IO Path]
           [Microsoft.Playwright IPage PageGotoOptions WaitUntilState PageScreenshotOptions]))

(defn build-search-url [task-item]
  (let [origin-str (domain/iata-code-value (:origin task-item))
        dest-str (domain/iata-code-value (:destination task-item))
        trip (:trip-type task-item)
        ob ^DateOnly (or (:outbound trip) (:outbound-date trip))
        ib ^DateOnly (or (:inbound trip) (:inbound-date trip))]
    (if (= (:kind trip) :round-trip)
      (let [ob-date (.ToString ob "yyyy-MM-dd")
            ib-date (.ToString ib "yyyy-MM-dd")]
        (str "https://www.google.com/travel/flights?q=Flights%20to%20" dest-str
             "%20from%20" origin-str "%20on%20" ob-date "%20through%20" ib-date "&hl=ja&curr=JPY"))
      (let [ob-date (.ToString ob "yyyy-MM-dd")]
        (str "https://www.google.com/travel/flights?q=Flights%20to%20" dest-str
             "%20from%20" origin-str "%20on%20" ob-date "&hl=ja&curr=JPY")))))

(defn parse-flight-number-from-itinerary [itinerary-str]
  (when-not (str/blank? itinerary-str)
    (let [cleaned (if (.StartsWith (str itinerary-str) "itinerary=")
                    (subs (str itinerary-str) 10)
                    (str itinerary-str))
          parts (str/split cleaned #",")
          flight-nums (keep (fn [p]
                              (let [tokens (str/split (.Trim (str p)) #"-")]
                                (when (>= (count tokens) 4)
                                  (str (nth tokens 2) " " (nth tokens 3)))))
                            parts)]
      (when (seq flight-nums)
        (str/join " ➔ " flight-nums)))))

(defn parse-google-flights-price [text]
  (when-not (str/blank? text)
    (let [s (str text)]
      (if-let [m (re-find #"(\d[\d,]*)\s*円" s)]
        (let [num-str (str/replace (second m) "," "")]
          (try (Int64/Parse num-str) (catch Exception _ nil)))
        (if-let [m2 (re-find #"[￥¥](\d[\d,]*)" s)]
          (let [num-str (str/replace (second m2) "," "")]
            (try (Int64/Parse num-str) (catch Exception _ nil)))
          nil)))))

(defn parse-offer-element
  ([^Guid task-id ^Guid run-log-id ^String booking-url price-text airlines-text times-text duration-text stops-text ^DateTimeOffset captured-at]
   (parse-offer-element task-id run-log-id booking-url price-text airlines-text times-text duration-text stops-text nil nil captured-at))
  ([^Guid task-id ^Guid run-log-id ^String booking-url price-text airlines-text times-text duration-text stops-text itinerary-or-flight-no outbound-date ^DateTimeOffset captured-at]
   (if-let [price (or (parse-google-flights-price price-text)
                      (scraper-common/parse-price-jpy price-text))]
     (let [total-duration (scraper-common/parse-duration-minutes duration-text)
           stops-str (str stops-text)
           stops-count (cond
                         (or (.Contains stops-str "直行") (.Contains stops-str "0")) 0
                         (.Contains stops-str "1") 1
                         (.Contains stops-str "2") 2
                         :else 1)
           airlines-summary (.Trim (str airlines-text))
           flight-number (when-not (str/blank? itinerary-or-flight-no)
                           (if (or (.Contains (str itinerary-or-flight-no) "-")
                                   (.StartsWith (str itinerary-or-flight-no) "itinerary="))
                             (parse-flight-number-from-itinerary itinerary-or-flight-no)
                             (str itinerary-or-flight-no)))
           flight-key (when (and task-id flight-number outbound-date)
                        (domain/build-flight-key task-id flight-number outbound-date))
           segment {:leg-index 0
                    :segment-index 0
                    :departure-airport ""
                    :arrival-airport ""
                    :marketing-airline airlines-summary
                    :operating-airline nil
                    :flight-number flight-number
                    :departure-time captured-at
                    :arrival-time (.AddMinutes captured-at (double total-duration))
                    :flight-duration-minutes total-duration
                    :layover-minutes-next nil}]
       {:id (Guid/NewGuid)
        :task-id task-id
        :run-log-id run-log-id
        :provider :google-flights
        :flight-number flight-number
        :flight-key flight-key
        :airlines-summary airlines-summary
        :departure-time captured-at
        :arrival-time (.AddMinutes captured-at (double total-duration))
        :total-duration-minutes total-duration
        :stops-count stops-count
        :segments [segment]
        :price-jpy price
        :booking-url booking-url
        :captured-at captured-at})
     nil)))

(def viewport-width 1440)
(def viewport-height 900)

(defn extract-cards-from-html
  ([^String html] (extract-cards-from-html html nil))
  ([^String html limit]
   (if (str/blank? html)
     []
     (let [card-matches (re-seq #"<li[^>]*class=\"[^\"]*pIav2d[^\"]*\"[^>]*>[\s\S]*?</li>" html)
           cards (mapv (fn [card]
                         (let [itin (second (re-find #"itinerary=([^\s\"'&>]+)" card))
                               fn-str (parse-flight-number-from-itinerary itin)
                               price-label (second (re-find #"aria-label=\"([^\"]*円[^\"]*)\"" card))
                               price (or (parse-google-flights-price price-label)
                                         (when-let [p-raw (second (re-find #"(\d[\d,]*)\s*円" card))]
                                           (try (Int64/Parse (str/replace p-raw "," "")) (catch Exception _ nil))))]
                           {:flight-number fn-str
                            :price-jpy price}))
                       card-matches)]
       (if (and limit (pos? limit))
         (vec (take limit cards))
         cards)))))

(defn apply-stops-filter-async [page max-stops]
  (when (and page (#{:direct-only :one-stop} max-stops))
    (try
      (let [stops-btn (scraper-common/await-task (.QuerySelectorAsync page "button[aria-label*='経由地数'], button[aria-label*='Stops']"))]
        (when stops-btn
          (scraper-common/await-task (.ClickAsync stops-btn nil))
          (scraper-common/await-task (.WaitForTimeoutAsync page (float 500.0)))
          (let [target-selector (if (= max-stops :direct-only)
                                  "label:has-text('直行便のみ'), [aria-label*='直行便のみ'], [aria-label*='Nonstop only']"
                                  "label:has-text('1 回以下'), [aria-label*='1 回以下'], [aria-label*='1 stop or fewer']")
                radio-el (scraper-common/await-task (.QuerySelectorAsync page target-selector))]
            (when radio-el
              (scraper-common/await-task (.ClickAsync radio-el nil)))
            (try (scraper-common/await-task (.Keyboard.PressAsync page "Escape")) (catch Exception _ nil))
            (scraper-common/await-task (.WaitForTimeoutAsync page (float 1000.0))))))
      (catch Exception ex
        (logger/warn "GoogleFlights" (str "経由地フィルター適用スキップ: " (.Message ex)))))))

(defn apply-cheapest-sort-async [page]
  (when page
    (try
      (let [sort-btn (scraper-common/await-task (.QuerySelectorAsync page "button[aria-label*='並べ替え'], button[aria-label*='Sort by'], button[aria-label*='フライト順']"))]
        (when sort-btn
          (scraper-common/await-task (.ClickAsync sort-btn nil))
          (scraper-common/await-task (.WaitForTimeoutAsync page (float 500.0)))
          (let [cheapest-item (scraper-common/await-task (.QuerySelectorAsync page "[role='menuitemradio']:has-text('料金が安い順'), [role='option']:has-text('料金が安い順'), :text('料金が安い順'), :text('Cheapest')"))]
            (when cheapest-item
              (scraper-common/await-task (.ClickAsync cheapest-item nil))
              (scraper-common/await-task (.WaitForTimeoutAsync page (float 1500.0)))))))
      (catch Exception ex
        (logger/warn "GoogleFlights" (str "安い順ソート適用スキップ: " (.Message ex)))))))

(defn scrape-async [page task-item ^Guid run-log-id]
  (if-not page
    []
    (try
      (try
        (scraper-common/await-task (.SetViewportSizeAsync page viewport-width viewport-height))
        (catch Exception _ nil))

      (let [url (build-search-url task-item)
            goto-opts (PageGotoOptions.)]
        (set! (.WaitUntil goto-opts) WaitUntilState/DOMContentLoaded)
        (set! (.Timeout goto-opts) (float 30000.0))
        (scraper-common/await-task (.GotoAsync page url goto-opts))

        ;; Cookie 同意やダイアログのスキップ (存在する場合)
        (try
          (when-let [consent-btn (scraper-common/await-task (.QuerySelectorAsync page "button[aria-label*='同意'], button[aria-label*='Accept']"))]
            (scraper-common/await-task (.ClickAsync consent-btn nil))
            (scraper-common/await-task (.WaitForTimeoutAsync page (float 1000.0))))
          (catch Exception _ nil))

        ;; 2段階目: 経由地数フィルター適用
        (apply-stops-filter-async page (:max-stops task-item))

        ;; 2段階目: 安い順ソート適用
        (apply-cheapest-sort-async page)

        ;; 検索結果カードの表示待機 (loop/recur による安全なポーリング)
        (let [selector-query "li.pIav2d, div[role='listitem'].pIav2d, div.yR1fYc, [class*='pIav2d']"
              list-items (loop [attempts-remaining 10]
                           (let [items (scraper-common/await-task (.QuerySelectorAllAsync page selector-query))]
                             (if (or (> (.Count items) 0) (<= attempts-remaining 0))
                               items
                               (do
                                 (scraper-common/await-task (.WaitForTimeoutAsync page (float 500.0)))
                                 (recur (dec attempts-remaining))))))]

          ;; 画面キャプチャ保存 (doc/work/screenshots/)
          (try
            (let [screenshot-dir (scraper-common/get-screenshot-dir)
                  timestamp (.ToString (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0)) "yyyyMMdd-HHmmss")
                  task-id-short (if (and (:id task-item) (not= (:id task-item) Guid/Empty))
                                  (.Substring (.ToString (:id task-item) "N") 0 8)
                                  "test")
                  screenshot-path (Path/Combine screenshot-dir (str timestamp "_GoogleFlights_" task-id-short ".png"))
                  ss-opts (PageScreenshotOptions.)]
              (set! (.Path ss-opts) screenshot-path)
              (set! (.FullPage ss-opts) false)
              (scraper-common/await-task (.ScreenshotAsync page ss-opts))
              (logger/success "Scraper" (str "[GoogleFlights] 検索画面キャプチャを保存しました (1440x900): " screenshot-path)))
            (catch Exception ex
              (logger/warn "Scraper" (str "[GoogleFlights] キャプチャ保存スキップ: " (.Message ex)))))

          (let [captured-at (DateTimeOffset/UtcNow)
                max-results (long (or (:max-results-count task-item) 10))
                target-items (take max-results list-items)
                outbound-date (let [trip (:trip-type task-item)]
                                (or (:outbound trip) (:outbound-date trip)))
                offers (atom [])]
            (doseq [item target-items]
              (try
                (let [price-el (scraper-common/await-task (.QuerySelectorAsync item ".JMc5Xc, span[aria-label*='円'], .YMlIz.FpEdX span, span[aria-label*='JPY'], [class*='YMlIz']"))
                      price-text (if price-el
                                   (or (scraper-common/await-task (.GetAttributeAsync price-el "aria-label"))
                                       (scraper-common/await-task (.InnerTextAsync price-el)))
                                   "")
                      airline-el (scraper-common/await-task (.QuerySelectorAsync item ".sSHqwe.tPgKwe.ogfYpf span, .Ir0Voe .sSHqwe, [class*='sSHqwe']"))
                      times-el (scraper-common/await-task (.QuerySelectorAsync item ".dpKdp span, .mv1WYe span, [class*='dpKdp']"))
                      duration-el (scraper-common/await-task (.QuerySelectorAsync item ".AdWm1c.gvkrdb, .Ak5kof, [class*='gvkrdb']"))
                      stops-el (scraper-common/await-task (.QuerySelectorAsync item ".EfT7Ae .VG3hNb, .EfT7Ae span, [class*='VG3hNb']"))
                      itin-str (or (scraper-common/await-task (.GetAttributeAsync item "itinerary"))
                                   (when-let [itin-el (scraper-common/await-task (.QuerySelectorAsync item "[itinerary]"))]
                                     (scraper-common/await-task (.GetAttributeAsync itin-el "itinerary"))))
                      airline-text (if airline-el (scraper-common/await-task (.InnerTextAsync airline-el)) "航空会社情報なし")
                      times-text (if times-el (scraper-common/await-task (.InnerTextAsync times-el)) "")
                      duration-text (if duration-el (scraper-common/await-task (.InnerTextAsync duration-el)) "")
                      stops-text (if stops-el (scraper-common/await-task (.InnerTextAsync stops-el)) "直行便")]
                  (when-let [offer (parse-offer-element (:id task-item) run-log-id url price-text airline-text times-text duration-text stops-text itin-str outbound-date captured-at)]
                    (swap! offers conj offer)))
                (catch Exception _ nil)))
            @offers)))
      (catch Exception ex
        (logger/error-ex "GoogleFlights" "スクレイピング例外" ex)
        []))))
