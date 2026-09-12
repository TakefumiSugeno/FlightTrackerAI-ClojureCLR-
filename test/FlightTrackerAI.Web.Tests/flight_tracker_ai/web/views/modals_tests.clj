(ns flight-tracker-ai.web.views.modals-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.web.views.modals :as modals]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]))

(deftest test-render-task-modal-new
  (testing "render-task-modal renders new registration modal with datalist and input fields"
    (let [html (h/render-html (modals/render-task-modal nil {:origin "HND" :destination "CDG"}))]
      (is (str/includes? html "新規フライト監視タスク登録"))
      (is (str/includes? html "HND"))
      (is (str/includes? html "CDG"))
      (is (str/includes? html "/api/tasks"))
      (is (str/includes? html "closeCurrentModal()"))
      (is (str/includes? html "airportsList"))
      (is (str/includes? html "btnRoundTrip"))
      (is (str/includes? html "btnOneWay")))))

(deftest test-render-task-modal-edit
  (testing "render-task-modal renders edit modal for existing task"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-id (Guid/NewGuid)
          task-item {:id task-id
                     :title "パリ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :round-trip
                                 :outbound-date (DateOnly. 2026 5 1)
                                 :inbound-date (DateOnly. 2026 5 8)}
                     :max-stops :direct-only
                     :target-price-jpy 150000
                     :check-interval-hours 12
                     :is-headless false
                     :user-notes "メモテスト"}
          html (h/render-html (modals/render-task-modal task-item))]
      (is (str/includes? html "タスク設定の変更"))
      (is (str/includes? html "パリ旅行"))
      (is (str/includes? html "HND"))
      (is (str/includes? html "CDG"))
      (is (str/includes? html (str "/api/tasks/" task-id)))
      (is (str/includes? html "メモテスト"))
      (is (str/includes? html "変更を保存")))))

(deftest test-render-notes-modal
  (testing "render-notes-modal renders quick note editing form"
    (let [task-id (Guid/NewGuid)
          task-item {:id task-id :title "出張タスク" :user-notes "早朝便希望"}
          html (h/render-html (modals/render-notes-modal task-item))]
      (is (str/includes? html "タスクのメモ・要望編集"))
      (is (str/includes? html "出張タスク"))
      (is (str/includes? html "早朝便希望"))
      (is (str/includes? html (str "/api/tasks/" task-id "/notes")))
      (is (str/includes? html "メモを保存")))))

(deftest test-render-settings-modal
  (testing "render-settings-modal displays form inputs for global settings"
    (let [settings {:default-check-interval-hours 12
                    :default-webhook-url "https://discord.com/webhook"
                    :openrouter-api-key "sk-or-v1"
                    :enable-google-flights true
                    :enable-skyscanner true
                    :headless-mode false}
          html (h/render-html (modals/render-settings-modal settings))]
      (is (str/includes? html "システム全体設定"))
      (is (str/includes? html "https://discord.com/webhook"))
      (is (str/includes? html "sk-or-v1"))
      (is (str/includes? html "/api/settings"))
      (is (str/includes? html "Google Flights 巡回を有効化"))
      (is (str/includes? html "Skyscanner 巡回を有効化"))
      (is (str/includes? html "ブラウザ画面を表示して巡回する")))))

(deftest test-render-detail-modal
  (testing "render-detail-modal displays flight offers table and chart integration"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:id (Guid/NewGuid)
                     :title "パリ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound-date (DateOnly. 2026 5 1)}
                     :max-stops :direct-only
                     :target-price-jpy 150000
                     :last-checked-at now}
          offer {:id (Guid/NewGuid)
                 :task-id (:id task-item)
                 :provider :google-flights
                 :airlines-summary "ANA"
                 :departure-time now
                 :arrival-time (.AddHours now 14.0)
                 :total-duration-minutes 840
                 :stops-count 0
                 :price-jpy 148000
                 :booking-url "https://flights.google.com"}
          html (h/render-html (modals/render-detail-modal task-item [offer]))]
      (is (str/includes? html "価格推移 &amp; 旅程詳細"))
      (is (str/includes? html "パリ旅行"))
      (is (str/includes? html "同日・同区間の候補便一覧"))
      (is (str/includes? html "ANA"))
      (is (str/includes? html "¥148,000"))
      (is (str/includes? html "直行便"))
      (is (str/includes? html "14h 00m"))
      (is (str/includes? html "https://flights.google.com"))
      (is (str/includes? html "priceHistoryChart"))
      (is (str/includes? html "閉じる")))))

(deftest test-render-logs-modal
  (testing "render-logs-modal renders log lines and controls"
    (let [logs ["[2026-09-12 JST] [INFO] [Worker] 巡回開始"
                "[2026-09-12 JST] [SUCCESS] [Worker] 巡回成功"]
          html (h/render-html (modals/render-logs-modal logs "logs/app.log"))]
      (is (str/includes? html "システム実行ログ"))
      (is (str/includes? html "最新 2 行を表示中"))
      (is (str/includes? html "logContentPre"))
      (is (str/includes? html "logs/app.log"))
      (is (str/includes? html "巡回開始"))
      (is (str/includes? html "/api/logs/modal"))
      (is (str/includes? html "ログをコピー (AI共有用)")))))

(deftest test-render-standalone-new-task-page
  (testing "render-standalone-new-task-page renders complete registration page"
    (let [params {:origin "HND"
                  :destination "SIN"
                  :outboundDate "2026-08-10"
                  :inboundDate "2026-08-17"
                  :tripType "RoundTrip"
                  :maxStops "DirectOnly"
                  :maxPriceJpy "90000"
                  :notes "お盆シンガポール"}
          html (h/render-html (modals/render-standalone-new-task-page params))]
      (is (str/includes? html "新規フライト監視タスク登録"))
      (is (str/includes? html "HND"))
      (is (str/includes? html "SIN"))
      (is (str/includes? html "2026-08-10"))
      (is (str/includes? html "2026-08-17"))
      (is (str/includes? html "90000"))
      (is (str/includes? html "お盆シンガポール"))
      (is (str/includes? html "/api/tasks/standalone")))))
