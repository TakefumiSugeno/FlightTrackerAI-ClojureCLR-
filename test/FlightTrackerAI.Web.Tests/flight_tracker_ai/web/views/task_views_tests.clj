(ns flight-tracker-ai.web.views.task-views-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.web.views.task-views :as tv]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [clojure.string :as str]))

(deftest test-render-task-form-controls
  (testing "render-task-form-controls outputs time range, max stops, and max results count options"
    (let [rendered (h/render-html (tv/render-task-form-controls {:outboundTimeRange "Morning"
                                                                 :inboundTimeRange "Evening"
                                                                 :maxStops "OneStop"
                                                                 :maxResultsCount 15}))]
      ;; Time ranges
      (is (str/includes? rendered "name=\"outboundTimeRange\""))
      (is (str/includes? rendered "name=\"inboundTimeRange\""))
      (is (str/includes? rendered "早朝 (00:00 - 06:00)"))
      (is (str/includes? rendered "午前 (06:00 - 12:00)"))
      (is (str/includes? rendered "夜間 (18:00 - 24:00)"))
      ;; Max stops
      (is (str/includes? rendered "name=\"maxStops\""))
      (is (str/includes? rendered "1箇所まで (1回以下の経由)"))
      ;; Max results count
      (is (str/includes? rendered "name=\"maxResultsCount\""))
      (is (str/includes? rendered "value=\"15\"")))))

(deftest test-render-flight-number-badge
  (testing "render-flight-number-badge displays flight numbers formatted with badge styling"
    (let [html (h/render-html (tv/render-flight-number-badge "5J 5055"))]
      (is (str/includes? html "5J 5055"))
      (is (str/includes? html "fa-plane")))
    (testing "nil or empty flight number returns nil or empty"
      (is (nil? (tv/render-flight-number-badge nil)))
      (is (nil? (tv/render-flight-number-badge ""))))))
