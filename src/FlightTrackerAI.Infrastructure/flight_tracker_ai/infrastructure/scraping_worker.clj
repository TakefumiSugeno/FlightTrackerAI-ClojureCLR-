(ns flight-tracker-ai.infrastructure.scraping-worker
  (:require [flight-tracker-ai.infrastructure.app-logger :as logger]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.settings-repository :as settings-repo]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.flight-repository :as flight-repo]
            [flight-tracker-ai.infrastructure.notification :as notif]
            [flight-tracker-ai.infrastructure.google-flights-scraper :as gf]
            [flight-tracker-ai.infrastructure.skyscanner-scraper :as ss]
            [flight-tracker-ai.core.analysis :as analysis]
            [flight-tracker-ai.core.domain :as domain])
  (:import [System DateTimeOffset TimeSpan DateOnly Guid]
           [System.Net.Http HttpClient]
           [System.Threading Thread ThreadStart]))

;; -------------------------------------------------------------
;; 1. Worker Logic (Pure & Business Rules)
;; -------------------------------------------------------------
(defn is-task-due [^DateTimeOffset now task-item]
  (if (= (:status task-item) :active)
    (if-let [last-checked (:last-checked-at task-item)]
      (let [interval-hours (double (max 1 (or (:check-interval-hours task-item) 12)))
            due-time (.AddHours ^DateTimeOffset last-checked interval-hours)]
        (>= (compare now due-time) 0))
      true)
    false))

(defn is-task-expired [^DateOnly today task-item]
  (let [trip (:trip-type task-item)
        outbound ^DateOnly (or (:outbound trip) (:outbound-date trip))]
    (if outbound
      (< (.CompareTo outbound today) 0)
      false)))

(defn execute-task-scraping
  [^HttpClient http-client ^String connection-string task-item settings]
  (let [now (DateTimeOffset/UtcNow)
        jst-now (.ToOffset now (TimeSpan/FromHours 9.0))
        today (DateOnly/FromDateTime (.DateTime jst-now))]
    (logger/info "Scraper" (str "巡回パイプライン開始: '" (:title task-item) "' ["
                                (domain/iata-code-value (:origin task-item)) " ➔ "
                                (domain/iata-code-value (:destination task-item)) "]"))
    ;; 1. 出発日超過チェック (JST基準) ➔ 自動完了
    (if (is-task-expired today task-item)
      (do
        (logger/info "Scraper" (str "タスク '" (:title task-item) "' は出発日を超過したため完了 (Completed) に移行します。"))
        (let [completed-task (assoc task-item :status :completed :updated-at now)]
          (task-repo/update-task connection-string completed-task)
          {:ok nil}))
      ;; 2. 通常巡回実行
      (let [enable-gf (:enable-google-flights settings)
            enable-ss (:enable-skyscanner settings)]
        (if (or enable-gf enable-ss)
          ;; 排他制御下でのスクレイピング実行
          (scraper-common/with-scraper-lock
            (let [all-offers (atom [])
                  scrape-errors (atom [])]
              ;; Google Flights
              (when enable-gf
                (try
                  (let [run-log-id (flight-repo/create-run-log connection-string (:id task-item) :google-flights)
                        offers (gf/scrape-async nil task-item run-log-id)
                        lowest-price (:price-jpy (analysis/find-lowest-offer offers))]
                    (flight-repo/save-snapshots connection-string offers)
                    (flight-repo/complete-run-log connection-string run-log-id (count offers) lowest-price 0 nil)
                    (swap! all-offers concat offers))
                  (catch Exception ex
                    (logger/error-ex "Scraper" "Google Flights 巡回失敗" ex)
                    (swap! scrape-errors conj (.Message ex)))))
              ;; Skyscanner
              (when enable-ss
                (try
                  (let [run-log-id (flight-repo/create-run-log connection-string (:id task-item) :skyscanner)
                        offers (ss/scrape-async nil task-item run-log-id)
                        lowest-price (:price-jpy (analysis/find-lowest-offer offers))]
                    (flight-repo/save-snapshots connection-string offers)
                    (flight-repo/complete-run-log connection-string run-log-id (count offers) lowest-price 0 nil)
                    (swap! all-offers concat offers))
                  (catch Exception ex
                    (logger/error-ex "Scraper" "Skyscanner 巡回失敗" ex)
                    (swap! scrape-errors conj (.Message ex)))))

              ;; 最安値判定 & 更新
              (if-let [lowest (analysis/find-lowest-offer @all-offers)]
                (let [eval-res (analysis/evaluate-price-opportunity task-item lowest)]
                  (when (:should-notify eval-res)
                    (notif/dispatch-price-notification http-client task-item lowest
                                                       (:price-change-percent eval-res)
                                                       (:is-target-met eval-res)
                                                       (:default-webhook-url settings)))
                  (task-repo/update-check-result connection-string (:id task-item) now
                                                 (:price-jpy lowest)
                                                 (:airlines-summary lowest)
                                                 (:provider lowest))
                  {:ok lowest})
                (if (seq @scrape-errors)
                  (let [combined (clojure.string/join "; " @scrape-errors)]
                    (task-repo/record-failure connection-string (:id task-item) combined)
                    {:error combined})
                  (do
                    (task-repo/update-check-result connection-string (:id task-item) now nil nil nil)
                    {:ok nil})))))
          (do
            (logger/info "Scraper" "Google Flights および Skyscanner の両方が無効化されています。")
            {:ok nil}))))))

;; -------------------------------------------------------------
;; 2. Background Worker Lifecycle
;; -------------------------------------------------------------
(defonce ^:private worker-running (atom false))
(defonce ^:private worker-thread (atom nil))

(defn- worker-loop [^String connection-string]
  (let [http-client (HttpClient.)]
    (logger/info "Worker" "FlightTrackerAI 定期巡回バックグラウンドワーカーが開始しました。")
    (while @worker-running
      (try
        (let [now (DateTimeOffset/UtcNow)
              settings (settings-repo/get-settings connection-string)
              tasks (task-repo/get-active-tasks connection-string)
              due (filter #(is-task-due now %) tasks)]
          (when (seq due)
            (logger/info "Worker" (str "定期巡回対象タスク " (count due) " 件を検知しました。順次巡回を開始します。"))
            (doseq [t due]
              (when @worker-running
                (execute-task-scraping http-client connection-string t settings)
                (Thread/Sleep 3000)))))
        (catch Exception ex
          (logger/error-ex "Worker" "巡回ループ実行中に例外が発生しました" ex)))
      (try
        (let [elapsed (atom 0)]
          (while (and @worker-running (< @elapsed 60000))
            (Thread/Sleep 1000)
            (swap! elapsed + 1000)))
        (catch Exception _ nil)))
    (logger/info "Worker" "FlightTrackerAI 定期巡回バックグラウンドワーカーが停止しました。")))

(defn start-worker! [^String connection-string]
  (when (compare-and-set! worker-running false true)
    (let [t (Thread. (gen-delegate ThreadStart [] (worker-loop connection-string)))]
      (set! (.IsBackground t) true)
      (reset! worker-thread t)
      (.Start t)
      true)))

(defn stop-worker! []
  (reset! worker-running false))
