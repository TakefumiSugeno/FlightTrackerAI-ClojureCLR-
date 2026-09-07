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
      (is (nil? offer)))))
