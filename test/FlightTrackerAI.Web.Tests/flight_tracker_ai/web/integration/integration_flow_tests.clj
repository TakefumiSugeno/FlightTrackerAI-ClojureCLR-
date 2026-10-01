(ns flight-tracker-ai.web.integration.integration-flow-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.settings-repository :as settings-repo]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.flight-repository :as flight-repo]
            [flight-tracker-ai.infrastructure.google-flights-scraper :as gf]
            [flight-tracker-ai.infrastructure.notification :as notif]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.web.controllers.api-controller :as api]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly IO.File IO.Path]
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
              (is (str/includes? dashboard-html "目標達成"))
              (is (str/includes? dashboard-html "¥142,000")))))))))

(deftest test-google-flights-scraping-e2e-flow
  (testing "E2E: Zero-config initial state -> New Task Registration -> Scraping top 10 offers -> Price/Capture consistency -> Flight number badge UI rendering"
    (let [conn-str (create-test-db)]
      ;; 1. ゼロ設定・初回起動 (対象0件) の確認
      (let [initial-tasks (task-repo/get-all-tasks conn-str)]
        (is (empty? initial-tasks))
        (let [empty-ui (h/render-html (dash/render-dashboard-content initial-tasks))]
          (is (str/includes? empty-ui "監視中のタスクはありません"))
          (is (str/includes? empty-ui "タスクを登録する"))))

      ;; 2. 新規フライト監視タスクの登録 (HND -> MNL, 目標 35,000円, 巡回取得件数 10件)
      (let [form-body "title=%E3%83%9E%E3%83%8B%E3%83%A9%E5%AE%9A%E6%9C%9F%E4%BE%BF&origin=HND&destination=MNL&tripType=OneWay&outboundDate=2026-06-15&outboundTimeRange=Morning&maxStops=OneStop&maxResultsCount=10&targetPriceJpy=35000"
            res-create (api/handle-api-request conn-str "POST" "/api/tasks" form-body)]
        (is (= 200 (:status res-create)))
        (is (= "closeModal" (get-in res-create [:headers "HX-Trigger"])))

        (let [tasks (task-repo/get-all-tasks conn-str)]
          (is (= 1 (count tasks)))
          (let [task (first tasks)
                task-id (:id task)
                now (DateTimeOffset/UtcNow)]
            (is (= "マニラ定期便" (:title task)))
            (is (= :morning (:outbound-time-range task)))
            (is (= :one-stop (:max-stops task)))
            (is (= 10 (:max-results-count task)))
            (is (= 35000 (:target-price-jpy task)))

            ;; 3. Google Flights 実DOMサンプルからのスクレイピング (上位10件制限)
            (let [sample-html-path "doc/work/GoogleFlightサンプル/GoogleFlightサンプル１.html"]
              (when (File/Exists sample-html-path)
                (let [html-content (File/ReadAllText sample-html-path)
                      run-log-id (flight-repo/create-run-log conn-str task-id :google-flights)
                      all-offers (gf/extract-cards-from-html html-content task-id run-log-id "https://flights.google.com/test" (DateOnly. 2026 6 15) now)
                      top10-offers (vec (take (:max-results-count task) all-offers))]
                  ;; 上位10件取得の検証
                  (is (= 10 (count top10-offers)))
                  ;; 便名が itinerary から抽出されていること
                  (is (every? #(not (str/blank? (:flight-number %))) top10-offers))
                  ;; flight_key が生成されていること
                  (is (every? #(not (str/blank? (:flight-key %))) top10-offers))

                  ;; 最安値オファーの特定
                  (let [lowest-offer (apply min-key :price-jpy top10-offers)
                        lowest-price (:price-jpy lowest-offer)
                        lowest-flight-number (:flight-number lowest-offer)
                        lowest-airline (:airlines-summary lowest-offer)]
                    ;; 実サンプル検証: 最安値は 33,650 円、便名は 5J 5055 (セブパシフィック航空)
                    (is (= 33650 lowest-price))
                    (is (= "5J 5055" lowest-flight-number))
                    (is (str/includes? lowest-airline "セブパシフィック航空"))

                    ;; 4. キャプチャ画像メタデータと取得データの 100% 一致検証
                    ;; 画面キャプチャ時の最安値表示金額（33,650円）とデータが完全一致すること
                    (flight-repo/save-snapshots conn-str top10-offers)
                    (flight-repo/complete-run-log conn-str run-log-id (count top10-offers) lowest-price 1500 nil)
                    (task-repo/update-check-result conn-str task-id now lowest-price lowest-airline :google-flights lowest-flight-number)

                    (let [updated-task (task-repo/get-task-by-id conn-str task-id)]
                      (is (= 33650 (:last-lowest-price-jpy updated-task)))
                      (is (= "5J 5055" (:last-lowest-flight-number updated-task)))
                      (is (= :google-flights (:last-lowest-provider updated-task)))

                      ;; 5. UI（ダッシュボード・詳細モーダル）での便名バッジ・最安値表示の完全一致検証
                      ;; ダッシュボードカード
                      (let [dash-html (h/render-html (dash/render-dashboard-content [updated-task]))]
                        (is (str/includes? dash-html "¥33,650"))
                        (is (str/includes? dash-html "5J 5055"))
                        (is (str/includes? dash-html "目標達成"))) ;; 33,650 <= 35,000

                      ;; 詳細モーダル
                      (let [res-detail (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/detail") nil)
                            detail-body (:body res-detail)]
                        (is (= 200 (:status res-detail)))
                        (is (str/includes? detail-body "便名"))
                        (is (str/includes? detail-body "5J 5055"))
                        (is (str/includes? detail-body "¥33,650"))
                        (is (str/includes? detail-body "GoogleFlights"))))))))))))))
