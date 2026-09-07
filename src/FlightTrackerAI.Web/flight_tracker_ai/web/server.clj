(System.Reflection.Assembly/Load "System.Net.HttpListener")

(ns flight-tracker-ai.web.server
  (:require [flight-tracker-ai.web.controllers.api-controller :as api]
            [flight-tracker-ai.web.views.layout :as layout]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.infrastructure.app-logger :as logger]
            [clojure.string :as str])
  (:import [System.Net HttpListener HttpListenerContext]
           [System.IO StreamReader]
           [System.Text Encoding]
           [System.Threading Thread ThreadPool WaitCallback ThreadStart]))

(defn- read-body [^System.IO.Stream stream]
  (if (or (nil? stream) (not (.CanRead stream)))
    ""
    (with-open [reader (StreamReader. stream Encoding/UTF8)]
      (.ReadToEnd reader))))

(defn- write-response [^System.Net.HttpListenerResponse resp status content-type ^String body-str]
  (try
    (set! (.StatusCode resp) status)
    (set! (.ContentType resp) content-type)
    (let [bytes (.GetBytes Encoding/UTF8 (or body-str ""))
          output (.OutputStream resp)]
      (set! (.ContentLength64 resp) (long (count bytes)))
      (.Write output bytes 0 (count bytes))
      (.Close output))
    (catch Exception _ nil)))

(defn handle-request [^String connection-string ^HttpListenerContext ctx]
  (let [req (.Request ctx)
        resp (.Response ctx)
        method (.HttpMethod req)
        raw-url (.RawUrl req)
        body-str (read-body (.InputStream req))]
    (try
      (cond
        ;; Dashboard HTML
        (and (= method "GET") (or (= raw-url "/") (= raw-url "/index.html")))
        (let [tasks (task-repo/get-all-tasks connection-string)
              content (dash/render-dashboard-content tasks)
              full-html (layout/base-layout "ダッシュボード" content)]
          (write-response resp 200 "text/html; charset=utf-8" full-html))

        ;; API endpoints
        (.StartsWith raw-url "/api/")
        (let [res (api/handle-api-request connection-string method raw-url body-str)]
          (write-response resp (:status res) (:content-type res) (:body res)))

        ;; Static or 404
        :else
        (write-response resp 404 "text/plain; charset=utf-8" "Not Found"))
      (catch Exception ex
        (logger/error-ex "Server" "リクエストハンドリング例外" ex)
        (write-response resp 500 "text/plain; charset=utf-8" (str "Internal Server Error: " (.Message ex)))))))

(defn start-server [^String connection-string ^String port]
  (let [listener (HttpListener.)
        prefix (str "http://localhost:" port "/")]
    (.Add (.Prefixes listener) prefix)
    (.Start listener)
    (logger/info "Server" (str "FlightTrackerAI サーバーが起動しました: " prefix))
    (let [t (Thread.
              (gen-delegate System.Threading.ThreadStart []
                (while (.IsListening listener)
                  (try
                    (let [ctx (.GetContext listener)]
                      (ThreadPool/QueueUserWorkItem
                        (gen-delegate WaitCallback [state]
                          (handle-request connection-string state))
                        ctx))
                    (catch Exception _ nil)))))]
      (set! (.IsBackground t) true)
      (.Start t)
      listener)))

(defn -main [& args]
  (let [port (or (first args) "5000")
        conn-str "Data Source=flight_tracker.db"]
    (db/initialize-database conn-str)
    (worker/start-worker! conn-str)
    (let [listener (start-server conn-str port)]
      (println (str "Server running on http://localhost:" port "/ (Press Enter to stop)"))
      (read-line)
      (.Stop listener)
      (worker/stop-worker!))))


