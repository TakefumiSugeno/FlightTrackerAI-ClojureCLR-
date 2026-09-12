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

(deftest test-get-screenshot-dir
  (testing "get-screenshot-dir returns existing directory path"
    (let [dir (scraper-common/get-screenshot-dir)]
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

(deftest test-cleanup-singleton-locks
  (testing "cleanup-singleton-locks! removes dangling lock files safely"
    (let [profile-dir (scraper-common/get-browser-profile-dir)
          lock-file (System.IO.Path/Combine profile-dir "SingletonLock")
          cookie-file (System.IO.Path/Combine profile-dir "SingletonCookie")]
      (System.IO.File/WriteAllText lock-file "dummy-lock")
      (System.IO.File/WriteAllText cookie-file "dummy-cookie")
      (is (System.IO.File/Exists lock-file))
      (scraper-common/cleanup-singleton-locks! profile-dir)
      (is (not (System.IO.File/Exists lock-file)))
      (is (not (System.IO.File/Exists cookie-file))))))

(defn- make-completed-task-result [val]
  (let [m (first (filter #(= (.Name %) "FromResult") (.GetMethods System.Threading.Tasks.Task)))
        gm (.MakeGenericMethod m (into-array System.Type [(.GetType val)]))]
    (.Invoke gm nil (into-array Object [val]))))

(deftest test-await-task-null-safe
  (testing "await-task returns nil when given nil task"
    (is (nil? (scraper-common/await-task nil))))
  (testing "await-task resolves completed task correctly"
    (let [task System.Threading.Tasks.Task/CompletedTask]
      (scraper-common/await-task task)
      (is true)))
  (testing "await-task resolves generic Task<T> result value correctly without dropping to nil"
    (let [task (make-completed-task-result "test-val")]
      (is (= "test-val" (scraper-common/await-task task))))))

(deftest test-browser-installed-state
  (testing "ensure-playwright-browsers-installed! does not throw and manages state"
    (scraper-common/ensure-playwright-browsers-installed!)
    (is (contains? #{:installed :installing :uninstalled :failed}
                   @scraper-common/browser-installed-state))))

