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
      (is (str/includes? html "タスクを登録する")))))

(deftest test-render-task-card
  (testing "render-task-card displays title, IATA codes, and price"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:id (Guid/NewGuid)
                     :title "パリ旅行"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :round-trip
                                 :outbound (DateOnly. 2026 5 1)
                                 :inbound (DateOnly. 2026 5 8)}
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
      (is (str/includes? html "🎯 目標達成"))
      (is (str/includes? html "toggleTaskStatus"))
      (is (str/includes? html "triggerImmediateRunWithBrowser"))
      (is (str/includes? html "fa-window-restore"))
      (is (str/includes? html "羽田直行希望")))))

(deftest test-render-manual-challenge-banner
  (testing "render-manual-challenge-banner contains manual assistance guidance and solve button"
    (let [html (h/render-html (dash/render-manual-challenge-banner))]
      (is (str/includes? html "manualChallengeBanner"))
      (is (str/includes? html "PRESS &amp; HOLD"))
      (is (str/includes? html "simulateResolveChallenge()"))
      (is (str/includes? html "手動解除シミュレート"))
      (is (str/includes? html "challengeSeconds")))))


(deftest test-render-ai-assistant-box
  (testing "render-ai-assistant-box contains textarea and template buttons"
    (let [html (h/render-html (dash/render-ai-assistant-box))]
      (is (str/includes? html "AI 構造化文書・自然言語解析アシスタント"))
      (is (str/includes? html "aiInput"))
      (is (str/includes? html "箇条書き"))
      (is (str/includes? html "YAML形式"))
      (is (str/includes? html "自然文"))
      (is (str/includes? html "insertTemplate('yaml')")))))


(deftest test-render-filter-bar
  (testing "render-filter-bar contains status filter tabs and view toggle buttons"
    (let [html (h/render-html (dash/render-filter-bar []))]
      (is (str/includes? html "setStatusFilter('all')"))
      (is (str/includes? html "setStatusFilter('active')"))
      (is (str/includes? html "switchView('cards')"))
      (is (str/includes? html "switchView('list')"))
      (is (str/includes? html "quickSearchInput")))))


(deftest test-render-list-view
  (testing "render-list-view renders table headers and task rows"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          task-item {:id (Guid/NewGuid)
                     :title "パリ出張"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :round-trip
                                 :outbound (DateOnly. 2026 5 1)
                                 :inbound (DateOnly. 2026 5 8)}
                     :max-stops :direct-only
                     :preferred-airlines []
                     :target-price-jpy 150000
                     :check-interval-hours 12
                     :notification-webhook-url nil
                     :user-notes "リスト確認"
                     :is-headless true
                     :status :active
                     :consecutive-failures 0
                     :created-at now
                     :updated-at now
                     :last-checked-at now
                     :last-lowest-price-jpy 145000
                     :last-lowest-airlines "AF"
                     :last-lowest-provider :google-flights
                     :ai-analysis-summary nil}
          html (h/render-html (dash/render-list-view [task-item]))]
      (is (str/includes? html "listView"))
      (is (str/includes? html "パリ出張"))
      (is (str/includes? html "HND"))
      (is (str/includes? html "CDG"))
      (is (str/includes? html "fa-arrow-right"))
      (is (str/includes? html "¥145,000"))
      (is (str/includes? html "🎯 目標達成")))))



(deftest test-render-task-card-error-state
  (testing "render-task-card shows humanized error and retry button when task has failed"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          now (DateTimeOffset/UtcNow)
          failed-task {:id (Guid/NewGuid)
                       :title "失敗タスク"
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
                       :status :error
                       :consecutive-failures 3
                       :created-at now
                       :updated-at now
                       :last-checked-at now
                       :last-lowest-price-jpy nil
                       :last-lowest-airlines nil
                       :last-lowest-provider nil
                       :ai-analysis-summary "一時的なアクセス過密またはBotブロックが検知されました。"}
          html (h/render-html (dash/render-task-card failed-task))]
      (is (str/includes? html "失敗タスク"))
      (is (str/includes? html "今すぐ再試行"))
      (is (str/includes? html "アクセス過密またはBotブロック")))))

(deftest test-dashboard-no-duplicate-hx-and-onclick-modal-calls
  (testing "dashboard buttons must not have both hx-get and modal-opening onclick attributes"
    (let [hnd (:ok (domain/create-iata-code "HND"))
          cdg (:ok (domain/create-iata-code "CDG"))
          task-item {:id (Guid/NewGuid)
                     :title "パリ出張"
                     :origin hnd
                     :destination cdg
                     :trip-type {:kind :one-way :outbound (DateOnly. 2026 5 1)}
                     :max-stops :direct-only
                     :preferred-airlines []
                     :target-price-jpy 150000
                     :check-interval-hours 12
                     :notification-webhook-url nil
                     :user-notes "メモテスト"
                     :is-headless true
                     :status :active
                     :consecutive-failures 0
                     :created-at (DateTimeOffset/UtcNow)
                     :updated-at (DateTimeOffset/UtcNow)
                     :last-checked-at nil
                     :last-lowest-price-jpy 145000
                     :last-lowest-airlines "AF"
                     :last-lowest-provider :google-flights
                     :ai-analysis-summary nil}
          card-html (h/render-html (dash/render-task-card task-item))
          list-html (h/render-html (dash/render-list-view [task-item]))
          empty-html (h/render-html (dash/render-empty-state))
          dash-html (h/render-html (dash/render-dashboard-content [task-item]))]
      ;; 1. 空状態ボタン
      (is (str/includes? empty-html "hx-get=\"/api/tasks/new-modal\""))
      (is (not (str/includes? empty-html "openNewTaskModal()")))
      ;; 2. カード内の編集・メモ・詳細ボタン
      (is (not (str/includes? card-html "openEditModal(")))
      (is (not (str/includes? card-html "openQuickNoteModal(")))
      (is (not (str/includes? card-html "openDetailModal(")))
      ;; 3. リストビュー内の編集・メモ・詳細ボタン
      (is (not (str/includes? list-html "openEditModal(")))
      (is (not (str/includes? list-html "openQuickNoteModal(")))
      (is (not (str/includes? list-html "openDetailModal(")))
      ;; 4. 正規表現による厳密検証: <button ...> タグ内に hx-get と onclick="open...Modal" が同居していないこと
      (is (nil? (re-find (re-pattern "<button[^>]*hx-get[^>]*onclick=[\"']open.*Modal") dash-html)))
      (is (nil? (re-find (re-pattern "<button[^>]*onclick=[\"']open.*Modal[^>]*hx-get") dash-html)))
      ;; 5. parseWithAI にスピナーと openModalSync が含まれること
      (is (str/includes? dash-html "openModalSync"))
      (is (str/includes? dash-html "fa-spinner")))))

