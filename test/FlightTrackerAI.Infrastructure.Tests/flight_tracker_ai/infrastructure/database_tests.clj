(ns flight-tracker-ai.infrastructure.database-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db])
  (:import [System Guid Convert]))

(defn- create-in-memory-conn-str []
  (str "Data Source=file:db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared"))

(deftest test-initialize-database-creates-tables-and-defaults
  (testing "initialize-database creates all required tables and default settings"
    (let [conn-str (create-in-memory-conn-str)]
      (db/initialize-database conn-str)
      (with-open [conn (db/create-connection conn-str)]
        ;; Check table existence
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd)
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('system_settings', 'tasks', 'task_run_logs', 'flight_snapshots');")
          (let [tbl-count (Convert/ToInt32 (.ExecuteScalar cmd))]
            (is (= 4 tbl-count))))

        ;; Check default settings record (id = 1)
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd)
                "SELECT default_check_interval_hours, enable_google_flights, enable_skyscanner, headless_mode FROM system_settings WHERE id = 1;")
          (with-open [reader (.ExecuteReader cmd)]
            (is (.Read reader))
            (is (= 12 (.GetInt32 reader 0)))
            (is (= 1 (.GetInt32 reader 1)))
            (is (= 1 (.GetInt32 reader 2)))
            (is (= 1 (.GetInt32 reader 3)))))))))

(deftest test-initialize-database-idempotence
  (testing "initialize-database is idempotent and does not throw on repeated calls"
    (let [conn-str (create-in-memory-conn-str)]
      (db/initialize-database conn-str)
      (db/initialize-database conn-str)
      (db/initialize-database conn-str)
      (with-open [conn (db/create-connection conn-str)]
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd) "SELECT count(*) FROM system_settings WHERE id = 1;")
          (let [count (Convert/ToInt32 (.ExecuteScalar cmd))]
            (is (= 1 count))))))))
