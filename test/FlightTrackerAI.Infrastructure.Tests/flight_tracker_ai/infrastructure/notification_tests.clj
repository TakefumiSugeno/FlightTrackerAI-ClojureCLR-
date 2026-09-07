(ns flight-tracker-ai.infrastructure.notification-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.notification :as notif]
            [flight-tracker-ai.core.domain :as domain])
  (:import [System Guid DateTimeOffset DateOnly]
           [System.Net.Http HttpClient]))

(deftest test-dispatch-price-notification-skips-when-no-webhook
  (testing "dispatch-price-notification returns {:ok nil} when no webhook is configured"
    (let [http-client (HttpClient.)
          hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:id (Guid/NewGuid)
                     :title "テスト"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound (DateOnly. 2026 5 1)}
                     :max-stops :one-stop
                     :preferred-airlines []
                     :target-price-jpy nil
                     :check-interval-hours 12
                     :notification-webhook-url nil
                     :user-notes nil
                     :is-headless true
                     :status :active
                     :consecutive-failures 0
                     :created-at now
                     :updated-at now
                     :last-checked-at nil
                     :last-lowest-price-jpy nil
                     :last-lowest-airlines nil
                     :last-lowest-provider nil
                     :ai-analysis-summary nil}
          offer {:id (Guid/NewGuid)
                 :task-id (:id task-item)
                 :run-log-id (Guid/NewGuid)
                 :provider :google-flights
                 :airlines-summary "ANA"
                 :departure-time now
                 :arrival-time (.AddHours now 12.0)
                 :total-duration-minutes 720
                 :stops-count 0
                 :segments []
                 :price-jpy 140000
                 :booking-url "https://example.com"
                 :captured-at now}
          res (notif/dispatch-price-notification http-client task-item offer -10.0 false nil)]
      (is (= {:ok nil} res)))))
