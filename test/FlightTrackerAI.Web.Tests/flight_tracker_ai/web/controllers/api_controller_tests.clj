(ns flight-tracker-ai.web.controllers.api-controller-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.web.controllers.api-controller :as api]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]))

(defn- create-test-db []
  (let [conn-str (str "Data Source=file:api_db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared")]
    (db/initialize-database conn-str)
    conn-str))

(defn- insert-test-task! [conn-str title]
  (let [hnd (:ok (domain/create-iata-code "HND"))
        cdg (:ok (domain/create-iata-code "CDG"))
        now (DateTimeOffset/UtcNow)
        t {:id (Guid/NewGuid)
           :title title
           :origin hnd
           :destination cdg
           :trip-type {:kind :one-way :outbound (DateOnly. 2026 6 1)}
           :max-stops :any-stops
           :preferred-airlines []
           :target-price-jpy 120000
           :check-interval-hours 12
           :notification-webhook-url nil
           :user-notes "初期メモ"
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
    (task-repo/create-task conn-str t)
    t))

(deftest test-get-new-modal
  (testing "GET /api/tasks/new-modal returns 200 with HTML"
    (let [conn-str (create-test-db)
          res (api/handle-api-request conn-str "GET" "/api/tasks/new-modal" nil)]
      (is (= 200 (:status res)))
      (is (str/includes? (:body res) "新規フライト監視タスク登録")))))

(deftest test-post-tasks-and-delete
  (testing "POST /api/tasks creates task and returns dashboard HTML with HX-Trigger, and DELETE removes it with HX-Trigger"
    (let [conn-str (create-test-db)
          form-body "title=API%E3%83%86%E3%82%B9%E3%83%88&origin=HND&destination=CDG&tripType=OneWay&outboundDate=2026-06-01&targetPriceJpy=120000"
          res (api/handle-api-request conn-str "POST" "/api/tasks" form-body)]
      (is (= 200 (:status res)))
      (is (= "closeModal" (get-in res [:headers "HX-Trigger"])))
      (is (str/includes? (:body res) "APIテスト"))
      (is (str/includes? (:body res) "HND"))
      (is (str/includes? (:body res) "CDG"))
      (let [tasks (task-repo/get-all-tasks conn-str)
            task-id (:id (first tasks))
            del-res (api/handle-api-request conn-str "DELETE" (str "/api/tasks/" task-id) nil)]
        (is (= 200 (:status del-res)))
        (is (= "closeModal" (get-in del-res [:headers "HX-Trigger"])))
        (is (= 0 (count (task-repo/get-all-tasks conn-str))))))))

(deftest test-post-tasks-validation-error-keeps-modal
  (testing "POST /api/tasks with invalid IATA returns modal HTML with error message and without closeModal trigger"
    (let [conn-str (create-test-db)
          form-body "title=%E3%82%A8%E3%83%A9%E3%83%BC%E3%83%86%E3%82%B9%E3%83%88&origin=INVALID&destination=CDG&tripType=OneWay&outboundDate=2026-06-01"
          res (api/handle-api-request conn-str "POST" "/api/tasks" form-body)]
      (is (= 200 (:status res)))
      (is (nil? (get-in res [:headers "HX-Trigger"])))
      (is (str/includes? (:body res) "英字3文字である必要があります"))
      (is (str/includes? (:body res) "INVALID"))
      (is (str/includes? (:body res) "CDG"))
      (is (str/includes? (:body res) "エラーテスト")))))

(deftest test-get-settings-modal-and-post
  (testing "GET /api/settings/modal returns settings form, and POST updates it"
    (let [conn-str (create-test-db)
          res-modal (api/handle-api-request conn-str "GET" "/api/settings/modal" nil)]
      (is (= 200 (:status res-modal)))
      (is (str/includes? (:body res-modal) "全体システム設定"))

      (let [post-body "defaultCheckIntervalHours=6&enableGoogleFlights=1&enableSkyscanner=1"
            res-post (api/handle-api-request conn-str "POST" "/api/settings" post-body)]
        (is (= 200 (:status res-post)))
        (is (= "closeModal" (get-in res-post [:headers "HX-Trigger"])))))))

(deftest test-edit-and-quick-note-modal
  (testing "GET /api/tasks/:id/edit-modal and /api/tasks/:id/quick-note-modal return correct modals"
    (let [conn-str (create-test-db)
          task (insert-test-task! conn-str "モーダルテスト")
          task-id (:id task)
          res-edit (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/edit-modal") nil)
          res-note (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/quick-note-modal") nil)]
      (is (= 200 (:status res-edit)))
      (is (str/includes? (:body res-edit) "モーダルテスト"))
      (is (= 200 (:status res-note)))
      (is (str/includes? (:body res-note) "タスクのメモ・要望編集"))
      (is (str/includes? (:body res-note) "初期メモ")))))

(deftest test-update-notes-patch
  (testing "PATCH /api/tasks/:id/notes updates note in DB"
    (let [conn-str (create-test-db)
          task (insert-test-task! conn-str "メモ更新テスト")
          task-id (:id task)
          form-body "notes=%E6%9B%B4%E6%96%B0%E3%81%95%E3%82%8C%E3%81%9F%E3%83%A1%E3%83%A2"
          res (api/handle-api-request conn-str "PATCH" (str "/api/tasks/" task-id "/notes") form-body)
          updated-task (task-repo/get-task-by-id conn-str task-id)]
      (is (= 200 (:status res)))
      (is (= "closeModal" (get-in res [:headers "HX-Trigger"])))
      (is (= "更新されたメモ" (:user-notes updated-task))))))

(deftest test-update-task-post
  (testing "POST /api/tasks/:id updates task with HX-Trigger and OOB swap on success, preserves modal on error"
    (let [conn-str (create-test-db)
          task (insert-test-task! conn-str "更新前タスク")
          task-id (:id task)
          ;; 1. Normal update
          update-body "title=%E6%9B%B4%E6%96%B0%E5%BE%8C%E3%82%BF%E3%82%B9%E3%82%AF&origin=HND&destination=CDG&targetPriceJpy=150000&checkIntervalHours=6"
          res-ok (api/handle-api-request conn-str "POST" (str "/api/tasks/" task-id) update-body)
          updated (task-repo/get-task-by-id conn-str task-id)]
      (is (= 200 (:status res-ok)))
      (is (= "closeModal" (get-in res-ok [:headers "HX-Trigger"])))
      (is (str/includes? (:body res-ok) "hx-swap-oob=\"outerHTML\""))
      (is (= "更新後タスク" (:title updated)))
      (is (= 150000 (:target-price-jpy updated)))
      (is (= 6 (:check-interval-hours updated)))

      ;; 2. Validation error update (invalid IATA)
      (let [invalid-body "title=%E4%B8%8D%E6%AD%A3IATA&origin=BADORIGIN&destination=CDG"
            res-err (api/handle-api-request conn-str "POST" (str "/api/tasks/" task-id) invalid-body)]
        (is (= 200 (:status res-err)))
        (is (nil? (get-in res-err [:headers "HX-Trigger"])))
        (is (str/includes? (:body res-err) "英字3文字である必要があります"))
        (is (str/includes? (:body res-err) "BADORIGIN"))))))

(deftest test-task-history-and-detail
  (testing "GET /api/tasks/:id/history returns json and /detail returns modal html"
    (let [conn-str (create-test-db)
          task (insert-test-task! conn-str "詳細テスト")
          task-id (:id task)
          res-hist (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/history") nil)
          res-detail (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/detail") nil)]
      (is (= 200 (:status res-hist)))
      (is (= "application/json; charset=utf-8" (:content-type res-hist)))
      (is (= 200 (:status res-detail)))
      (is (str/includes? (:body res-detail) "詳細テスト")))))

(deftest test-immediate-run-lock-conflict
  (testing "POST /api/tasks/:id/run returns 409 Conflict when scraper-lock is already held"
    (let [conn-str (create-test-db)
          task (insert-test-task! conn-str "排他テスト")
          task-id (:id task)]
      (.Wait scraper-common/scraper-lock)
      (try
        (let [res (api/handle-api-request conn-str "POST" (str "/api/tasks/" task-id "/run") nil)]
          (is (= 409 (:status res)))
          (is (str/includes? (:body res) "他のタスクが巡回中です")))
        (finally
          (.Release scraper-common/scraper-lock))))))

(deftest test-toggle-task-status
  (testing "POST /api/tasks/:id/toggle-status toggles between active and paused"
    (let [conn-str (create-test-db)
          task (insert-test-task! conn-str "トグルテスト")
          task-id (:id task)
          res1 (api/handle-api-request conn-str "POST" (str "/api/tasks/" task-id "/toggle-status") nil)
          t1 (task-repo/get-task-by-id conn-str task-id)
          res2 (api/handle-api-request conn-str "POST" (str "/api/tasks/" task-id "/toggle-status") nil)
          t2 (task-repo/get-task-by-id conn-str task-id)]
      (is (= 200 (:status res1)))
      (is (= :paused (:status t1)))
      (is (= 200 (:status res2)))
      (is (= :active (:status t2))))))

(deftest test-logs-endpoints
  (testing "GET /api/logs/modal and /api/logs/text return logs"
    (let [conn-str (create-test-db)
          res-modal (api/handle-api-request conn-str "GET" "/api/logs/modal" nil)
          res-text (api/handle-api-request conn-str "GET" "/api/logs/text" nil)]
      (is (= 200 (:status res-modal)))
      (is (str/includes? (:body res-modal) "システム実行ログ"))
      (is (= 200 (:status res-text)))
      (is (= "text/plain; charset=utf-8" (:content-type res-text))))))

(deftest test-ai-parse-endpoint
  (testing "POST /api/ai/parse returns JSON with parsed or fallback fields"
    (let [conn-str (create-test-db)
          res (api/handle-api-request conn-str "POST" "/api/ai/parse" "{\"prompt\":\"東京からパリ往復\"}")]
      (is (= 200 (:status res)))
      (is (= "application/json; charset=utf-8" (:content-type res)))
      (is (str/includes? (:body res) "TripType")))
    (let [conn-str (create-test-db)
          err-res (api/handle-api-request conn-str "POST" "/api/ai/parse" "{\"prompt\":\"\"}")]
      (is (= 400 (:status err-res))))))

(deftest test-create-task-standalone
  (testing "POST /api/tasks/standalone creates task and returns redirect script"
    (let [conn-str (create-test-db)
          form-body "title=スタンドアロン&origin=HND&destination=SIN&tripType=RoundTrip&outboundDate=2026-08-10&inboundDate=2026-08-17&targetPriceJpy=80000&maxStops=DirectOnly"
          res (api/handle-api-request conn-str "POST" "/api/tasks/standalone" form-body)
          tasks (task-repo/get-all-tasks conn-str)]
      (is (= 200 (:status res)))
      (is (str/includes? (:body res) "window.location.href='/'"))
      (is (= 1 (count tasks)))
      (is (= "スタンドアロン" (:title (first tasks))))
      (is (= :direct-only (:max-stops (first tasks)))))))

(deftest test-route-aliases
  (testing "Route aliases /modal, /notes-modal, /detail-modal, and /view work correctly"
    (let [conn-str (create-test-db)
          task (insert-test-task! conn-str "エイリアステスト")
          task-id (:id task)
          res-modal (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/modal") nil)
          res-notes (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/notes-modal") nil)
          res-detail (api/handle-api-request conn-str "GET" (str "/api/tasks/" task-id "/detail-modal") nil)
          res-view (api/handle-api-request conn-str "GET" "/api/tasks/view" nil)]
      (is (= 200 (:status res-modal)))
      (is (str/includes? (:body res-modal) "エイリアステスト"))
      (is (= 200 (:status res-notes)))
      (is (str/includes? (:body res-notes) "タスクのメモ・要望編集"))
      (is (= 200 (:status res-detail)))
      (is (str/includes? (:body res-detail) "エイリアステスト"))
      (is (= 200 (:status res-view)))
      (is (str/includes? (:body res-view) "エイリアステスト")))))

