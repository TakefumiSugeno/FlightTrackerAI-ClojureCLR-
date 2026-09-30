(ns flight-tracker-ai.infrastructure.google-flights-scraper-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.google-flights-scraper :as gf]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]))

(deftest test-build-search-url
  (testing "build-search-url creates valid Google Flights query URL for RoundTrip and OneWay"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          rt-task {:origin hnd
                   :destination cdg
                   :trip-type {:kind :round-trip
                               :outbound (DateOnly. 2026 5 1)
                               :inbound (DateOnly. 2026 5 8)}}
          ow-task {:origin hnd
                   :destination cdg
                   :trip-type {:kind :one-way
                               :outbound (DateOnly. 2026 5 1)}}]
      (let [rt-url (gf/build-search-url rt-task)]
        (is (str/includes? rt-url "Flights%20to%20CDG%20from%20HND%20on%202026-05-01%20through%202026-05-08"))
        (is (str/includes? rt-url "curr=JPY")))
      (let [ow-url (gf/build-search-url ow-task)]
        (is (str/includes? ow-url "Flights%20to%20CDG%20from%20HND%20on%202026-05-01"))
        (is (not (str/includes? ow-url "through")))))))

(deftest test-parse-offer-element
  (testing "parse-offer-element parses Google Flights DOM elements correctly"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          offer (gf/parse-offer-element task-id run-log-id "https://flights.google.com/test"
                                        "￥148,200" "全日空, シンガポール航空" "10:35 - 07:15+1"
                                        "22時間40分" "1回乗継" now)]
      (is (some? offer))
      (is (= 148200 (:price-jpy offer)))
      (is (= 1360 (:total-duration-minutes offer)))
      (is (= 1 (:stops-count offer)))
      (is (= "全日空, シンガポール航空" (:airlines-summary offer)))
      (is (= :google-flights (:provider offer)))))

  (testing "parse-offer-element returns nil when price is invalid"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          offer (gf/parse-offer-element task-id run-log-id "https://flights.google.com/test"
                                        "満席" "全日空" "10:35 - 07:15+1"
                                        "22時間40分" "直行便" now)]
      (is (nil? offer))))

  (testing "parse-offer-element handles direct flights and 2 stops"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          direct-offer (gf/parse-offer-element task-id run-log-id "url" "50,000円" "JAL" "10:00-11:00" "1時間" "直行便" now)
          two-stops-offer (gf/parse-offer-element task-id run-log-id "url" "50,000円" "ANA" "10:00-20:00" "10時間" "2回乗継" now)]
      (is (= 0 (:stops-count direct-offer)))
      (is (= 2 (:stops-count two-stops-offer)))))

  (testing "scrape-async handles nil page safely without throwing"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-item {:id task-id
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound (DateOnly. 2026 6 1)}}
          res (gf/scrape-async nil task-item run-log-id)]
      (is (= [] res)))))

(deftest test-parse-flight-number-from-itinerary
  (testing "parse-flight-number-from-itinerary extracts flight numbers correctly"
    (is (= "5J 5055" (gf/parse-flight-number-from-itinerary "itinerary=NRT-MNL-5J-5055-20270227")))
    (is (= "5J 5055" (gf/parse-flight-number-from-itinerary "NRT-MNL-5J-5055-20270227")))
    (is (= "NH 869" (gf/parse-flight-number-from-itinerary "HND-MNL-NH-869-20270227")))
    (is (= "5J 5065 ➔ 5J 2516" (gf/parse-flight-number-from-itinerary "NRT-CEB-5J-5065-20270227,CEB-MNL-5J-2516-20270228")))
    (is (nil? (gf/parse-flight-number-from-itinerary "")))
    (is (nil? (gf/parse-flight-number-from-itinerary nil)))))

(deftest test-parse-google-flights-price-avoids-time-strings
  (testing "parse-google-flights-price accurately extracts prices and ignores time representations"
    ;; Real Google Flights strings with times and prices
    (let [sample-label "往復の合計金額 34527 円～。 セブパシフィック航空 が運航する直行便。 土曜日, 2月 27 12:50 成田国際空港発、土曜日, 2月 27 17:30 Ninoy Aquino International Airport着。 合計時間 5時間 40分。   フライトを選択"]
      (is (= 34527 (gf/parse-google-flights-price sample-label))))
    (is (= 148200 (gf/parse-google-flights-price "￥148,200")))
    (is (= 48200 (gf/parse-google-flights-price "48,200 円")))
    (is (= 34144 (gf/parse-google-flights-price "34144 円")))
    ;; Time strings must NOT be mistaken for prices!
    (is (nil? (gf/parse-google-flights-price "8:50 – 15:15")))
    (is (nil? (gf/parse-google-flights-price "12:50発 17:30着")))
    (is (nil? (gf/parse-google-flights-price "5時間 40分")))
    (is (nil? (gf/parse-google-flights-price "満席")))))

(deftest test-parse-offer-element-with-flight-key
  (testing "parse-offer-element creates offer containing flight-number and flight-key"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          outbound (DateOnly. 2027 2 27)
          itinerary "NRT-MNL-5J-5055-20270227"
          price-text "往復の合計金額 34527 円～"
          offer (gf/parse-offer-element task-id run-log-id "https://flights.google.com/test"
                                        price-text "セブパシフィック航空" "12:50 - 17:30"
                                        "5時間40分" "直行便" itinerary outbound now)]
      (is (some? offer))
      (is (= 34527 (:price-jpy offer)))
      (is (= "5J 5055" (:flight-number offer)))
      (is (= (str task-id "_5J 5055_2027-02-27") (:flight-key offer)))
      (is (= "5J 5055" (:flight-number (first (:segments offer))))))))

(deftest test-offline-sample-html-card-extraction
  (testing "offline sample HTML parses flight card data without anomalies"
    (let [sample-path "doc/work/GoogleFlightサンプル/東京都発シティ・オブ・マニラ行き _ Google フライト.html"]
      (when (System.IO.File/Exists sample-path)
        (let [html (System.IO.File/ReadAllText sample-path)
              cards (gf/extract-cards-from-html html)]
          (is (pos? (count cards)))
          (let [first-card (first cards)]
            (is (= 34527 (:price-jpy first-card)))
            (is (= "5J 5055" (:flight-number first-card)))
            (is (not= 8501515 (:price-jpy first-card)))
            (is (< (:price-jpy first-card) 500000))))))))
