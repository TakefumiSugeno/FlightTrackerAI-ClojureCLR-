(ns flight-tracker-ai.infrastructure.scraping-worker-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.core.domain :as domain])
  (:import [System Guid DateTimeOffset DateOnly]
           [System.Net.Http HttpClient]))

(defn- create-test-db []
  (let [conn-str (str "Data Source=file:worker_db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared")]
    (db/initialize-database conn-str)
    conn-str))

(deftest test-is-task-due
  (testing "is-task-due returns true for never-checked active task"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:status :active
                     :last-checked-at nil
                     :check-interval-hours 12}]
      (is (true? (worker/is-task-due now task-item)))))

  (testing "is-task-due respects interval"
    (let [now (DateTimeOffset/UtcNow)
          checked-6h-ago (.AddHours now -6.0)
          checked-13h-ago (.AddHours now -13.0)
          t-recent {:status :active :last-checked-at checked-6h-ago :check-interval-hours 12}
          t-due {:status :active :last-checked-at checked-13h-ago :check-interval-hours 12}]
      (is (false? (worker/is-task-due now t-recent)))
      (is (true? (worker/is-task-due now t-due))))))

(deftest test-is-task-expired
  (testing "is-task-expired identifies past outbound flights"
    (let [today (DateOnly. 2026 8 30)
          past-task {:trip-type {:kind :one-way :outbound (DateOnly. 2026 8 1)}}
          future-task {:trip-type {:kind :one-way :outbound (DateOnly. 2026 9 15)}}]
      (is (true? (worker/is-task-expired today past-task)))
      (is (false? (worker/is-task-expired today future-task))))))

(deftest test-execute-task-scraping-auto-complete
  (testing "execute-task-scraping auto-completes expired tasks in DB"
    (let [conn-str (create-test-db)
          hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-id (Guid/NewGuid)
          now (DateTimeOffset/UtcNow)
          task-item {:id task-id
                     :title "過去タスク"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound (DateOnly. 2020 1 1)}
                     :max-stops :any-stops
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

      (task-repo/create-task conn-str task-item)

      (let [settings {:default-check-interval-hours 12
                      :default-webhook-url nil
                      :openrouter-api-key nil
                      :enable-google-flights false
                      :enable-skyscanner false
                      :headless-mode true}
            http-client (HttpClient.)
            res (worker/execute-task-scraping http-client conn-str task-item settings)]
        (is (= {:ok nil} res))
        (let [updated (task-repo/get-task-by-id conn-str task-id)]
          (is (some? updated))
          (is (= :completed (:status updated))))))))
