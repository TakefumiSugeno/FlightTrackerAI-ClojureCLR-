(ns flight-tracker-ai.infrastructure.app-logger-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.app-logger :as logger]
            [clojure.string :as str]))

(deftest test-app-logger-records-logs
  (testing "AppLogger records info, success, and error logs to file and recent logs"
    (logger/info "TestCategory" "This is an info log message")
    (logger/success "TestCategory" "This is a success log message")
    (logger/error "TestCategory" "This is an error log message")
    (let [recent-logs (logger/get-recent-logs 10)]
      (is (seq recent-logs))
      (is (some #(str/includes? % "This is an info log message") recent-logs))
      (is (some #(str/includes? % "This is a success log message") recent-logs))
      (is (some #(str/includes? % "This is an error log message") recent-logs)))))

(deftest test-app-logger-get-log-file-path
  (testing "AppLogger get-log-file-path returns valid path ending in app.log"
    (let [path (logger/get-log-file-path)]
      (is (not (str/blank? path)))
      (is (str/ends-with? path "app.log")))))
