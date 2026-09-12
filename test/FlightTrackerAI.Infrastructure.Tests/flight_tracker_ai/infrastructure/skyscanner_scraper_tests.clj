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

(deftest test-detect-bot-challenge
  (testing "detect-bot-challenge identifies PerimeterX and robot challenges correctly"
    (is (true? (ss/detect-bot-challenge "Are you a robot?" "Some content" false)))
    (is (true? (ss/detect-bot-challenge "Verification" "Please PRESS & HOLD to continue" false)))
    (is (true? (ss/detect-bot-challenge "Verification" "Please press & hold to continue" false)))
    (is (true? (ss/detect-bot-challenge "Normal title" "Normal content" true)))
    (is (true? (ss/detect-bot-challenge "Are you a person or a robot?" "Normal content" false)))
    (is (false? (ss/detect-bot-challenge "Flights to Paris" "Cheap tickets available" false)))
    (is (false? (ss/detect-bot-challenge nil nil false)))))

(deftest test-parse-offer-element-edge-cases
  (testing "parse-offer-element handles direct flights and multiple stops"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          direct-offer (ss/parse-offer-element task-id run-log-id "url" "80000円" "AF" "" "14h" "直行便" now)
          one-stop-offer (ss/parse-offer-element task-id run-log-id "url" "80000円" "AF" "" "14h" "経由 1回" now)
          two-stop-offer (ss/parse-offer-element task-id run-log-id "url" "80000円" "AF" "" "14h" "2回経由" now)]
      (is (= 0 (:stops-count direct-offer)))
      (is (= 1 (:stops-count one-stop-offer)))
      (is (= 2 (:stops-count two-stop-offer)))))

  (testing "parse-offer-element returns nil when price text is invalid"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          offer (ss/parse-offer-element task-id run-log-id "url" "完売" "AF" "" "14h" "直行便" now)]
      (is (nil? offer)))))

(deftest test-scrape-async-nil-page
  (testing "scrape-async handles nil page safely without throwing"
    (let [task-id (Guid/NewGuid)
          run-log-id (Guid/NewGuid)
          hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-item {:id task-id
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound (DateOnly. 2026 6 1)}}
          res (ss/scrape-async nil task-item run-log-id)]
      (is (= [] res)))))
