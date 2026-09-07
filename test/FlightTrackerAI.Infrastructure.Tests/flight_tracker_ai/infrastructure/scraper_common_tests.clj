(ns flight-tracker-ai.infrastructure.scraper-common-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common])
  (:import [System.IO Directory]))

(deftest test-parse-price-jpy
  (testing "parse-price-jpy parses valid prices correctly"
    (is (= 148200 (scraper-common/parse-price-jpy "¥148,200")))
    (is (= 148200 (scraper-common/parse-price-jpy "148200円")))
    (is (= 95000 (scraper-common/parse-price-jpy "￥95,000")))
    (is (= 32500 (scraper-common/parse-price-jpy " 32,500 JPY "))))

  (testing "parse-price-jpy returns nil for invalid prices"
    (is (nil? (scraper-common/parse-price-jpy "")))
    (is (nil? (scraper-common/parse-price-jpy "   ")))
    (is (nil? (scraper-common/parse-price-jpy "無料")))))

(deftest test-parse-duration-minutes
  (testing "parse-duration-minutes parses various duration formats"
    (is (= 875 (scraper-common/parse-duration-minutes "14時間35分")))
    (is (= 420 (scraper-common/parse-duration-minutes "7時間")))
    (is (= 45 (scraper-common/parse-duration-minutes "45分")))
    (is (= 875 (scraper-common/parse-duration-minutes "14h 35m")))
    (is (= 1320 (scraper-common/parse-duration-minutes "22h")))))

(deftest test-get-browser-profile-dir
  (testing "get-browser-profile-dir returns existing directory path"
    (let [dir (scraper-common/get-browser-profile-dir)]
      (is (some? dir))
      (is (Directory/Exists dir)))))

(deftest test-close-context-async-null-safe
  (testing "close-context-async handles nil context safely without throwing"
    (scraper-common/close-context-async nil)
    (is true)))

(deftest test-with-scraper-lock-mutual-exclusion
  (testing "with-scraper-lock serializes concurrent executions"
    (let [log (atom [])
          f1 (future
               (scraper-common/with-scraper-lock
                 (swap! log conj :start-1)
                 (System.Threading.Thread/Sleep 50)
                 (swap! log conj :end-1)))
          f2 (future
               (System.Threading.Thread/Sleep 10)
               (scraper-common/with-scraper-lock
                 (swap! log conj :start-2)
                 (System.Threading.Thread/Sleep 10)
                 (swap! log conj :end-2)))]
      @f1
      @f2
      (is (= [:start-1 :end-1 :start-2 :end-2] @log)))))

