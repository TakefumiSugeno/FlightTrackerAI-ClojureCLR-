(ns flight-tracker-ai.infrastructure.skyscanner-scraper
  (:require [flight-tracker-ai.infrastructure.app-logger :as logger]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]))

(defn build-search-url [task-item]
  (let [origin-str (.ToLowerInvariant ^String (domain/iata-code-value (:origin task-item)))
        dest-str (.ToLowerInvariant ^String (domain/iata-code-value (:destination task-item)))
        trip (:trip-type task-item)]
    (if (= (:kind trip) :round-trip)
      (let [ob-date (.ToString ^DateOnly (:outbound trip) "yyMMdd")
            ib-date (.ToString ^DateOnly (:inbound trip) "yyMMdd")]
        (str "https://www.skyscanner.jp/transport/flights/" origin-str "/" dest-str "/" ob-date "/" ib-date
             "/?adultsv2=1&cabinclass=economy&currency=JPY"))
      (let [ob-date (.ToString ^DateOnly (:outbound trip) "yyMMdd")]
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

(defn scrape-async [page task-item ^Guid run-log-id]
  (try
    (let [url (build-search-url task-item)]
      (when page
        (scraper-common/await-task (.GotoAsync page url)))
      [])
    (catch Exception ex
      (logger/error-ex "Skyscanner" "スクレイピング例外" ex)
      [])))
