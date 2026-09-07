(ns flight-tracker-ai.infrastructure.app-logger
  (:import [System DateTimeOffset TimeSpan Console ConsoleColor AppDomain]
           [System.IO File Directory DirectoryInfo Path]
           [System.Text Encoding]))

(defonce ^:private lock-obj (Object.))

(defn- find-solution-root []
  (try
    (let [curr (Directory/GetCurrentDirectory)]
      (loop [dir (DirectoryInfo. curr)
             depth 0]
        (if (or (> depth 10) (nil? dir))
          curr
          (let [marker (Path/Combine (.FullName dir) "FlightTrackerAI.slnx")]
            (if (File/Exists marker)
              (.FullName dir)
              (recur (.Parent dir) (inc depth)))))))
    (catch Exception _
      (Directory/GetCurrentDirectory))))

(defn get-log-file-path []
  (try
    (let [root (find-solution-root)
          work-dir (Path/Combine root "doc" "work")]
      (when-not (Directory/Exists work-dir)
        (Directory/CreateDirectory work-dir))
      (Path/Combine work-dir "app.log"))
    (catch Exception _
      "app.log")))

(defn write-log [level category message]
  (locking lock-obj
    (try
      (let [jst-now (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0))
            time-str (.ToString jst-now "yyyy-MM-dd HH:mm:ss.fff")
            log-line (str "[" time-str " JST] [" level "] [" category "] " message)
            prev-color Console/ForegroundColor]
        ;; Console output with colors
        (try
          (cond
            (= level "ERROR") (set! Console/ForegroundColor ConsoleColor/Red)
            (= level "WARN") (set! Console/ForegroundColor ConsoleColor/Yellow)
            (= level "SUCCESS") (set! Console/ForegroundColor ConsoleColor/Green)
            :else (set! Console/ForegroundColor ConsoleColor/Cyan))
          (Console/WriteLine log-line)
          (finally
            (set! Console/ForegroundColor prev-color)))
        ;; File output append
        (let [path (get-log-file-path)]
          (File/AppendAllLines path (into-array String [log-line]) Encoding/UTF8)))
      (catch Exception _
        nil))))

(defn info [category message]
  (write-log "INFO" category message))

(defn success [category message]
  (write-log "SUCCESS" category message))

(defn warn [category message]
  (write-log "WARN" category message))

(defn error [category message]
  (write-log "ERROR" category message))

(defn error-ex [category message ^Exception ex]
  (let [ex-msg (if ex (str message ": " (.Message ex) "\n" (.StackTrace ex)) message)]
    (write-log "ERROR" category ex-msg)))

(defn get-recent-logs [line-count]
  (locking lock-obj
    (try
      (let [path (get-log-file-path)]
        (if (File/Exists path)
          (let [all-lines (File/ReadAllLines path Encoding/UTF8)
                len (count all-lines)
                skip-n (max 0 (- len line-count))]
            (vec (drop skip-n all-lines)))
          ["ログファイルはまだ生成されていません。"]))
      (catch Exception ex
        [(str "ログの読み込み中にエラーが発生しました: " (.Message ex))]))))

