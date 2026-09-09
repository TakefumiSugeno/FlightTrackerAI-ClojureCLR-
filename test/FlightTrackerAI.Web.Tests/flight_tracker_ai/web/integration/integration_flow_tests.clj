(ns flight-tracker-ai.web.integration.integration-flow-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.settings-repository :as settings-repo]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.flight-repository :as flight-repo]
            [flight-tracker-ai.infrastructure.notification :as notif]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.web.controllers.api-controller :as api]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]
           [System.Net.Http HttpClient]))

(defn- create-test-db []
  (let [conn-str (str "Data Source=file:integration_db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared")]
    (db/initialize-database conn-str)
    conn-str))

(deftest test-end-to-end-integration-flow
  (testing "Full Flow: Modal request -> Task creation -> Scrape simulation -> Lowest price update -> Target met evaluation -> UI rendering"
    (let [conn-str (create-test-db)]
      ;; 1. GET /api/tasks/new-modal (Check modal rendering)
      (let [res-modal (api/handle-api-request conn-str "GET" "/api/tasks/new-modal" nil)]
        (is (= 200 (:status res-modal)))
        (is (str/includes? (:body res-modal) "新規フライト監視タスク登録")))

      ;; 2. POST /api/tasks (Create a task: HND -> CDG, Target: 150,000 JPY)
      (let [form-body "title=%E3%83%91%E3%83%AA%E6%97%85%E8%A1%8C&origin=HND&destination=CDG&tripType=OneWay&outboundDate=2026-07-01&targetPriceJpy=150000&checkIntervalHours=12"
            res-create (api/handle-api-request conn-str "POST" "/api/tasks" form-body)]
        (is (= 200 (:status res-create)))
        (is (= "closeModal" (get-in res-create [:headers "HX-Trigger"])))
        (is (str/includes? (:body res-create) "パリ旅行"))
        (is (str/includes? (:body res-create) "HND"))
        (is (str/includes? (:body res-create) "CDG")))

      ;; Verify DB contains the task
      (let [tasks (task-repo/get-all-tasks conn-str)]
        (is (= 1 (count tasks)))
        (let [created-task (first tasks)
              task-id (:id created-task)
              now (DateTimeOffset/UtcNow)]
          (is (= "パリ旅行" (:title created-task)))
          (is (= 150000 (:target-price-jpy created-task)))

          ;; 3. Simulate Scraper execution (offer found at 142,000 JPY -> Target met!)
          (let [run-log-id (flight-repo/create-run-log conn-str task-id :google-flights)
                offer {:id (Guid/NewGuid)
                       :task-id task-id
                       :run-log-id run-log-id
                       :provider :google-flights
                       :airlines-summary "ANA"
                       :departure-time now
                       :arrival-time (.AddHours now 14.0)
                       :total-duration-minutes 840
                       :stops-count 0
                       :segments []
                       :price-jpy 142000
                       :booking-url "https://flights.google.com/test"
                       :captured-at now}]
            (flight-repo/save-snapshots conn-str [offer])
            (flight-repo/complete-run-log conn-str run-log-id 1 142000 1200 nil)
            (task-repo/update-check-result conn-str task-id now 142000 "ANA" :google-flights)

            ;; 4. Check UI Detail Modal (Timeline and offers)
            (let [res-detail (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/detail") nil)]
              (is (= 200 (:status res-detail)))
              (is (str/includes? (:body res-detail) "¥142,000"))
              (is (str/includes? (:body res-detail) "ANA"))
              (is (str/includes? (:body res-detail) "GoogleFlights")))

            ;; 5. Check Dashboard UI shows Target Achieved Badge
            (let [reloaded-tasks (task-repo/get-all-tasks conn-str)
                  dashboard-html (h/render-html (dash/render-dashboard-content reloaded-tasks))]
              (is (str/includes? dashboard-html "🎯 目標達成"))
              (is (str/includes? dashboard-html "¥142,000")))))))))
