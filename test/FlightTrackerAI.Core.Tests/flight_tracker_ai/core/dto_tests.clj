(ns flight-tracker-ai.core.dto-tests
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto])
  (:import [System Guid DateTimeOffset DateOnly TimeSpan]))

(deftest test-task-row-roundtrip
  (testing "task->row and row->task roundtrip correctly preserves all fields"
    (let [task {:id (Guid/NewGuid)
                :title "GW パリ旅行"
                :origin "HND"
                :destination "CDG"
                :trip-type {:kind :round-trip
                            :outbound (DateOnly. 2026 5 1)
                            :inbound (DateOnly. 2026 5 8)}
                :max-stops :one-stop
                :preferred-airlines ["ANA" "AF"]
                :target-price-jpy 160000
                :check-interval-hours 12
                :notification-webhook-url "https://discord.com/api/webhooks/xxx"
                :user-notes "羽田発着希望、預け荷物あり"
                :is-headless true
                :status :active
                :consecutive-failures 0
                :created-at (DateTimeOffset. 2026 8 29 10 0 0 (TimeSpan/FromHours 9.0))
                :updated-at (DateTimeOffset. 2026 8 29 14 0 0 (TimeSpan/FromHours 9.0))
                :last-checked-at (DateTimeOffset. 2026 8 29 14 0 0 (TimeSpan/FromHours 9.0))
                :last-lowest-price-jpy 148200
                :last-lowest-airlines "ANA + SQ / 復: AF"
                :last-lowest-provider :google-flights
                :ai-analysis-summary "現在最安値圏内です"}
          row (dto/task->row task)]
      (is (= "HND" (:origin row)))
      (is (= "CDG" (:destination row)))
      (is (= "RoundTrip" (:trip_type row)))
      (is (= "2026-05-01" (:outbound_date row)))
      (is (= "2026-05-08" (:inbound_date row)))
      (is (= 148200 (:last_lowest_price_jpy row)))
      (is (= "GoogleFlights" (:last_lowest_provider row)))
      (is (= "羽田発着希望、預け荷物あり" (:user_notes row)))
      (is (= 1 (:is_headless row)))

      (let [res (dto/row->task row)]
        (is (nil? (:error res)))
        (let [restored (:ok res)]
          (is (= (:id task) (:id restored)))
          (is (= (:title task) (:title restored)))
          (is (= "HND" (:origin restored)))
          (is (= "CDG" (:destination restored)))
          (is (= (:max-stops task) (:max-stops restored)))
          (is (= ["ANA" "AF"] (:preferred-airlines restored)))
          (is (= 160000 (:target-price-jpy restored)))
          (is (= 12 (:check-interval-hours restored)))
          (is (= (:notification-webhook-url task) (:notification-webhook-url restored)))
          (is (= (:user-notes task) (:user-notes restored)))
          (is (= true (:is-headless restored)))
          (is (= :active (:status restored)))
          (is (= 148200 (:last-lowest-price-jpy restored)))
          (is (= "ANA + SQ / 復: AF" (:last-lowest-airlines restored)))
          (is (= :google-flights (:last-lowest-provider restored)))
          (is (= "現在最安値圏内です" (:ai-analysis-summary restored))))))

  (testing "task->row supports both :outbound and :outbound-date keys"
    (let [task-with-date-keys {:id (Guid/NewGuid)
                               :title "キー互換テスト"
                               :origin "HND"
                               :destination "MNL"
                               :trip-type {:kind :round-trip
                                           :outbound-date (DateOnly. 2026 10 1)
                                           :inbound-date (DateOnly. 2026 10 10)}
                               :status :active}
          row (dto/task->row task-with-date-keys)]
      (is (= "2026-10-01" (:outbound_date row)))
      (is (= "2026-10-10" (:inbound_date row)))))))

(deftest test-offer-row-roundtrip
  (testing "offer->row and row->offer roundtrip correctly preserves fields"
    (let [offer {:id (Guid/NewGuid)
                 :task-id (Guid/NewGuid)
                 :run-log-id (Guid/NewGuid)
                 :provider :google-flights
                 :airlines-summary "全日空 + シンガポール航空"
                 :departure-time (DateTimeOffset. 2026 5 1 10 35 0 (TimeSpan/FromHours 9.0))
                 :arrival-time (DateTimeOffset. 2026 5 2 7 15 0 (TimeSpan/FromHours 2.0))
                 :total-duration-minutes 1360
                 :stops-count 1
                 :segments [{:leg-index 0
                             :segment-index 0
                             :departure-airport "HND"
                             :arrival-airport "SIN"
                             :marketing-airline "全日空"
                             :flight-number "NH841"
                             :flight-duration-minutes 420
                             :layover-minutes-next 135}]
                 :price-jpy 148200
                 :booking-url "https://flights.google.com"
                 :captured-at (DateTimeOffset. 2026 8 29 14 0 0 (TimeSpan/FromHours 9.0))}
          row (dto/offer->row offer)]
      (is (.Contains (str (:segments_json row)) "NH841"))
      (let [restored (dto/row->offer row)]
        (is (= (:id offer) (:id restored)))
        (is (= (:price-jpy offer) (:price-jpy restored)))
        (is (= :google-flights (:provider restored)))
        (is (= 1 (count (:segments restored))))
        (is (= "NH841" (:flight-number (first (:segments restored)))))))))

(deftest test-settings-row-roundtrip
  (testing "settings->row and row->settings roundtrip correctly"
    (let [settings {:default-check-interval-hours 6
                    :default-webhook-url "https://discord.com/webhook/test"
                    :openrouter-api-key "sk-or-v1-xxx"
                    :enable-google-flights true
                    :enable-skyscanner false
                    :headless-mode false}
          row (dto/settings->row settings)]
      (is (= 6 (:default_check_interval_hours row)))
      (is (= 1 (:enable_google_flights row)))
      (is (= 0 (:enable_skyscanner row)))
      (is (= 0 (:headless_mode row)))
      (let [restored (dto/row->settings row)]
        (is (= 6 (:default-check-interval-hours restored)))
        (is (= "https://discord.com/webhook/test" (:default-webhook-url restored)))
        (is (= "sk-or-v1-xxx" (:openrouter-api-key restored)))
        (is (true? (:enable-google-flights restored)))
        (is (false? (:enable-skyscanner restored)))
        (is (false? (:headless-mode restored)))))))
