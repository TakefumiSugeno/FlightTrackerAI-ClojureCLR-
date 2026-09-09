(ns flight-tracker-ai.web.controllers.api-controller
  (:require [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.settings-repository :as settings-repo]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.flight-repository :as flight-repo]
            [flight-tracker-ai.infrastructure.ai-client :as ai-client]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.infrastructure.app-logger :as logger]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.web.views.modals :as modals]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly Uri SystemException]
           [System.Net.Http HttpClient]))

(defn parse-query-string [^String raw-query]
  (if (or (nil? raw-query) (str/blank? raw-query))
    {}
    (let [q (if (.StartsWith raw-query "?") (subs raw-query 1) raw-query)]
      (into {}
            (for [part (str/split q #"&")
                  :when (not (str/blank? part))]
              (let [idx (.IndexOf part "=")]
                (if (>= idx 0)
                  [(keyword (.Substring part 0 idx))
                   (Uri/UnescapeDataString (.Substring part (inc idx)))]
                  [(keyword part) ""])))))))

(defn- parse-form-data [^String body-str]
  (parse-query-string body-str))

(defn- extract-prompt [^String body-str form]
  (if-let [p (:prompt form)]
    p
    (when-let [m (re-find #"(?i)\"prompt\"\s*:\s*\"((?:\\\"|[^\"])*)\"" (or body-str ""))]
      (.. (second m) (Replace "\\\"" "\"") (Replace "\\n" "\n")))))

(defn handle-api-request [^String connection-string ^String method ^String raw-url ^String body-str]
  (let [uri (Uri. (str "http://localhost" raw-url))
        path (.AbsolutePath uri)
        query (parse-query-string (.Query uri))
        form (when body-str (parse-form-data body-str))]
    (cond
      ;; 1. GET /api/tasks/new-modal
      (and (= method "GET") (= path "/api/tasks/new-modal"))
      (let [prompt (:prompt query)]
        (if (and prompt (not (str/blank? prompt)))
          ;; AI 解析付き登録モーダル
          (let [settings (settings-repo/get-settings connection-string)
                client (HttpClient.)
                ai-res (ai-client/parse-flight-query client (:openrouter-api-key settings) prompt (DateOnly/FromDateTime System.DateTime/UtcNow))
                params (if (:ok ai-res)
                         {:origin (:Origin (:ok ai-res))
                          :destination (:Destination (:ok ai-res))
                          :tripType (:TripType (:ok ai-res))
                          :outboundDate (:OutboundDate (:ok ai-res))
                          :inboundDate (:InboundDate (:ok ai-res))
                          :targetPriceJpy (:MaxPriceJpy (:ok ai-res))
                          :notes (:Notes (:ok ai-res))}
                         {:notes prompt})]
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body (modals/render-task-modal nil params)})
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (modals/render-task-modal nil {})}))

      ;; 2. GET /api/tasks/:id/edit-modal or /api/tasks/:id/modal
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (or (.EndsWith path "/edit-modal") (.EndsWith path "/modal")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (modals/render-task-modal t {})}
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 3. GET /api/tasks/:id/quick-note-modal or /api/tasks/:id/notes-modal
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (or (.EndsWith path "/quick-note-modal") (.EndsWith path "/notes-modal")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [origin-str (domain/iata-code-value (:origin t))
                dest-str (domain/iata-code-value (:destination t))]
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body (modals/render-quick-note-modal task-id (:user-notes t) (str origin-str " ➔ " dest-str))})
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 4. PATCH /api/tasks/:id/notes (or POST)
      (and (or (= method "PATCH") (= method "POST"))
           (.StartsWith path "/api/tasks/")
           (.EndsWith path "/notes"))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [updated-t (assoc t :user-notes (or (:notes form) "") :updated-at (DateTimeOffset/UtcNow))]
            (task-repo/update-task connection-string updated-t)
            (let [all-tasks (task-repo/get-all-tasks connection-string)]
              {:status 200
               :headers {"HX-Trigger" "closeModal"}
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks "showToast('ユーザーメモを更新しました！', true);"))}))
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 5. POST /api/tasks/:id/toggle-status
      (and (= method "POST")
           (.StartsWith path "/api/tasks/")
           (.EndsWith path "/toggle-status"))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [new-status (if (= (:status t) :paused) :active :paused)
                updated-t (assoc t :status new-status :updated-at (DateTimeOffset/UtcNow))]
            (task-repo/update-task connection-string updated-t)
            (let [all-tasks (task-repo/get-all-tasks connection-string)
                  msg (if (= new-status :paused) "タスクの巡回を一時停止しました。" "タスクの巡回を再開しました！")]
              {:status 200
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks (str "showToast('" msg "', true);")))}))
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 6. GET /api/tasks/view (HTML fragment for HTMX)
      (and (= method "GET") (= path "/api/tasks/view"))
      (let [all-tasks (task-repo/get-all-tasks connection-string)]
        {:status 200
         :content-type "text/html; charset=utf-8"
         :body (h/render-html (dash/render-dashboard-content all-tasks))})

      ;; 7. POST /api/tasks (Create Task via Modal)
      (and (= method "POST") (= path "/api/tasks"))
      (let [origin-res (domain/create-iata-code (:origin form))
            dest-res (domain/create-iata-code (:destination form))]
        (if (or (:error origin-res) (:error dest-res))
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (modals/render-task-modal nil form (or (:error origin-res) (:error dest-res)))}
          (let [is-round (and (= (:tripType form) "RoundTrip") (not (str/blank? (:inboundDate form))))
                outbound (try (DateOnly/Parse (:outboundDate form)) (catch Exception _ (DateOnly/FromDateTime System.DateTime/UtcNow)))
                inbound (when is-round (try (DateOnly/Parse (:inboundDate form)) (catch Exception _ nil)))
                trip (if is-round {:kind :round-trip :outbound outbound :inbound inbound} {:kind :one-way :outbound outbound})
                target-price (when-not (str/blank? (:targetPriceJpy form))
                               (try (long (read-string (:targetPriceJpy form))) (catch Exception _ nil)))
                interval (try (long (read-string (or (:checkIntervalHours form) "12"))) (catch Exception _ 12))
                now (DateTimeOffset/UtcNow)
                task-item {:id (Guid/NewGuid)
                           :title (or (:title form) "新規タスク")
                           :origin (:ok origin-res)
                           :destination (:ok dest-res)
                           :trip-type trip
                           :max-stops :any-stops
                           :preferred-airlines []
                           :target-price-jpy target-price
                           :check-interval-hours interval
                           :notification-webhook-url (when-not (str/blank? (:webhookUrl form)) (:webhookUrl form))
                           :user-notes (when-not (str/blank? (:userNotes form)) (:userNotes form))
                           :is-headless true
                           :status :active
                           :consecutive-failures 0
                           :created-at now
                           :updated-at now
                           :last-checked-at nil
                           :last-lowest-price-jpy nil
                           :last-lowest-airlines nil
                           :last-lowest-provider nil
                           :ai-analysis-summary nil}]
            (task-repo/create-task connection-string task-item)
            (let [all-tasks (task-repo/get-all-tasks connection-string)]
              {:status 200
               :headers {"HX-Trigger" "closeModal"}
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks "showToast('新規タスクを登録しました！', true);"))}))))

      ;; 8. POST /api/tasks/standalone (Create Task via Standalone Page)
      (and (= method "POST") (= path "/api/tasks/standalone"))
      (let [origin-res (domain/create-iata-code (:origin form))
            dest-res (domain/create-iata-code (:destination form))]
        (if (or (:error origin-res) (:error dest-res))
          {:status 400
           :content-type "text/html; charset=utf-8"
           :body (str "<p class='text-rose-400'>エラー: " (or (:error origin-res) (:error dest-res)) "</p>")}
          (let [is-round (and (= (:tripType form) "RoundTrip") (not (str/blank? (:inboundDate form))))
                outbound (try (DateOnly/Parse (:outboundDate form)) (catch Exception _ (DateOnly/FromDateTime System.DateTime/UtcNow)))
                inbound (when is-round (try (DateOnly/Parse (:inboundDate form)) (catch Exception _ nil)))
                trip (if is-round {:kind :round-trip :outbound outbound :inbound inbound} {:kind :one-way :outbound outbound})
                target-price (when-not (str/blank? (:targetPriceJpy form))
                               (try (long (read-string (:targetPriceJpy form))) (catch Exception _ nil)))
                interval (try (long (read-string (or (:checkIntervalHours form) "12"))) (catch Exception _ 12))
                now (DateTimeOffset/UtcNow)
                task-item {:id (Guid/NewGuid)
                           :title (or (:title form) "新規タスク")
                           :origin (:ok origin-res)
                           :destination (:ok dest-res)
                           :trip-type trip
                           :max-stops (cond
                                        (= (:maxStops form) "DirectOnly") :direct-only
                                        (= (:maxStops form) "OneStop") :one-stop
                                        :else :any-stops)
                           :preferred-airlines []
                           :target-price-jpy target-price
                           :check-interval-hours interval
                           :notification-webhook-url (when-not (str/blank? (:webhookUrl form)) (:webhookUrl form))
                           :user-notes (when-not (str/blank? (:userNotes form)) (:userNotes form))
                           :is-headless (not= (:showBrowser form) "true")
                           :status :active
                           :consecutive-failures 0
                           :created-at now
                           :updated-at now
                           :last-checked-at nil
                           :last-lowest-price-jpy nil
                           :last-lowest-airlines nil
                           :last-lowest-provider nil
                           :ai-analysis-summary nil}]
            (task-repo/create-task connection-string task-item)
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body "<script>window.location.href='/';</script>"})))

      ;; 9. POST /api/tasks/:id (Update Task)
      (and (= method "POST")
           (not (.Contains path "/run"))
           (not (.Contains path "/retry"))
           (not (.Contains path "/notes"))
           (not (.Contains path "/new-modal"))
           (not (.Contains path "/edit-modal"))
           (not (.Contains path "/modal"))
           (not (.Contains path "/toggle-status"))
           (not (.Contains path "/standalone"))
           (not (= path "/api/tasks"))
           (not (= path "/api/settings"))
           (not (= path "/api/ai/parse"))
           (.StartsWith path "/api/tasks/"))
      (let [id-str (subs path (count "/api/tasks/"))
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [origin-res (if (:origin form) (domain/create-iata-code (:origin form)) {:ok (:origin t)})
                dest-res (if (:destination form) (domain/create-iata-code (:destination form)) {:ok (:destination t)})]
            (if (or (:error origin-res) (:error dest-res))
              {:status 200
               :content-type "text/html; charset=utf-8"
               :body (modals/render-task-modal t form (or (:error origin-res) (:error dest-res)))}
              (let [target-price (if-not (str/blank? (:targetPriceJpy form))
                                   (try (long (read-string (:targetPriceJpy form))) (catch Exception _ (:target-price-jpy t)))
                                   (:target-price-jpy t))
                    interval (if-not (str/blank? (:checkIntervalHours form))
                               (try (long (read-string (:checkIntervalHours form))) (catch Exception _ (:check-interval-hours t)))
                               (:check-interval-hours t))
                    updated-t (assoc t
                                     :title (or (:title form) (:title t))
                                     :origin (:ok origin-res)
                                     :destination (:ok dest-res)
                                     :target-price-jpy target-price
                                     :check-interval-hours interval
                                     :notification-webhook-url (if-not (str/blank? (:webhookUrl form)) (:webhookUrl form) (:notification-webhook-url t))
                                     :user-notes (if-not (nil? (:userNotes form)) (:userNotes form) (:user-notes t))
                                     :updated-at (DateTimeOffset/UtcNow))]
                (task-repo/update-task connection-string updated-t)
                (let [all-tasks (task-repo/get-all-tasks connection-string)]
                  {:status 200
                   :headers {"HX-Trigger" "closeModal"}
                   :content-type "text/html; charset=utf-8"
                   :body (h/render-html (dash/render-dashboard-content all-tasks "showToast('タスク設定を更新しました！', true);"))}))))
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 10. DELETE /api/tasks/:id
      (and (= method "DELETE") (.StartsWith path "/api/tasks/"))
      (let [id-str (subs path (count "/api/tasks/"))
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (when task-id
          (task-repo/delete-task connection-string task-id))
        (let [all-tasks (task-repo/get-all-tasks connection-string)]
          {:status 200
           :headers {"HX-Trigger" "closeModal"}
           :content-type "text/html; charset=utf-8"
           :body (h/render-html (dash/render-dashboard-content all-tasks "showToast('タスクを削除しました。', true);"))}))

      ;; 11. POST /api/tasks/:id/run (Immediate Scrape with 409 Conflict Check & headless param)
      (and (= method "POST")
           (.StartsWith path "/api/tasks/")
           (or (.Contains path "/run") (.Contains path "/retry")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))
            is-headless (not= (:headless query) "false")]
        (if-not (.Wait scraper-common/scraper-lock 0)
          {:status 409
           :content-type "text/plain; charset=utf-8"
           :body "他のタスクが巡回中です。完了までお待ちください。"}
          (do
            (.Release scraper-common/scraper-lock)
            (when-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
              (let [active-t (assoc t :status :active)
                    _ (task-repo/update-task connection-string active-t)
                    base-settings (settings-repo/get-settings connection-string)
                    settings (if-not is-headless (assoc base-settings :headless-mode false) base-settings)
                    client (HttpClient.)]
                (worker/execute-task-scraping client connection-string active-t settings)))
            (let [all-tasks (task-repo/get-all-tasks connection-string)]
              {:status 200
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks "closeCurrentModal(); showToast('巡回が完了しました。', true);"))}))))

      ;; 12. GET /api/tasks/:id/detail or /api/tasks/:id/detail-modal
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (or (.EndsWith path "/detail") (.EndsWith path "/detail-modal")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [latest (flight-repo/get-latest-offers-for-task connection-string task-id 10)
                history (flight-repo/get-price-history connection-string task-id)]
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body (modals/render-timeline-modal t latest history)})
          {:status 404
           :content-type "text/plain; charset=utf-8"
           :body "Task not found"}))

      ;; 13. GET /api/tasks/:id/history (History JSON)
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (.EndsWith path "/history"))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if task-id
          (let [history (flight-repo/get-price-history connection-string task-id)]
            {:status 200
             :content-type "application/json; charset=utf-8"
             :body (dto/to-json (or history []))})
          {:status 404
           :content-type "text/plain; charset=utf-8"
           :body "Task not found"}))

      ;; 14. GET /api/settings/modal
      (and (= method "GET") (= path "/api/settings/modal"))
      (let [settings (settings-repo/get-settings connection-string)]
        {:status 200
         :content-type "text/html; charset=utf-8"
         :body (modals/render-settings-modal settings)})

      ;; 15. POST /api/settings
      (and (= method "POST") (= path "/api/settings"))
      (let [interval (try (long (read-string (or (:defaultCheckIntervalHours form) "12"))) (catch Exception _ 12))
            webhook-url (when-not (str/blank? (:defaultWebhookUrl form)) (:defaultWebhookUrl form))
            api-key (when-not (str/blank? (:openRouterApiKey form)) (:openRouterApiKey form))
            enable-gf (= (:enableGoogleFlights form) "1")
            enable-ss (= (:enableSkyscanner form) "1")
            headless (= (:headlessMode form) "1")
            settings {:default-check-interval-hours interval
                      :default-webhook-url webhook-url
                      :openrouter-api-key api-key
                      :enable-google-flights enable-gf
                      :enable-skyscanner enable-ss
                      :headless-mode headless}]
        (settings-repo/update-settings connection-string settings)
        {:status 200
         :headers {"HX-Trigger" "closeModal"}
         :content-type "text/html; charset=utf-8"
         :body "<script>showToast('システム設定を保存しました', true);</script>"})

      ;; 16. GET /api/logs/modal
      (and (= method "GET") (= path "/api/logs/modal"))
      (let [logs (logger/get-recent-logs 150)
            log-path (logger/get-log-file-path)]
        {:status 200
         :content-type "text/html; charset=utf-8"
         :body (modals/render-logs-modal logs log-path)})

      ;; 17. GET /api/logs/text
      (and (= method "GET") (= path "/api/logs/text"))
      (let [logs (logger/get-recent-logs 150)]
        {:status 200
         :content-type "text/plain; charset=utf-8"
         :body (str/join "\n" logs)})

      ;; 18. POST /api/ai/parse
      (and (= method "POST") (= path "/api/ai/parse"))
      (let [prompt (extract-prompt body-str form)]
        (if (str/blank? prompt)
          {:status 400
           :content-type "application/json; charset=utf-8"
           :body "{\"error\":\"Prompt is required\"}"}
          (let [settings (settings-repo/get-settings connection-string)
                client (HttpClient.)
                ai-res (ai-client/parse-flight-query client (:openrouter-api-key settings) prompt (DateOnly/FromDateTime System.DateTime/UtcNow))]
            (if (:ok ai-res)
              {:status 200
               :content-type "application/json; charset=utf-8"
               :body (dto/to-json (:ok ai-res))}
              {:status 200
               :content-type "application/json; charset=utf-8"
               :body (dto/to-json {:Origin "" :Destination "" :TripType "RoundTrip" :OutboundDate "" :InboundDate "" :MaxStops "Any" :MaxPriceJpy nil :Notes prompt})}))))

      ;; Default: 404
      :else
      {:status 404
       :content-type "text/plain; charset=utf-8"
       :body "Endpoint not found"})))


