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
  (testing "base-layout renders header with log viewer and settings buttons"
    (let [res (layout/base-layout "テスト" [:div "テスト"])]
      (is (str/includes? res "/api/logs/modal"))
      (is (str/includes? res "ログ確認"))
      (is (str/includes? res "/api/settings/modal"))
      (is (str/includes? res "insertTemplate"))
      (is (str/includes? res "parseWithAI")))))
