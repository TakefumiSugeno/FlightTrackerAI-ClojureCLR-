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
      (is (str/includes? html "/api/tasks")))))

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


