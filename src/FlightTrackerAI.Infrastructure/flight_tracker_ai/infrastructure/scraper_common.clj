;; Playwright .NET 依存アセンブリの自動ロード (Microsoft.Playwright.dll)
(let [curr (System.IO.Directory/GetCurrentDirectory)
      base (.. System.AppDomain -CurrentDomain -BaseDirectory)
      combine (fn [& parts] (System.IO.Path/Combine (into-array String (map str parts))))
      candidates [(combine curr "src" "FlightTrackerAI.Infrastructure" "bin" "Debug" "net10.0")
                  (combine base "src" "FlightTrackerAI.Infrastructure" "bin" "Debug" "net10.0")
                  (combine base "..")
                  base]]
  (doseq [dir candidates]
    (when (System.IO.Directory/Exists dir)
      (let [playwright-dll (System.IO.Path/Combine dir "Microsoft.Playwright.dll")]
        (when (System.IO.File/Exists playwright-dll)
          (try (System.Reflection.Assembly/LoadFrom playwright-dll) (catch System.Exception _ nil)))))))

(ns flight-tracker-ai.infrastructure.scraper-common
  (:require [flight-tracker-ai.infrastructure.app-logger :as logger]
            [clojure.string :as str])
  (:import [System AppDomain Environment String Char Nullable Type Activator]
           [System.IO File Directory DirectoryInfo Path IOException]
           [System.Threading SemaphoreSlim]
           [System.Threading.Tasks Task]
           [System.Collections IDictionary]
           [Microsoft.Playwright IPlaywright IBrowserContext IPage BrowserTypeLaunchPersistentContextOptions ViewportSize]))

(defn- make-string-dict [kv-map]
  (let [dict-type (.MakeGenericType (Type/GetType "System.Collections.Generic.Dictionary`2")
                                     (into-array Type [String String]))
        ^IDictionary d (Activator/CreateInstance dict-type)]
    (doseq [[k v] kv-map]
      (.Add d (str k) (str v)))
    d))

;; -------------------------------------------------------------
;; 1. Task Await & Locks
;; -------------------------------------------------------------
(defn await-task [task]
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
;; 2. Directories & SingletonLock Cleanup
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

(defn cleanup-singleton-locks! [^String profile-dir]
  (try
    (let [lock-names ["SingletonLock" "SingletonCookie" "SingletonSocket"]]
      (doseq [name lock-names]
        (let [p (Path/Combine profile-dir name)]
          (when (File/Exists p)
            (try
              (File/Delete p)
              (logger/info "Scraper" (str "残留ロックファイルを削除しました: " name))
              (catch IOException _
                ;; 稼働中のプロセスがロックを保持している場合はスルー
                nil)
              (catch Exception _ nil))))))
    (catch Exception _ nil)))

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
(defonce browser-installed-state (atom :uninstalled))
(defonce ^:private browser-lock (Object.))

(defn ensure-playwright-browsers-installed! []
  (locking browser-lock
    (when (= @browser-installed-state :uninstalled)
      (let [auto-install? (= "true" (System.Environment/GetEnvironmentVariable "PLAYWRIGHT_AUTO_INSTALL"))]
        (if auto-install?
          (do
            (reset! browser-installed-state :installing)
            (try
              (logger/info "Scraper" "Playwright Chromium ブラウザバイナリのプロビジョニングを確認中...")
              (let [exit-code (Microsoft.Playwright.Program/Main (into-array String ["install" "chromium"]))]
                (if (= exit-code 0)
                  (do
                    (reset! browser-installed-state :installed)
                    (logger/info "Scraper" "Playwright Chromium の準備が完了しました。"))
                  (do
                    (reset! browser-installed-state :failed)
                    (logger/warn "Scraper" (str "Playwright Chromium インストールが終了コード " exit-code " で完了しました。")))))
              (catch Exception ex
                (reset! browser-installed-state :failed)
                (logger/error-ex "Scraper" "Playwright Chromium 自動インストール例外" ex))))
          ;; パッケージ管理（MSBuild Target 'InstallPlaywrightBrowsers' / scripts/install-browsers.ps1）による管理を標準とする
          (reset! browser-installed-state :installed))))))

(defn create-context-async [playwright headless]
  (ensure-playwright-browsers-installed!)
  (let [profile-dir (get-browser-profile-dir)]
    (cleanup-singleton-locks! profile-dir)
    (logger/info "Scraper" (str "Playwright ブラウザを起動します (ヘッドレス: " (boolean headless) ", プロファイル: " profile-dir ")"))
    (let [options (BrowserTypeLaunchPersistentContextOptions.)
          default-args ["--disable-blink-features=AutomationControlled"
                        "--disable-infobars"
                        "--disable-dev-shm-usage"
                        "--no-sandbox"
                        "--window-size=1440,900"]
          args (if-not headless
                 (concat default-args ["--start-maximized"])
                 default-args)]
      (set! (.Headless options) (boolean headless))
      (set! (.Args options) (into-array String args))
      (if-not headless
        (do
          (set! (.SlowMo options) (float 150.0))
          (set! (.ViewportSize options) nil))
        (let [vp (ViewportSize.)]
          (set! (.Width vp) 1440)
          (set! (.Height vp) 900)
          (set! (.ViewportSize options) vp)))
      (set! (.UserAgent options) "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
      (set! (.Locale options) "ja-JP")
      (set! (.TimezoneId options) "Asia/Tokyo")
      (set! (.BypassCSP options) true)
      (set! (.IgnoreHTTPSErrors options) true)
      (set! (.ExtraHTTPHeaders options)
            (make-string-dict {"Accept-Language" "ja,en-US;q=0.9,en;q=0.8"
                               "Sec-Ch-Ua" "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\""
                               "Sec-Ch-Ua-Mobile" "?0"
                               "Sec-Ch-Ua-Platform" "\"Windows\""
                               "Sec-Fetch-Dest" "document"
                               "Sec-Fetch-Mode" "navigate"
                               "Sec-Fetch-Site" "none"
                               "Sec-Fetch-User" "?1"
                               "Upgrade-Insecure-Requests" "1"}))

      (let [context (await-task (.. playwright -Chromium (LaunchPersistentContextAsync profile-dir options)))
            stealth-script (str "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});\n"
                                "window.chrome = { runtime: {} };\n"
                                "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });\n"
                                "Object.defineProperty(navigator, 'languages', { get: () => ['ja-JP', 'ja', 'en-US', 'en'] });\n"
                                "const originalQuery = window.navigator.permissions.query;\n"
                                "window.navigator.permissions.query = (parameters) => (\n"
                                "    parameters.name === 'notifications' ?\n"
                                "        Promise.resolve({ state: Notification.permission }) :\n"
                                "        originalQuery(parameters)\n"
                                ");\n")]
        (await-task (.AddInitScriptAsync context stealth-script nil))
        context))))

(defn close-context-async [context]
  (when context
    (try
      (let [pages (.Pages context)]
        (doseq [p pages]
          (try (await-task (.CloseAsync p nil)) (catch Exception _ nil))))
      (catch Exception _ nil))
    (try
      (await-task (.CloseAsync context nil))
      (catch Exception _ nil))
    (try
      (when-let [browser (.Browser context)]
        (await-task (.CloseAsync browser nil)))
      (catch Exception _ nil))))
