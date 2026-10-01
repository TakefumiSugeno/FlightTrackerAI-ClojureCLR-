(ns test-runner
  (:require [clojure.test :as t]
            [clojure.string :as str])
  (:import [System.IO File Directory Path SearchOption]
           [System DateTimeOffset TimeSpan Math]
           [System.Text Encoding]
           [System.Reflection Assembly]))

;; -------------------------------------------------------------
;; 1. アセンブリの動的プリロード
;; -------------------------------------------------------------
(defn preload-assemblies! []
  (let [curr (Directory/GetCurrentDirectory)
        base (.. System.AppDomain -CurrentDomain -BaseDirectory)
        src-dir (Path/Combine (into-array String [curr "src"]))
        bin-dirs (when (Directory/Exists src-dir)
                   (Directory/GetDirectories src-dir "net10.0" SearchOption/AllDirectories))
        candidates (distinct (filter identity (concat bin-dirs [base (Path/Combine (into-array String [base ".."]))])))]
    (doseq [dir candidates]
      (when (Directory/Exists dir)
        (doseq [dll (Directory/GetFiles dir "*.dll")]
          (try
            (Assembly/LoadFrom dll)
            (catch System.Exception _ nil)))))))

;; -------------------------------------------------------------
;; 2. テスト名前空間およびソースモジュールの動的探索
;; -------------------------------------------------------------
(defn find-test-namespaces []
  (let [curr (Directory/GetCurrentDirectory)
        test-dir (Path/Combine (into-array String [curr "test"]))
        files (if (Directory/Exists test-dir)
                (Directory/GetFiles test-dir "*_tests.clj" SearchOption/AllDirectories)
                (into-array String []))]
    (->> files
         (remove (fn [^String f]
                   (or (.Contains f "\\bin\\") (.Contains f "/bin/")
                       (.Contains f "\\obj\\") (.Contains f "/obj/"))))
         (keep (fn [^String f]
                 (try
                   (let [content (File/ReadAllText f)
                         m (re-find #"\(ns\s+([^\s\)\;]+)" content)]
                     (when (second m)
                       (symbol (second m))))
                   (catch System.Exception _ nil))))
         (sort-by str)
         vec)))

(defn find-source-modules []
  (let [curr (Directory/GetCurrentDirectory)
        src-dir (Path/Combine (into-array String [curr "src"]))
        files (if (Directory/Exists src-dir)
                (Directory/GetFiles src-dir "*.clj" SearchOption/AllDirectories)
                (into-array String []))]
    (->> files
         (remove (fn [^String f]
                   (or (.Contains f "\\bin\\") (.Contains f "/bin/")
                       (.Contains f "\\obj\\") (.Contains f "/obj/"))))
         (keep (fn [^String f]
                 (try
                   (let [content (File/ReadAllText f)
                         m (re-find #"\(ns\s+([^\s\)\;]+)" content)]
                     (when (second m)
                       {:file-path f
                        :ns-sym (symbol (second m))}))
                   (catch System.Exception _ nil))))
         (sort-by (comp str :ns-sym))
         vec)))

(defn analyze-source-lines [^String file-path]
  (let [lines (File/ReadAllLines file-path)
        total (alength lines)
        executable-lines (atom #{})]
    (dotimes [idx total]
      (let [line-no (inc idx)
            line-text (.Trim (aget lines idx))]
        (when (and (not (System.String/IsNullOrWhiteSpace line-text))
                   (not (.StartsWith line-text ";")))
          (swap! executable-lines conj line-no))))
    {:total-lines total
     :executable-lines @executable-lines
     :executable-count (count @executable-lines)}))

;; -------------------------------------------------------------
;; 3. Var インストルメンテーションと呼び出し追跡
;; -------------------------------------------------------------
(def call-counts (atom {}))

(defn instrument-var! [ns-sym var-sym v]
  (when (and (var? v) (fn? @v))
    (let [orig @v
          k [(ns-name (:ns (meta v))) (:name (meta v))]]
      (swap! call-counts assoc k 0)
      (alter-var-root v (fn [f]
                          (fn [& args]
                            (swap! call-counts update k (fnil inc 0))
                            (apply f args)))))))

(defn instrument-source-modules! [source-modules]
  (doseq [{:keys [ns-sym]} source-modules]
    (try
      (require ns-sym)
      (let [interns (ns-interns (the-ns ns-sym))]
        (doseq [[sym v] interns]
          (instrument-var! ns-sym sym v)))
      (catch System.Exception e
        (println (str "WARN: Failed to instrument namespace " ns-sym ": " (.Message e)))))))

(defn calculate-module-coverage [source-module]
  (let [{:keys [file-path ns-sym]} source-module
        {:keys [total-lines executable-lines executable-count]} (analyze-source-lines file-path)
        interns (try (ns-interns (the-ns ns-sym)) (catch System.Exception _ {}))
        var-metas (->> interns
                       (map (fn [[sym v]]
                              (let [m (meta v)]
                                {:name (str sym)
                                 :line (:line m)
                                 :var v
                                 :key [(ns-name (:ns m)) (:name m)]
                                 :is-fn (fn? @v)})))
                       (filter :line)
                       (sort-by :line))
        var-ranges (loop [vars var-metas
                          acc []]
                     (if (empty? vars)
                       acc
                       (let [curr (first vars)
                             next-var (second vars)
                             start-line (:line curr)
                             end-line (if next-var (dec (:line next-var)) total-lines)
                             calls (get @call-counts (:key curr) 0)
                             covered? (pos? calls)]
                         (recur (rest vars)
                                (conj acc (assoc curr
                                                 :start-line start-line
                                                 :end-line end-line
                                                 :covered? covered?
                                                 :calls calls))))))
        covered-lines (atom #{})
        uncovered-fns (atom [])]
    (doseq [vr var-ranges]
      (let [range-exec-lines (filter (fn [ln] (and (>= ln (:start-line vr)) (<= ln (:end-line vr))))
                                     executable-lines)]
        (if (:covered? vr)
          (doseq [ln range-exec-lines]
            (swap! covered-lines conj ln))
          (when (:is-fn vr)
            (swap! uncovered-fns conj (:name vr))))))
    ;; 名前空間先頭（ns宣言等）はモジュールがロードされていれば実行済みと判定
    (let [first-var-line (or (:line (first var-metas)) total-lines)
          preamble-lines (filter #(< % first-var-line) executable-lines)]
      (doseq [ln preamble-lines]
        (swap! covered-lines conj ln)))
    (let [covered-count (count @covered-lines)
          coverage-pct (if (pos? executable-count)
                         (Math/Round (* (/ (double covered-count) (double executable-count)) 100.0) 1)
                         100.0)]
      {:name (str ns-sym)
       :file-path file-path
       :total-lines total-lines
       :executable-lines executable-count
       :covered-lines covered-count
       :coverage coverage-pct
       :uncovered-fns @uncovered-fns})))

;; -------------------------------------------------------------
;; 4. テスト実行と結果記録
;; -------------------------------------------------------------
(def test-details (atom []))
(def current-test (atom nil))

(defn custom-report [m]
  (case (:type m)
    :begin-test-var
    (let [v (:var m)]
      (reset! current-test {:name (str (ns-name (:ns (meta v))))
                            :var-name (str (:name (meta v)))
                            :started-at (DateTimeOffset/UtcNow)
                            :status :pass
                            :messages []}))

    :end-test-var
    (when-let [ct @current-test]
      (let [ended-at (DateTimeOffset/UtcNow)
            duration (.TotalMilliseconds (.Subtract ended-at ^DateTimeOffset (:started-at ct)))]
        (swap! test-details conj (assoc ct :duration-ms duration)))
      (reset! current-test nil))

    :pass
    nil

    :fail
    (when @current-test
      (swap! current-test assoc :status :fail)
      (swap! current-test update :messages conj (str "FAIL: expected " (pr-str (:expected m)) " actual " (pr-str (:actual m)))))

    :error
    (when @current-test
      (swap! current-test assoc :status :error)
      (let [actual (:actual m)
            act-str (cond
                      (nil? actual) "nil"
                      (instance? System.Exception actual) (str (.GetType ^System.Exception actual) ": " (.Message ^System.Exception actual) "\n" (.StackTrace ^System.Exception actual))
                      :else (try (pr-str actual) (catch System.Exception e (.Message e))))]
        (swap! current-test update :messages conj (str "ERROR: " (:message m) " " act-str))))

    nil))

;; -------------------------------------------------------------
;; 5. HTML レポート生成
;; -------------------------------------------------------------
(defn generate-test-results-html [summary details output-path]
  (let [jst-now (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0))
        date-str (.ToString jst-now "yyyy-MM-dd HH:mm:ss JST")
        total-suites (:suites summary)
        total-cases (count details)
        pass-count (:pass summary)
        fail-count (:fail summary)
        error-count (:error summary)
        total-duration (reduce + (map #(or (:duration-ms %) 0.0) details))
        is-all-ok (and (= fail-count 0) (= error-count 0))
        rows (for [d details]
               (let [status-badge (case (:status d)
                                    :pass "<span style=\"color:#10b981;font-weight:bold;\">✔ PASS</span>"
                                    :fail "<span style=\"color:#f43f5e;font-weight:bold;\">❌ FAIL</span>"
                                    :error "<span style=\"color:#f59e0b;font-weight:bold;\">⚠ ERROR</span>")
                      dur (str (.ToString (double (:duration-ms d)) "N1") " ms")
                      msg (if (seq (:messages d))
                            (str "<br><pre style=\"font-size:11px;color:#f43f5e;margin-top:6px;white-space:pre-wrap;\">" (str/join "\n" (:messages d)) "</pre>")
                            "")]
                 (str "<tr>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;font-family:monospace;\">" (:name d) "</td>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;font-family:monospace;font-weight:bold;\">" (:var-name d) msg "</td>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;text-align:center;\">" status-badge "</td>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;text-align:right;font-family:monospace;\">" dur "</td>
                      </tr>")))
        html (str "<!DOCTYPE html>
<html lang=\"ja\">
<head>
  <meta charset=\"utf-8\">
  <title>Test Results - FlightTrackerAI</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
    .container { max-width: 1040px; margin: 0 auto; }
    .header { border-bottom: 2px solid #334155; padding-bottom: 16px; margin-bottom: 24px; display: flex; justify-content: space-between; align-items: center; }
    .summary-cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 24px; }
    .card { background-color: #1e293b; border-radius: 8px; padding: 16px; text-align: center; border: 1px solid #334155; }
    .card-title { font-size: 11px; color: #94a3b8; text-transform: uppercase; margin-bottom: 6px; letter-spacing: 0.5px; }
    .card-value { font-size: 26px; font-weight: bold; }
    table { width: 100%; border-collapse: collapse; background-color: #1e293b; border-radius: 8px; overflow: hidden; border: 1px solid #334155; }
    th { background-color: #334155; padding: 12px 10px; text-align: left; font-size: 12px; color: #cbd5e1; }
  </style>
</head>
<body>
  <div class=\"container\">
    <div class=\"header\">
      <div>
        <h1 style=\"margin:0;font-size:24px;\">FlightTrackerAI Test Execution Report</h1>
        <p style=\"margin:6px 0 0 0;font-size:12px;color:#94a3b8;\">実行日時: " date-str " • 100% ClojureCLR (.NET 10) • 所要時間: " (.ToString (double total-duration) "N0") " ms • <a href=\"CoverageReport.html\" style=\"color:#38bdf8;text-decoration:none;font-weight:bold;\">📊 カバレッジレポートを表示 ➔</a></p>
      </div>
      <div>
        <span style=\"font-size:16px;font-weight:bold;padding:8px 16px;border-radius:8px;" (if is-all-ok "background:#064e3b;color:#34d399;border:1px solid #059669;" "background:#4c0519;color:#fb7185;border:1px solid #e11d48;") "\">"
          (if is-all-ok "ALL TESTS PASSED ✔" "TESTS FAILED ❌") "
        </span>
      </div>
    </div>
    <div class=\"summary-cards\">
      <div class=\"card\"><div class=\"card-title\">Test Suites</div><div class=\"card-value\" style=\"color:#38bdf8;\">" total-suites "</div></div>
      <div class=\"card\"><div class=\"card-title\">Test Cases</div><div class=\"card-value\" style=\"color:#a78bfa;\">" total-cases "</div></div>
      <div class=\"card\"><div class=\"card-title\">Passed Assertions</div><div class=\"card-value\" style=\"color:#34d399;\">" pass-count "</div></div>
      <div class=\"card\"><div class=\"card-title\">Failures</div><div class=\"card-value\" style=\"color:#f43f5e;\">" fail-count "</div></div>
      <div class=\"card\"><div class=\"card-title\">Errors</div><div class=\"card-value\" style=\"color:#fbbf24;\">" error-count "</div></div>
    </div>
    <table>
      <thead>
        <tr>
          <th>Namespace</th>
          <th>Test Case</th>
          <th style=\"text-align:center;\">Status</th>
          <th style=\"text-align:right;\">Duration</th>
        </tr>
      </thead>
      <tbody>
        " (str/join "" rows) "
      </tbody>
    </table>
  </div>
</body>
</html>")]
    (let [dir (Path/GetDirectoryName (Path/GetFullPath output-path))]
      (when-not (Directory/Exists dir)
        (Directory/CreateDirectory dir)))
    (File/WriteAllText output-path html Encoding/UTF8)
    (println (str "Test Results HTML written to: " output-path))))

(defn generate-coverage-html [coverage-modules output-path]
  (let [jst-now (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0))
        date-str (.ToString jst-now "yyyy-MM-dd HH:mm:ss JST")
        total-executable (reduce + (map :executable-lines coverage-modules))
        total-covered (reduce + (map :covered-lines coverage-modules))
        overall-coverage (if (pos? total-executable)
                           (Math/Round (* (/ (double total-covered) (double total-executable)) 100.0) 1)
                           100.0)
        overall-coverage-str (.ToString overall-coverage "F1")
        is-target-met (>= overall-coverage 80.0)
        rows (for [m coverage-modules]
               (let [cov (:coverage m)
                     cov-color (cond
                                 (>= cov 90.0) "#34d399"
                                 (>= cov 80.0) "#38bdf8"
                                 (>= cov 60.0) "#fbbf24"
                                 :else "#f43f5e")
                     uncovered (if (seq (:uncovered-fns m))
                                 (str "<div style=\"font-size:11px;color:#94a3b8;margin-top:4px;\">未実行関数: <span style=\"color:#fb7185;\">"
                                      (str/join ", " (:uncovered-fns m)) "</span></div>")
                                 "")]
                 (str "<tr>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;font-family:monospace;\">" (:name m) uncovered "</td>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;text-align:right;\">" (:total-lines m) "</td>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;text-align:right;\">" (:executable-lines m) "</td>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;text-align:right;\">" (:covered-lines m) "</td>
                        <td style=\"padding:10px;border-bottom:1px solid #334155;text-align:right;font-weight:bold;color:" cov-color ";\">" (.ToString (double cov) "F1") "%</td>
                      </tr>")))
        html (str "<!DOCTYPE html>
<html lang=\"ja\">
<head>
  <meta charset=\"utf-8\">
  <title>Coverage Report - FlightTrackerAI</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
    .container { max-width: 1040px; margin: 0 auto; }
    .header { border-bottom: 2px solid #334155; padding-bottom: 16px; margin-bottom: 24px; display: flex; justify-content: space-between; align-items: center; }
    .badge { font-size: 18px; font-weight: bold; padding: 8px 18px; border-radius: 8px; }
    table { width: 100%; border-collapse: collapse; background-color: #1e293b; border-radius: 8px; overflow: hidden; border: 1px solid #334155; }
    th { background-color: #334155; padding: 12px 10px; text-align: left; font-size: 12px; color: #cbd5e1; }
  </style>
</head>
<body>
  <div class=\"container\">
    <div class=\"header\">
      <div>
        <h1 style=\"margin:0;font-size:24px;\">FlightTrackerAI Code Coverage Report</h1>
        <p style=\"margin:6px 0 0 0;font-size:12px;color:#94a3b8;\">計測方式: ランタイム Var インストルメンテーション • 計測日時: " date-str " • 目標: 80% 以上 • <a href=\"TestResults.html\" style=\"color:#38bdf8;text-decoration:none;font-weight:bold;\">✔ テスト合否一覧を表示 ➔</a></p>
      </div>
      <div>
        <div class=\"badge\" style=\"" (if is-target-met "background:#064e3b;color:#34d399;border:1px solid #059669;" "background:#4c0519;color:#fb7185;border:1px solid #e11d48;") "\">
          Overall: " overall-coverage-str "% " (if is-target-met "✔ (PASS)" "❌ (FAIL)") "
        </div>
      </div>
    </div>
    <table>
      <thead>
        <tr>
          <th>Module (ClojureCLR)</th>
          <th style=\"text-align:right;\">Total Lines</th>
          <th style=\"text-align:right;\">Executable Lines</th>
          <th style=\"text-align:right;\">Covered Lines</th>
          <th style=\"text-align:right;\">Coverage</th>
        </tr>
      </thead>
      <tbody>
        " (str/join "" rows) "
      </tbody>
    </table>
  </div>
</body>
</html>")]
    (let [dir (Path/GetDirectoryName (Path/GetFullPath output-path))]
      (when-not (Directory/Exists dir)
        (Directory/CreateDirectory dir)))
    (File/WriteAllText output-path html Encoding/UTF8)
    (println (str "Coverage Report written to: " output-path))))

;; -------------------------------------------------------------
;; 6. 全テスト実行パイプライン
;; -------------------------------------------------------------
(defn run-all-tests []
  ;; 1. アセンブリのプリロード
  (preload-assemblies!)

  ;; 2. テスト名前空間およびソースモジュールの動的探索
  (let [test-namespaces (find-test-namespaces)
        source-modules (find-source-modules)]
    (println (str "動的検出されたテスト名前空間数: " (count test-namespaces)))
    (println (str "動的検出されたソースモジュール数: " (count source-modules)))

    ;; 3. ソースモジュールへの Var インストルメンテーション適用
    (instrument-source-modules! source-modules)

    ;; 4. テストスイートの実行
    (doseq [ns-sym test-namespaces]
      (require ns-sym))

    (let [summary (atom {:suites (count test-namespaces) :pass 0 :fail 0 :error 0})
          old-report t/report
          jst-now (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0))
          run-timestamp (.ToString jst-now "yyyyMMdd-HHmmss")
          base-results-dir (Path/Combine (into-array String ["doc" "work" "TestResults"]))
          run-dir (Path/Combine (into-array String [base-results-dir run-timestamp]))
          latest-dir (Path/Combine (into-array String [base-results-dir "latest"]))
          run-results-path (Path/Combine (into-array String [run-dir "TestResults.html"]))
          run-coverage-path (Path/Combine (into-array String [run-dir "CoverageReport.html"]))
          latest-results-path (Path/Combine (into-array String [latest-dir "TestResults.html"]))
          latest-coverage-path (Path/Combine (into-array String [latest-dir "CoverageReport.html"]))]
      (binding [t/report (fn [m]
                           (custom-report m)
                           (case (:type m)
                             :pass (swap! summary update :pass inc)
                             :fail (do (swap! summary update :fail inc) (old-report m))
                             :error (do (swap! summary update :error inc) (old-report m))
                             (old-report m)))]
        (doseq [ns-sym test-namespaces]
          (t/test-ns (the-ns ns-sym))))

      (println "\n=======================================================")
      (println "TOTAL TEST EXECUTION SUMMARY:")
      (println (assoc @summary :cases (count @test-details)))
      (println "=======================================================\n")

      ;; 5. 実測コードカバレッジの算出
      (let [coverage-modules (mapv calculate-module-coverage source-modules)]
        ;; 6. レポート出力 (履歴および最新)
        (generate-test-results-html @summary @test-details run-results-path)
        (generate-coverage-html coverage-modules run-coverage-path)
        (generate-test-results-html @summary @test-details latest-results-path)
        (generate-coverage-html coverage-modules latest-coverage-path)

        (println (str "\n✔ テスト結果出力先: " run-dir))
        (println (str "✔ 最新結果リンク: " latest-dir))

        (if (or (> (:fail @summary) 0) (> (:error @summary) 0))
          (System.Environment/Exit 1)
          (System.Environment/Exit 0))))))

;; ランナー実行
(run-all-tests)
