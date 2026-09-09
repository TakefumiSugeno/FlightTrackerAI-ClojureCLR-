(ns flight-tracker-ai.web.views.layout-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.web.views.layout :as layout]
            [clojure.string :as str]))

(deftest test-base-layout
  (testing "base-layout renders HTML boilerplate and title"
    (let [res (layout/base-layout "テストタイトル" [:div "コンテンツ"])]
      (is (str/includes? res "<title>テストタイトル - FlightTrackerAI</title>"))
      (is (str/includes? res "FlightTrackerAI"))
      (is (str/includes? res "tailwindcss"))
      (is (str/includes? res "コンテンツ")))))

(deftest test-base-layout-header-actions
  (testing "base-layout renders header with log viewer, settings, and new task buttons"
    (let [res (layout/base-layout "テスト" [:div "テスト"])]
      (is (str/includes? res "/api/logs/modal"))
      (is (str/includes? res "ログ確認"))
      (is (str/includes? res "/api/settings/modal"))
      (is (str/includes? res "/api/tasks/new-modal"))
      (is (str/includes? res "insertTemplate")))))

(deftest test-base-layout-modal-and-esc-handlers
  (testing "base-layout includes state machine, history sync, dirty check, and Escape key listener with IME guard"
    (let [res (layout/base-layout "テスト" [:div "テスト"])]
      (is (str/includes? res "window.__modalState"))
      (is (str/includes? res "closeCurrentModal"))
      (is (str/includes? res "window.closeCurrentModal = closeCurrentModal"))
      (is (str/includes? res "isFormDirty"))
      (is (str/includes? res "openModalSync"))
      (is (str/includes? res "popstate"))
      (is (str/includes? res "isComposing"))
      (is (str/includes? res "closeModal"))
      (is (str/includes? res "htmx:afterSwap"))
      (is (str/includes? res "overflow-hidden"))
      (is (str/includes? res "modal-container")))))

