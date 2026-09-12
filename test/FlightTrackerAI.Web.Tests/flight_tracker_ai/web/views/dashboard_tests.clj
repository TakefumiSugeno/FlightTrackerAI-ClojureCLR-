(ns flight-tracker-ai.web.views.dashboard-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly]))

(deftest test-render-empty-state
  (testing "render-empty-state contains guidance when 0 tasks exist"
    (let [html (h/render-html (dash/render-empty-state))]
      (is (str/includes? html "監視中のタスクはありません"))
      (is (str/includes? html "タスクを登録する"))
      (is (str/includes? html "/api/tasks/new-modal")))))

(deftest test-render-task-card
  (testing "render-task-card displays title, IATA codes, price, and 5 action buttons"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:id (Guid/NewGuid)
                     :title "パリ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :round-trip
                                 :outbound-date (DateOnly. 2026 5 1)
                                 :inbound-date (DateOnly. 2026 5 8)}
                     :max-stops :direct-only
                     :preferred-airlines []
                     :target-price-jpy 150000
                     :check-interval-hours 12
                     :notification-webhook-url nil
                     :user-notes "羽田直行希望"
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
          html (h/render-html (dash/render-task-card task-item))]
      (is (str/includes? html "パリ旅行"))
      (is (str/includes? html "HND"))
      (is (str/includes? html "CDG"))
      (is (str/includes? html "¥148,000"))
      (is (str/includes? html "羽田直行希望"))
      ;; 5大ボタン検証
      (is (str/includes? html "/toggle-status"))
      (is (str/includes? html "/run?headless=true"))
      (is (str/includes? html "/run?headless=false"))
      (is (str/includes? html "/modal"))
      (is (str/includes? html "hx-delete"))
      (is (str/includes? html "/detail-modal")))))

(deftest test-render-task-card-paused
  (testing "render-task-card displays pause status and resume button"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          fuk (:ok (domain/create-iata-code "FUK"))
          task-item {:id (Guid/NewGuid)
                     :title "福岡出張"
                     :origin hnd
                     :destination fuk
                     :trip-type {:kind :one-way :outbound-date (DateOnly. 2026 6 1)}
                     :max-stops :direct-only
                     :target-price-jpy 20000
                     :status :paused
                     :last-checked-at nil}
          html (h/render-html (dash/render-task-card task-item))]
      (is (str/includes? html "一時停止"))
      (is (str/includes? html "巡回再開"))
      (is (str/includes? html "fa-play")))))

(deftest test-render-task-table
  (testing "render-task-table renders table headers, data rows and action buttons"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:id (Guid/NewGuid)
                     :title "パリ出張"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :round-trip
                                 :outbound-date (DateOnly. 2026 5 1)
                                 :inbound-date (DateOnly. 2026 5 8)}
                     :max-stops :direct-only
                     :target-price-jpy 150000
                     :check-interval-hours 12
                     :status :active
                     :last-checked-at now
                     :last-lowest-price-jpy 145000
                     :last-lowest-airlines "AF"
                     :last-lowest-provider :google-flights}
          html (h/render-html (dash/render-task-table [task-item]))]
      (is (str/includes? html "タスク名 / 状態"))
      (is (str/includes? html "パリ出張"))
      (is (str/includes? html "HND ➔ CDG"))
      (is (str/includes? html "¥145,000"))
      (is (str/includes? html "/toggle-status"))
      (is (str/includes? html "/run?headless=false"))
      (is (str/includes? html "/detail-modal")))))

(deftest test-render-dashboard
  (testing "render-dashboard contains AI assistant box, control tabs, search and view modes"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-item {:id (Guid/NewGuid)
                     :title "ハワイ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound-date (DateOnly. 2026 7 1)}
                     :status :active}
          dash-cards (h/render-html (dash/render-dashboard [task-item] "card" "all" ""))
          dash-table (h/render-html (dash/render-dashboard [task-item] "table" "all" ""))]
      ;; AI Box
      (is (str/includes? dash-cards "AI 構造化文書・自然言語解析アシスタント"))
      (is (str/includes? dash-cards "aiInput"))
      (is (str/includes? dash-cards "parseWithAI()"))
      (is (str/includes? dash-cards "insertTemplate('markdown')"))
      ;; コントロールバー
      (is (str/includes? dash-cards "すべて (1)"))
      (is (str/includes? dash-cards "監視中 (1)"))
      (is (str/includes? dash-cards "/api/tasks/view?mode="))
      ;; カードビュー
      (is (str/includes? dash-cards "ハワイ旅行"))
      ;; テーブルビュー
      (is (str/includes? dash-table "<table"))
      (is (str/includes? dash-table "ハワイ旅行")))))
