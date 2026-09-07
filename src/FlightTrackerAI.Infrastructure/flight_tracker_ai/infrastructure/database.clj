(ns flight-tracker-ai.infrastructure.database
  (:import [System AppDomain Environment String StringComparison Convert Activator]
           [System.IO File Directory DirectoryInfo Path]
           [System.Runtime.InteropServices NativeLibrary]
           [System.Reflection Assembly]))

;; -------------------------------------------------------------
;; 1. SQLite & Native Library Bootstrap
;; -------------------------------------------------------------
(defonce ^:private sqlite-connection-type
  (let [curr (Directory/GetCurrentDirectory)
        base (.. AppDomain -CurrentDomain -BaseDirectory)
        combine (fn [& parts] (Path/GetFullPath (String/Join (str Path/DirectorySeparatorChar) (into-array String (map str parts)))))
        native-candidates [(combine curr "src" "FlightTrackerAI.Infrastructure" "bin" "Debug" "net10.0" "runtimes" "win-x64" "native" "e_sqlite3.dll")
                           (combine curr "runtimes" "win-x64" "native" "e_sqlite3.dll")
                           (combine base "runtimes" "win-x64" "native" "e_sqlite3.dll")]
        _ (when-let [np (first (filter #(File/Exists %) native-candidates))]
            (try (NativeLibrary/Load np) (catch Exception _ nil)))
        asm-candidates [(combine curr "src" "FlightTrackerAI.Infrastructure" "bin" "Debug" "net10.0" "Microsoft.Data.Sqlite.dll")
                        (combine base "Microsoft.Data.Sqlite.dll")]
        asm (or (some (fn [p]
                        (when (File/Exists p)
                          (try (Assembly/LoadFrom (Path/GetFullPath p)) (catch Exception _ nil))))
                      asm-candidates)
                (try (Assembly/Load "Microsoft.Data.Sqlite") (catch Exception _ nil)))]
    (if asm
      (.GetType ^Assembly asm "Microsoft.Data.Sqlite.SqliteConnection")
      (Type/GetType "Microsoft.Data.Sqlite.SqliteConnection, Microsoft.Data.Sqlite"))))

;; -------------------------------------------------------------
;; 2. DotEnv Loader
;; -------------------------------------------------------------
(defn load-dotenv []
  (try
    (let [curr (Directory/GetCurrentDirectory)
          candidates [(DirectoryInfo. curr)
                      (DirectoryInfo. (.. AppDomain -CurrentDomain -BaseDirectory))]
          find-env (fn [^DirectoryInfo dir]
                     (loop [d dir depth 0]
                       (if (or (> depth 10) (nil? d))
                         nil
                         (let [p (Path/Combine (.FullName d) ".env")]
                           (if (File/Exists p)
                             p
                             (recur (.Parent d) (inc depth)))))))
          env-path (some find-env candidates)]
      (when env-path
        (doseq [line (File/ReadAllLines env-path)]
          (let [trimmed (.Trim line)]
            (when (and (not (.StartsWith trimmed "#")) (.Contains trimmed "="))
              (let [idx (.IndexOf trimmed "=")
                    k (.Trim (.Substring trimmed 0 idx))
                    raw-v (.Trim (.Substring trimmed (inc idx)))
                    v (if (and (>= (count raw-v) 2)
                               (or (and (.StartsWith raw-v "\"") (.EndsWith raw-v "\""))
                                   (and (.StartsWith raw-v "'") (.EndsWith raw-v "'"))))
                        (.Substring raw-v 1 (- (count raw-v) 2))
                        raw-v)]
                (when-not (String/IsNullOrWhiteSpace k)
                  (Environment/SetEnvironmentVariable k v))))))))
    (catch Exception _ nil)))

;; -------------------------------------------------------------
;; 3. Database Connection (WAL & Foreign Keys Enabled)
;; -------------------------------------------------------------
(defn create-connection [^String connection-string]
  (load-dotenv)
  (when-not sqlite-connection-type
    (throw (InvalidOperationException. "Microsoft.Data.Sqlite.SqliteConnection type could not be loaded.")))
  (let [conn (Activator/CreateInstance ^Type sqlite-connection-type (into-array Object [connection-string]))]
    (.Open conn)
    (with-open [cmd (.CreateCommand conn)]
      (set! (.CommandText cmd) "PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;")
      (.ExecuteNonQuery cmd))
    conn))

(defn- column-exists? [conn ^String table-name ^String col-name]
  (with-open [cmd (.CreateCommand conn)]
    (set! (.CommandText cmd) (str "PRAGMA table_info(" table-name ");"))
    (with-open [reader (.ExecuteReader cmd)]
      (loop []
        (if (.Read reader)
          (let [c-name (.GetString reader 1)]
            (if (.Equals c-name col-name StringComparison/OrdinalIgnoreCase)
              true
              (recur)))
          false)))))

;; -------------------------------------------------------------
;; 4. Database Initialization & Idempotent Migration
;; -------------------------------------------------------------
(def create-tables-ddl
  "
  -- 1. システム全体設定
  CREATE TABLE IF NOT EXISTS system_settings (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      default_check_interval_hours INTEGER NOT NULL DEFAULT 12,
      default_webhook_url TEXT,
      openrouter_api_key TEXT,
      enable_google_flights INTEGER NOT NULL DEFAULT 1,
      enable_skyscanner INTEGER NOT NULL DEFAULT 1,
      headless_mode INTEGER NOT NULL DEFAULT 1,
      updated_at TEXT NOT NULL
  );

  -- 2. 航空券監視タスクテーブル
  CREATE TABLE IF NOT EXISTS tasks (
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

  CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);

  -- 3. タスク巡回実行ログ
  CREATE TABLE IF NOT EXISTS task_run_logs (
      id TEXT PRIMARY KEY,
      task_id TEXT NOT NULL,
      executed_at TEXT NOT NULL,
      status TEXT NOT NULL,
      provider TEXT NOT NULL,
      found_offers_count INTEGER NOT NULL DEFAULT 0,
      lowest_price_jpy INTEGER,
      duration_ms INTEGER NOT NULL DEFAULT 0,
      error_message TEXT,
      ai_analysis_summary TEXT,
      FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
  );

  CREATE INDEX IF NOT EXISTS idx_run_logs_task_executed ON task_run_logs(task_id, executed_at);

  -- 4. 収集便スナップショットテーブル
  CREATE TABLE IF NOT EXISTS flight_snapshots (
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
      captured_at TEXT NOT NULL,
      FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
      FOREIGN KEY (run_log_id) REFERENCES task_run_logs(id) ON DELETE CASCADE
  );

  CREATE INDEX IF NOT EXISTS idx_snapshots_task_captured ON flight_snapshots(task_id, captured_at);
  CREATE INDEX IF NOT EXISTS idx_snapshots_task_price ON flight_snapshots(task_id, price_jpy);
  ")

(def seed-ddl
  "
  INSERT OR IGNORE INTO system_settings (
      id, default_check_interval_hours, default_webhook_url, openrouter_api_key,
      enable_google_flights, enable_skyscanner, headless_mode, updated_at
  ) VALUES (
      1, 12, NULL, NULL, 1, 1, 1, datetime('now')
  );
  ")

(defn initialize-database [^String connection-string]
  (with-open [conn (create-connection connection-string)]
    ;; 1. 基本テーブル作成
    (with-open [cmd (.CreateCommand conn)]
      (set! (.CommandText cmd) create-tables-ddl)
      (.ExecuteNonQuery cmd))

    ;; 2. 冪等なマイグレーション (カラム追加)
    (when-not (column-exists? conn "system_settings" "headless_mode")
      (with-open [cmd (.CreateCommand conn)]
        (set! (.CommandText cmd) "ALTER TABLE system_settings ADD COLUMN headless_mode INTEGER NOT NULL DEFAULT 1;")
        (.ExecuteNonQuery cmd)))

    (when-not (column-exists? conn "tasks" "is_headless")
      (with-open [cmd (.CreateCommand conn)]
        (set! (.CommandText cmd) "ALTER TABLE tasks ADD COLUMN is_headless INTEGER NOT NULL DEFAULT 1;")
        (.ExecuteNonQuery cmd)))

    (when-not (column-exists? conn "tasks" "ai_analysis_summary")
      (with-open [cmd (.CreateCommand conn)]
        (set! (.CommandText cmd) "ALTER TABLE tasks ADD COLUMN ai_analysis_summary TEXT;")
        (.ExecuteNonQuery cmd)))

    (when-not (column-exists? conn "task_run_logs" "ai_analysis_summary")
      (with-open [cmd (.CreateCommand conn)]
        (set! (.CommandText cmd) "ALTER TABLE task_run_logs ADD COLUMN ai_analysis_summary TEXT;")
        (.ExecuteNonQuery cmd)))

    ;; 3. 初期シードデータ投入
    (with-open [cmd (.CreateCommand conn)]
      (set! (.CommandText cmd) seed-ddl)
      (.ExecuteNonQuery cmd))
    nil))
