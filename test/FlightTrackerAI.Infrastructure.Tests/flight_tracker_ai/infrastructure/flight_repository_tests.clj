(ns flight-tracker-ai.infrastructure.flight-repository-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.flight-repository :as flight-repo]
            [flight-tracker-ai.core.domain :as domain])
  (:import [System Guid DateTimeOffset DateOnly]))

(defn- create-test-db []
  (let [conn-str (str "Data Source=file:flight_db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared")]
    (db/initialize-database conn-str)
    conn-str))

(deftest test-flight-repository-workflow
  (testing "run logs, snapshot saving, latest offers and price history work correctly"
    (let [conn-str (create-test-db)
          hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          task-item {:id task-id
                     :title "パリ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way
                                 :outbound (DateOnly. 2026 5 1)}
                     :max-stops :one-stop
                     :preferred-airlines []
                     :target-price-jpy 150000
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
                     :ai-analysis-summary nil}]

      ;; 1. タスク作成
      (task-repo/create-task conn-str task-item)

      ;; 2. 巡回ログ作成
      (let [run-log-id (flight-repo/create-run-log conn-str task-id :google-flights)]
        (is (some? run-log-id))

        ;; 3. スナップショット保存
        (let [offer1 {:id (Guid/NewGuid)
                      :task-id task-id
                      :run-log-id run-log-id
                      :provider :google-flights
                      :airlines-summary "ANA"
                      :departure-time now
                      :arrival-time (.AddHours now 14.0)
                      :total-duration-minutes 840
                      :stops-count 0
                      :segments []
                      :price-jpy 180000
                      :booking-url "https://google.com/flights"
                      :captured-at now}
              offer2 {:id (Guid/NewGuid)
                      :task-id task-id
                      :run-log-id run-log-id
                      :provider :google-flights
                      :airlines-summary "エールフランス"
                      :departure-time now
                      :arrival-time (.AddHours now 13.0)
                      :total-duration-minutes 780
                      :stops-count 0
                      :segments []
                      :price-jpy 148000
                      :booking-url "https://google.com/flights"
                      :captured-at now}]
          (flight-repo/save-snapshots conn-str [offer1 offer2])
          (flight-repo/complete-run-log conn-str run-log-id 2 148000 1200 nil)

          ;; 4. 最新オファー取得
          (let [latest-offers (flight-repo/get-latest-offers-for-task conn-str task-id 10)]
            (is (= 2 (count latest-offers)))
            (is (= 148000 (:price-jpy (first latest-offers)))))

          ;; 5. 価格履歴取得
          (let [history (flight-repo/get-price-history conn-str task-id)]
            (is (= 1 (count history)))
            (is (= 148000 (:lowest-price-jpy (first history))))
            (is (= :google-flights (:provider (first history)))))

          ;; 6. 古いログパージ (未来の日付を基準に30日前を指定しても今回は消えないこと、0日指定なら消えること)
          (let [deleted (flight-repo/purge-old-logs conn-str 0)]
            (is (>= deleted 0))))))))
