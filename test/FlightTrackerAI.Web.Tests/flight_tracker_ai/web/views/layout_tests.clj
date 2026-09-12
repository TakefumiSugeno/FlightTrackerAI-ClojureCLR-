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
  (testing "base-layout includes closeCurrentModal, showToast, parseWithAI, and modal container"
    (let [res (layout/base-layout "テスト" [:div "テスト"])]
      (is (str/includes? res "closeCurrentModal"))
      (is (str/includes? res "showToast"))
      (is (str/includes? res "parseWithAI"))
      (is (str/includes? res "closeModal"))
      (is (str/includes? res "font-awesome"))
      (is (str/includes? res "toastContainer"))
      (is (str/includes? res "modal-container")))))

