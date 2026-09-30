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

(deftest test-new-columns-and-defaults-exist
  (testing "initialize-database creates tables with new columns and indices"
    (let [conn-str (create-in-memory-conn-str)]
      (db/initialize-database conn-str)
      (with-open [conn (db/create-connection conn-str)]
        ;; 1. system_settings
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd) "SELECT default_max_results_count FROM system_settings WHERE id = 1;")
          (with-open [reader (.ExecuteReader cmd)]
            (is (.Read reader))
            (is (= 10 (.GetInt32 reader 0)))))

        ;; 2. tasks columns
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd)
                "SELECT outbound_time_range, inbound_time_range, max_results_count, last_lowest_flight_number FROM tasks LIMIT 0;")
          (with-open [reader (.ExecuteReader cmd)]
            (is (not (.Read reader)))))

        ;; 3. flight_snapshots columns
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd)
                "SELECT flight_number, flight_key FROM flight_snapshots LIMIT 0;")
          (with-open [reader (.ExecuteReader cmd)]
            (is (not (.Read reader)))))

        ;; 4. flight_key index
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd)
                "SELECT count(*) FROM sqlite_master WHERE type='index' AND name='idx_snapshots_flight_key';")
          (let [idx-count (Convert/ToInt32 (.ExecuteScalar cmd))]
            (is (= 1 idx-count))))))))

(deftest test-migration-adds-columns-to-legacy-schema
  (testing "legacy schema without new columns is successfully migrated"
    (let [conn-str (create-in-memory-conn-str)]
      ;; Create legacy schema manually
      (with-open [conn (db/create-connection conn-str)]
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd)
                "
                CREATE TABLE system_settings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    default_check_interval_hours INTEGER NOT NULL DEFAULT 12,
                    default_webhook_url TEXT,
                    openrouter_api_key TEXT,
                    enable_google_flights INTEGER NOT NULL DEFAULT 1,
                    enable_skyscanner INTEGER NOT NULL DEFAULT 1,
                    headless_mode INTEGER NOT NULL DEFAULT 1,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE tasks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    destination TEXT NOT NULL,
                    trip_type TEXT NOT NULL,
                    outbound_date TEXT NOT NULL,
                    inbound_date TEXT,
                    preferred_airlines TEXT NOT NULL DEFAULT '[]',
                    max_stops TEXT NOT NULL DEFAULT 'Any',
                    target_price_jpy INTEGER,
                    check_interval_hours INTEGER NOT NULL DEFAULT 12,
                    webhook_url TEXT,
                    user_notes TEXT,
                    is_headless INTEGER NOT NULL DEFAULT 1,
                    status TEXT NOT NULL DEFAULT 'Active',
                    error_message TEXT,
                    consecutive_failures INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    last_checked_at TEXT,
                    last_lowest_price_jpy INTEGER,
                    last_lowest_airlines TEXT,
                    last_lowest_provider TEXT,
                    ai_analysis_summary TEXT
                );
                CREATE TABLE flight_snapshots (
                    id TEXT PRIMARY KEY,
                    task_id TEXT NOT NULL,
                    run_log_id TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    airlines_summary TEXT NOT NULL,
                    departure_time TEXT NOT NULL,
                    arrival_time TEXT NOT NULL,
                    total_duration_minutes INTEGER NOT NULL,
                    stops_count INTEGER NOT NULL DEFAULT 0,
                    segments_json TEXT NOT NULL DEFAULT '[]',
                    price_jpy INTEGER NOT NULL,
                    booking_url TEXT NOT NULL,
                    captured_at TEXT NOT NULL
                );
                INSERT INTO system_settings (id, default_check_interval_hours, updated_at) VALUES (1, 6, datetime('now'));
                ")
          (.ExecuteNonQuery cmd)))

      ;; Run migration via initialize-database
      (db/initialize-database conn-str)

      ;; Verify migrated columns and data preservation
      (with-open [conn (db/create-connection conn-str)]
        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd) "SELECT default_check_interval_hours, default_max_results_count FROM system_settings WHERE id = 1;")
          (with-open [reader (.ExecuteReader cmd)]
            (is (.Read reader))
            (is (= 6 (.GetInt32 reader 0)))
            (is (= 10 (.GetInt32 reader 1)))))

        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd) "SELECT outbound_time_range, inbound_time_range, max_results_count, last_lowest_flight_number FROM tasks LIMIT 0;")
          (with-open [reader (.ExecuteReader cmd)]
            (is (not (.Read reader)))))

        (with-open [cmd (.CreateCommand conn)]
          (set! (.CommandText cmd) "SELECT flight_number, flight_key FROM flight_snapshots LIMIT 0;")
          (with-open [reader (.ExecuteReader cmd)]
            (is (not (.Read reader)))))))))
