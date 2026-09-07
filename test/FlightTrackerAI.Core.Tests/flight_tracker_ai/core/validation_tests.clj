(ns flight-tracker-ai.core.validation-tests
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.validation :as val])
  (:import [System DateOnly]))

(def today (DateOnly. 2026 8 29))

(deftest test-validate-dates
  (testing "validate-dates succeeds for future one-way flight"
    (let [trip {:kind :one-way :outbound (DateOnly. 2026 9 15)}
          res (val/validate-dates today trip)]
      (is (nil? (:error res)))
      (is (some? (:ok res)))))

  (testing "validate-dates fails for past one-way flight"
    (let [trip {:kind :one-way :outbound (DateOnly. 2026 8 1)}
          res (val/validate-dates today trip)]
      (is (some? (:error res)))))

  (testing "validate-dates succeeds for valid roundtrip flight"
    (let [trip {:kind :round-trip :outbound (DateOnly. 2026 10 1) :inbound (DateOnly. 2026 10 10)}
          res (val/validate-dates today trip)]
      (is (nil? (:error res)))
      (is (some? (:ok res)))))

  (testing "validate-dates fails when inbound is before outbound"
    (let [trip {:kind :round-trip :outbound (DateOnly. 2026 10 10) :inbound (DateOnly. 2026 10 1)}
          res (val/validate-dates today trip)]
      (is (some? (:error res))))))

(deftest test-validate-check-interval
  (testing "validate-check-interval checks bounds correctly"
    (doseq [[hours expected-valid] [[1 true]
                                    [12 true]
                                    [168 true]
                                    [0 false]
                                    [-1 false]
                                    [169 false]]]
      (let [res (val/validate-check-interval hours)]
        (if expected-valid
          (is (nil? (:error res)))
          (is (some? (:error res))))))))

(deftest test-validate-target-price
  (testing "validate-target-price handles null and bounds"
    (is (nil? (:error (val/validate-target-price nil))))
    (is (nil? (:error (val/validate-target-price 100000))))
    (is (some? (:error (val/validate-target-price 0))))
    (is (some? (:error (val/validate-target-price -500))))))

(deftest test-validate-route
  (testing "validate-route fails when origin and destination are the same"
    (let [hnd1 (:ok (domain/create-iata-code "HND"))
          hnd2 (:ok (domain/create-iata-code "HND"))
          res (val/validate-route hnd1 hnd2)]
      (is (some? (:error res)))
      (is (.Contains (str (:error res)) "同一の空港"))))

  (testing "validate-route succeeds when origin and destination differ"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          res (val/validate-route hnd cdg)]
      (is (nil? (:error res)))
      (is (= [hnd cdg] (:ok res))))))
