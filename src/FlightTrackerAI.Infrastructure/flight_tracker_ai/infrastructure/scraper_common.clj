(ns flight-tracker-ai.infrastructure.scraper-common
  (:require [flight-tracker-ai.infrastructure.app-logger :as logger]
            [clojure.string :as str])
  (:import [System AppDomain Environment String Char]
           [System.IO File Directory DirectoryInfo Path]
           [System.Threading SemaphoreSlim]
           [System.Threading.Tasks Task]))

;; -------------------------------------------------------------
;; 1. Task Await & Locks
;; -------------------------------------------------------------
(defn await-task [^Task task]
  (when task
    (.GetResult (.GetAwaiter (.ConfigureAwait task false)))))

(defonce scraper-lock (SemaphoreSlim. 1 1))

(defmacro with-scraper-lock [& body]
  `(do
     (await-task (.WaitAsync scraper-lock))
     (try
       ~@body
       (finally
         (.Release scraper-lock)))))

;; -------------------------------------------------------------
;; 2. Directories (Screenshots & Browser Profile)
;; -------------------------------------------------------------
(defn get-screenshot-dir []
  (try
    (let [curr (Directory/GetCurrentDirectory)
          base (.. AppDomain -CurrentDomain -BaseDirectory)
          candidates [(Path/Combine curr "doc" "work" "screenshots")
                      (Path/Combine base "doc" "work" "screenshots")]
          found (or (first (filter #(try (Directory/Exists (Path/GetDirectoryName (Path/GetFullPath %))) (catch Exception _ false)) candidates))
                    (Path/Combine curr "doc" "work" "screenshots"))
          full (Path/GetFullPath found)]
      (when-not (Directory/Exists full)
        (Directory/CreateDirectory full))
      full)
    (catch Exception _ "doc/work/screenshots")))

(defn get-browser-profile-dir []
  (try
    (let [curr (Directory/GetCurrentDirectory)
          base (.. AppDomain -CurrentDomain -BaseDirectory)
          candidates [(Path/Combine curr "doc" "work" "browser_profile")
                      (Path/Combine base "doc" "work" "browser_profile")]
          found (or (first (filter #(try (Directory/Exists (Path/GetDirectoryName (Path/GetFullPath %))) (catch Exception _ false)) candidates))
                    (Path/Combine curr "doc" "work" "browser_profile"))
          full (Path/GetFullPath found)]
      (when-not (Directory/Exists full)
        (Directory/CreateDirectory full))
      full)
    (catch Exception _ "doc/work/browser_profile")))

;; -------------------------------------------------------------
;; 3. Parsing Helpers (Price & Duration)
;; -------------------------------------------------------------
(defn parse-price-jpy [raw-text]
  (if (or (nil? raw-text) (str/blank? raw-text))
    nil
    (let [digits (str/join "" (filter #(Char/IsDigit ^Char %) (str raw-text)))]
      (if (str/blank? digits)
        nil
        (try
          (let [p (long (read-string digits))]
            (when (> p 0) p))
          (catch Exception _ nil))))))

(defn parse-duration-minutes [raw-text]
  (if (or (nil? raw-text) (str/blank? raw-text))
    0
    (let [trimmed (.Replace (str raw-text) " " "")]
      (cond
        (.Contains trimmed "時間")
        (let [parts (.Split trimmed (into-array String ["時間"]) System.StringSplitOptions/None)
              h (try (long (read-string (first parts))) (catch Exception _ 0))
              m (if (and (> (count parts) 1) (.Contains (second parts) "分"))
                  (try (long (read-string (.Replace (second parts) "分" ""))) (catch Exception _ 0))
                  0)]
          (+ (* h 60) m))

        (.Contains trimmed "h")
        (let [parts (.Split trimmed (into-array String ["h"]) System.StringSplitOptions/None)
              h (try (long (read-string (first parts))) (catch Exception _ 0))
              m (if (and (> (count parts) 1) (.Contains (second parts) "m"))
                  (try (long (read-string (.Replace (second parts) "m" ""))) (catch Exception _ 0))
                  0)]
          (+ (* h 60) m))

        (.Contains trimmed "分")
        (try (long (read-string (.Replace trimmed "分" ""))) (catch Exception _ 0))

        :else 0))))

;; -------------------------------------------------------------
;; 4. Playwright Browser Lifecycle
;; -------------------------------------------------------------
(defn close-context-async [context]
  (when context
    (try
      (let [pages (.Pages context)]
        (doseq [p pages]
          (try (await-task (.CloseAsync p)) (catch Exception _ nil))))
      (catch Exception _ nil))
    (try
      (await-task (.CloseAsync context))
      (catch Exception _ nil))
    (try
      (when-let [browser (.Browser context)]
        (await-task (.CloseAsync browser)))
      (catch Exception _ nil))))
