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
       :provider :google-flights
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

(defn scrape-async [page task-item ^Guid run-log-id]
  (if-not page
    []
    (try
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
              (logger/success "Scraper" (str "[GoogleFlights] 検索画面キャプチャを保存しました: " screenshot-path)))
            (catch Exception ex
              (logger/warn "Scraper" (str "[GoogleFlights] キャプチャ保存スキップ: " (.Message ex)))))

          (let [captured-at (DateTimeOffset/UtcNow)
                offers (atom [])]
            (doseq [item list-items]
              (try
                (let [price-el (scraper-common/await-task (.QuerySelectorAsync item ".YMlIz.FpEdX span, span[aria-label*='円'], span[aria-label*='JPY'], [class*='YMlIz']"))
                      airline-el (scraper-common/await-task (.QuerySelectorAsync item ".sSHqwe.tPgKwe.ogfYpf span, .Ir0Voe .sSHqwe, [class*='sSHqwe']"))
                      times-el (scraper-common/await-task (.QuerySelectorAsync item ".dpKdp span, .mv1WYe span, [class*='dpKdp']"))
                      duration-el (scraper-common/await-task (.QuerySelectorAsync item ".AdWm1c.gvkrdb, .Ak5kof, [class*='gvkrdb']"))
                      stops-el (scraper-common/await-task (.QuerySelectorAsync item ".EfT7Ae .VG3hNb, .EfT7Ae span, [class*='VG3hNb']"))
                      price-text (if price-el (scraper-common/await-task (.InnerTextAsync price-el)) "")
                      airline-text (if airline-el (scraper-common/await-task (.InnerTextAsync airline-el)) "航空会社情報なし")
                      times-text (if times-el (scraper-common/await-task (.InnerTextAsync times-el)) "")
                      duration-text (if duration-el (scraper-common/await-task (.InnerTextAsync duration-el)) "")
                      stops-text (if stops-el (scraper-common/await-task (.InnerTextAsync stops-el)) "直行便")]
                  (when-let [offer (parse-offer-element (:id task-item) run-log-id url price-text airline-text times-text duration-text stops-text captured-at)]
                    (swap! offers conj offer)))
                (catch Exception _ nil)))
            @offers)))
      (catch Exception ex
        (logger/error-ex "GoogleFlights" "スクレイピング例外" ex)
        []))))
