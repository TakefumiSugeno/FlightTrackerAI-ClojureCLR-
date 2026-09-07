(ns flight-tracker-ai.core.domain-tests
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [flight-tracker-ai.core.domain :as domain]))

(deftest test-iata-code-valid
  (testing "IATA code creates successfully for valid 3-letter codes"
    (doseq [[input expected] [["HND" "HND"]
                              ["NRT" "NRT"]
                              ["cdg" "CDG"]
                              [" lax " "LAX"]
                              ["FUK" "FUK"]]]
      (let [res (domain/create-iata-code input)]
        (is (nil? (:error res)))
        (is (= expected (:ok res)))
        (is (= expected (domain/iata-code-value res)))))))

(deftest test-iata-code-invalid
  (testing "IATA code fails for invalid codes"
    (doseq [input ["" "   " "H" "HN" "HNDA" "H12" "H-D"]]
      (let [res (domain/create-iata-code input)]
        (is (some? (:error res)))
        (is (nil? (:ok res)))))))

(deftest test-max-stops-conversions
  (testing "MaxStops from-string and to-string roundtrip correctly"
    (doseq [[input expected-str expected-kw] [["DirectOnly" "DirectOnly" :direct-only]
                                              ["0" "DirectOnly" :direct-only]
                                              ["OneStop" "OneStop" :one-stop]
                                              ["1" "OneStop" :one-stop]
                                              ["Any" "Any" :any-stops]]]
      (let [parsed (domain/max-stops-from-string input)]
        (is (= expected-kw parsed))
        (is (= expected-str (domain/max-stops-to-string parsed)))))))

(deftest test-task-status-conversions
  (testing "TaskStatus transitions and strings"
    (doseq [[input expected-str expected-kw] [["Active" "Active" :active]
                                              ["Paused" "Paused" :paused]
                                              ["Completed" "Completed" :completed]]]
      (let [parsed (domain/task-status-from-string input nil)]
        (is (= expected-kw parsed))
        (is (= expected-str (domain/task-status-to-string parsed)))))

    (testing "TaskStatus Failed holds error message"
      (let [parsed (domain/task-status-from-string "Failed" "Network timeout")]
        (is (= {:failed "Network timeout"} parsed))
        (is (= "Error: Network timeout" (domain/task-status-to-string parsed)))))))

(deftest test-scraping-provider-conversions
  (testing "ScrapingProvider parses GoogleFlights correctly"
    (let [parsed (domain/scraping-provider-from-string "GoogleFlights")]
      (is (= :google-flights parsed))
      (is (= "GoogleFlights" (domain/scraping-provider-to-string parsed)))))

  (testing "ScrapingProvider parses Skyscanner correctly"
    (let [parsed (domain/scraping-provider-from-string "Skyscanner")]
      (is (= :skyscanner parsed))
      (is (= "Skyscanner" (domain/scraping-provider-to-string parsed))))))

(deftest test-target-achieved
  (testing "target-achieved? returns true when active and price <= target"
    (is (true? (domain/target-achieved? {:status :active :target-price-jpy 150000 :last-lowest-price-jpy 148200})))
    (is (true? (domain/target-achieved? {:status :active :target-price-jpy 150000 :last-lowest-price-jpy 150000})))
    (is (false? (domain/target-achieved? {:status :active :target-price-jpy 150000 :last-lowest-price-jpy 152000})))
    (is (false? (domain/target-achieved? {:status :paused :target-price-jpy 150000 :last-lowest-price-jpy 140000})))
    (is (false? (domain/target-achieved? {:status :active :target-price-jpy nil :last-lowest-price-jpy 140000})))))
