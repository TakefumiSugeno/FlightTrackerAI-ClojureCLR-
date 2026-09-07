(ns flight-tracker-ai.infrastructure.ai-client-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.ai-client :as ai])
  (:import [System DateOnly]
           [System.Net HttpStatusCode]
           [System.Net.Http HttpClient HttpResponseMessage StringContent HttpMessageHandler]
           [System.Text Encoding]))

(deftest test-parse-flight-query-missing-api-key
  (testing "parse-flight-query returns {:error ...} when API key is missing"
    (let [client (HttpClient.)
          res (ai/parse-flight-query client nil "GWに東京からパリ" (DateOnly. 2026 4 1))]
      (is (some? (:error res)))
      (is (.Contains ^String (:error res) "API キーが設定されていません")))))

(deftest test-parse-flight-query-json-success
  (testing "parse-flight-query-json extracts structured flight parameters"
    (let [json-content "{\"Origin\":\"HND\",\"Destination\":\"CDG\",\"TripType\":\"RoundTrip\",\"OutboundDate\":\"2026-05-01\",\"InboundDate\":\"2026-05-08\",\"MaxPriceJpy\":160000,\"PreferredAirlines\":[\"ANA\"],\"Notes\":\"羽田発希望\"}"
          res (ai/parse-flight-query-json json-content)]
      (is (some? (:ok res)))
      (let [parsed (:ok res)]
        (is (= "HND" (:Origin parsed)))
        (is (= "CDG" (:Destination parsed)))
        (is (= "RoundTrip" (:TripType parsed)))
        (is (= "2026-05-01" (:OutboundDate parsed)))
        (is (= "2026-05-08" (:InboundDate parsed)))
        (is (= 160000 (:MaxPriceJpy parsed)))))))
