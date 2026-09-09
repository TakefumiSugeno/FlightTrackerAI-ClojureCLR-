(ns flight-tracker-ai.web.views.modals-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.web.views.modals :as modals]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]))

(deftest test-render-task-modal-new
  (testing "render-task-modal renders new registration modal"
    (let [html (modals/render-task-modal nil {:origin "HND" :destination "CDG"})]
      (is (str/includes? html "新規フライト監視タスク登録"))
      (is (str/includes? html "HND"))
      (is (str/includes? html "CDG"))
      (is (str/includes? html "/api/tasks"))
      (is (str/includes? html "closeCurrentModal()"))
      (is (str/includes? html "modal-backdrop-clickable")))))

(deftest test-render-settings-modal
  (testing "render-settings-modal displays form inputs for global settings"
    (let [settings {:default-check-interval-hours 12
                    :default-webhook-url "https://discord.com/webhook"
                    :openrouter-api-key "sk-or-v1"
                    :enable-google-flights true
                    :enable-skyscanner true
                    :headless-mode true}
          html (modals/render-settings-modal settings)]
      (is (str/includes? html "全体システム設定"))
      (is (str/includes? html "https://discord.com/webhook"))
      (is (str/includes? html "sk-or-v1")))))

(deftest test-render-timeline-modal
  (testing "render-timeline-modal displays flight offers"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:id (Guid/NewGuid)
                     :title "パリ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound (DateOnly. 2026 5 1)}
                     :max-stops :direct-only
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
                     :last-checked-at now
                     :last-lowest-price-jpy 148000
                     :last-lowest-airlines "ANA"
                     :last-lowest-provider :google-flights
                     :ai-analysis-summary nil}
          offer {:id (Guid/NewGuid)
                 :task-id (:id task-item)
                 :run-log-id (Guid/NewGuid)
                 :provider :google-flights
                 :airlines-summary "ANA"
                 :departure-time now
                 :arrival-time (.AddHours now 14.0)
                 :total-duration-minutes 840
                 :stops-count 0
                 :segments []
                 :price-jpy 148000
                 :booking-url "https://flights.google.com"
                 :captured-at now}
          html (modals/render-timeline-modal task-item [offer] [])]
      (is (str/includes? html "パリ旅行"))
      (is (str/includes? html "¥148,000"))
      (is (str/includes? html "ANA"))
      (is (str/includes? html "priceChart"))
      (is (str/includes? html "timelineModal")))))

(deftest test-render-quick-note-modal
  (testing "render-quick-note-modal renders form with existing note and action url"
    (let [task-id (Guid/NewGuid)
          html (modals/render-quick-note-modal task-id "パリ旅行" "直行便のみ監視")]
      (is (str/includes? html "quickNoteModal"))
      (is (str/includes? html "タスクのメモ・要望編集"))
      (is (str/includes? html "直行便のみ監視"))
      (is (str/includes? html "パリ旅行"))
      (is (str/includes? html (str "/api/tasks/" task-id "/notes"))))))

(deftest test-render-logs-modal
  (testing "render-logs-modal renders log lines and controls"
    (let [logs ["[2026-09-08 JST] [INFO] [Worker] 巡回開始"
                "[2026-09-08 JST] [SUCCESS] [Worker] 巡回成功"]
          html (modals/render-logs-modal logs "logs/app.log")]
      (is (str/includes? html "システム実行ログ"))
      (is (str/includes? html "最新 2 行を表示中"))
      (is (str/includes? html "logContentPre"))
      (is (str/includes? html "logs/app.log"))
      (is (str/includes? html "巡回開始"))
      (is (str/includes? html "/api/logs/modal")))))

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
          html (modals/render-standalone-new-task-page params)]
      (is (str/includes? (str html) "新規フライト監視タスク登録"))
      (is (str/includes? (str html) "HND"))
      (is (str/includes? (str html) "SIN"))
      (is (str/includes? (str html) "2026-08-10"))
      (is (str/includes? (str html) "2026-08-17"))
      (is (str/includes? (str html) "90000"))
      (is (str/includes? (str html) "お盆シンガポール"))
      (is (str/includes? (str html) "/api/tasks/standalone")))))

(deftest test-render-task-modal-with-validation-error
  (testing "render-task-modal displays inline error message without losing form inputs"
    (let [params {:origin "INVALID" :destination "CDG" :title "夏休み旅行"}
          html (modals/render-task-modal nil params "IATAコードは3文字の英字である必要があります")]
      (is (str/includes? html "IATAコードは3文字の英字である必要があります"))
      (is (str/includes? html "INVALID"))
      (is (str/includes? html "CDG"))
      (is (str/includes? html "夏休み旅行"))
      (is (str/includes? html "text-rose-300"))
      (is (str/includes? html "isFormDirty")))))

(deftest test-render-modals-dirty-guard
  (testing "input modals have dirty guard logic on backdrop click"
    (let [task-html (modals/render-task-modal nil {})
          settings-html (modals/render-settings-modal {})
          quick-note-html (modals/render-quick-note-modal (Guid/NewGuid) "メモ" "HND ➔ CDG")]
      (is (str/includes? task-html "isFormDirty"))
      (is (str/includes? settings-html "isFormDirty"))
      (is (str/includes? quick-note-html "isFormDirty")))))

(deftest test-render-timeline-modal-has-close-button
  (testing "render-timeline-modal includes close button in footer"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-item {:id (Guid/NewGuid)
                     :title "テスト旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound (DateOnly. 2026 5 1)}
                     :max-stops :direct-only
                     :preferred-airlines []
                     :target-price-jpy 100000
                     :check-interval-hours 12
                     :notification-webhook-url nil
                     :user-notes nil
                     :is-headless true
                     :status :active
                     :consecutive-failures 0
                     :created-at (DateTimeOffset/UtcNow)
                     :updated-at (DateTimeOffset/UtcNow)
                     :last-checked-at nil
                     :last-lowest-price-jpy nil
                     :last-lowest-airlines nil
                     :last-lowest-provider nil
                     :ai-analysis-summary nil}
          html (modals/render-timeline-modal task-item [] [])]
      (is (str/includes? html "閉じる"))
      (is (str/includes? html "closeCurrentModal()")))))



