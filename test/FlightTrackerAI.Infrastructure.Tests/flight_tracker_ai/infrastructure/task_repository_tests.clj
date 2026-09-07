(ns flight-tracker-ai.infrastructure.task-repository-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.task-repository :as repo]
            [flight-tracker-ai.core.domain :as domain])
  (:import [System Guid DateTimeOffset DateOnly]))

(defn- create-test-db []
  (let [conn-str (str "Data Source=file:task_db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared")]
    (db/initialize-database conn-str)
    conn-str))

(deftest test-create-task-and-get-task-by-id
  (testing "create-task and get-task-by-id roundtrip successfully"
    (let [conn-str (create-test-db)
          hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          task-item {:id task-id
                     :title "パリ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :round-trip
                                 :outbound (DateOnly. 2026 5 1)
                                 :inbound (DateOnly. 2026 5 8)}
                     :max-stops :direct-only
                     :preferred-airlines ["ANA" "AF"]
                     :target-price-jpy 150000
                     :check-interval-hours 12
                     :notification-webhook-url "https://discord.com/api/webhooks/test"
                     :user-notes "羽田発直行便希望"
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

      (repo/create-task conn-str task-item)
      (let [fetched (repo/get-task-by-id conn-str task-id)]
        (is (some? fetched))
        (is (= task-id (:id fetched)))
        (is (= "パリ旅行" (:title fetched)))
        (is (= "HND" (domain/iata-code-value (:origin fetched))))
        (is (= "CDG" (domain/iata-code-value (:destination fetched))))
        (is (= :direct-only (:max-stops fetched)))
        (is (= 150000 (:target-price-jpy fetched)))
        (is (= "羽田発直行便希望" (:user-notes fetched)))))))

(deftest test-update-check-result
  (testing "update-check-result updates last lowest price, airlines, and provider"
    (let [conn-str (create-test-db)
          hnd (:ok (domain/create-iata-code "HND"))
          sin (:ok (domain/create-iata-code "SIN"))
          task-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          task-item {:id task-id
                     :title "シンガポール出張"
                     :origin hnd
                     :destination sin
                     :trip-type {:kind :one-way
                                 :outbound (DateOnly. 2026 6 1)}
                     :max-stops :any-stops
                     :preferred-airlines []
                     :target-price-jpy nil
                     :check-interval-hours 6
                     :notification-webhook-url nil
                     :user-notes nil
                     :is-headless true
                     :status :active
                     :consecutive-failures 2
                     :created-at now
                     :updated-at now
                     :last-checked-at nil
                     :last-lowest-price-jpy nil
                     :last-lowest-airlines nil
                     :last-lowest-provider nil
                     :ai-analysis-summary nil}]

      (repo/create-task conn-str task-item)
      (let [checked-at (DateTimeOffset/UtcNow)]
        (repo/update-check-result conn-str task-id checked-at 85000 "JAL" :google-flights)
        (let [updated (repo/get-task-by-id conn-str task-id)]
          (is (some? updated))
          (is (= 85000 (:last-lowest-price-jpy updated)))
          (is (= "JAL" (:last-lowest-airlines updated)))
          (is (= :google-flights (:last-lowest-provider updated)))
          (is (= 0 (:consecutive-failures updated))))))))

(deftest test-delete-task
  (testing "delete-task removes task from database"
    (let [conn-str (create-test-db)
          nrt (:ok (domain/create-iata-code "NRT"))
          lax (:ok (domain/create-iata-code "LAX"))
          task-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          task-item {:id task-id
                     :title "ロサンゼルス"
                     :origin nrt
                     :destination lax
                     :trip-type {:kind :one-way
                                 :outbound (DateOnly. 2026 7 1)}
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
                     :ai-analysis-summary nil}]

      (repo/create-task conn-str task-item)
      (repo/delete-task conn-str task-id)
      (let [fetched (repo/get-task-by-id conn-str task-id)]
        (is (nil? fetched))))))
