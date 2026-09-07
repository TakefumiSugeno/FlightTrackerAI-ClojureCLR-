(ns flight-tracker-ai.infrastructure.skyscanner-scraper-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.skyscanner-scraper :as ss]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]))

(deftest test-build-search-url
  (testing "build-search-url creates valid Skyscanner query URL for RoundTrip and OneWay"
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
      (let [rt-url (ss/build-search-url rt-task)]
        (is (str/includes? rt-url "hnd/cdg/260501/260508"))
        (is (str/includes? rt-url "currency=JPY")))
      (let [ow-url (ss/build-search-url ow-task)]
        (is (str/includes? ow-url "hnd/cdg/260501"))
        (is (not (str/includes? ow-url "260508")))
        (is (str/includes? ow-url "currency=JPY"))))))

(deftest test-parse-offer-element
  (testing "parse-offer-element parses Skyscanner DOM elements correctly"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          offer (ss/parse-offer-element task-id run-log-id "https://skyscanner.jp/test"
                                        "152,000円" "エールフランス" "" "14h 35m" "直行便" now)]
      (is (some? offer))
      (is (= 152000 (:price-jpy offer)))
      (is (= 875 (:total-duration-minutes offer)))
      (is (= 0 (:stops-count offer)))
      (is (= "エールフランス" (:airlines-summary offer)))
      (is (= :skyscanner (:provider offer))))))
