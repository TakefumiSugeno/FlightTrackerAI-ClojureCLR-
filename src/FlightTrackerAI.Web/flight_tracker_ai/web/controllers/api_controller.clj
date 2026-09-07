(ns flight-tracker-ai.web.controllers.api-controller
  (:require [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.settings-repository :as settings-repo]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.flight-repository :as flight-repo]
            [flight-tracker-ai.infrastructure.ai-client :as ai-client]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.web.views.modals :as modals]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly Uri SystemException]
           [System.Net.Http HttpClient]))

(defn- parse-query-string [^String raw-query]
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

      ;; 2. GET /api/tasks/:id/edit-modal
      (and (= method "GET") (.EndsWith path "/edit-modal"))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (modals/render-task-modal t {})}
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 3. GET /api/tasks/:id/quick-note-modal
      (and (= method "GET") (.EndsWith path "/quick-note-modal"))
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
      (and (or (= method "PATCH") (= method "POST")) (.EndsWith path "/notes"))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [updated-t (assoc t :user-notes (or (:notes form) "") :updated-at (DateTimeOffset/UtcNow))]
            (task-repo/update-task connection-string updated-t)
            (let [all-tasks (task-repo/get-all-tasks connection-string)]
              {:status 200
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks))}))
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 5. POST /api/tasks (Create Task)
      (and (= method "POST") (= path "/api/tasks"))
      (let [origin-res (domain/create-iata-code (:origin form))
            dest-res (domain/create-iata-code (:destination form))]
        (if (or (:error origin-res) (:error dest-res))
          {:status 400
           :content-type "text/plain; charset=utf-8"
           :body (or (:error origin-res) (:error dest-res))}
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
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks))}))))

      ;; 6. POST /api/tasks/:id (Update Task)
      (and (= method "POST")
           (not (.Contains path "/run"))
           (not (.Contains path "/retry"))
           (not (.Contains path "/notes"))
           (not (.Contains path "/new-modal"))
           (not (.Contains path "/edit-modal"))
           (.StartsWith path "/api/tasks/"))
      (let [id-str (subs path (count "/api/tasks/"))
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [origin-res (if (:origin form) (domain/create-iata-code (:origin form)) {:ok (:origin t)})
                dest-res (if (:destination form) (domain/create-iata-code (:destination form)) {:ok (:destination t)})
                target-price (if-not (str/blank? (:targetPriceJpy form))
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
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks))}))
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 7. DELETE /api/tasks/:id
      (and (= method "DELETE") (.StartsWith path "/api/tasks/"))
      (let [id-str (subs path (count "/api/tasks/"))
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (when task-id
          (task-repo/delete-task connection-string task-id))
        (let [all-tasks (task-repo/get-all-tasks connection-string)]
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (h/render-html (dash/render-dashboard-content all-tasks))}))

      ;; 8. POST /api/tasks/:id/run (Immediate Scrape with 409 Conflict Check)
      (and (= method "POST") (or (.Contains path "/run") (.Contains path "/retry")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-not (.Wait scraper-common/scraper-lock 0)
          {:status 409
           :content-type "text/plain; charset=utf-8"
           :body "他のタスクが巡回中です。完了までお待ちください。"}
          (do
            (.Release scraper-common/scraper-lock)
            (when-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
              (let [active-t (assoc t :status :active)
                    _ (task-repo/update-task connection-string active-t)
                    settings (settings-repo/get-settings connection-string)
                    client (HttpClient.)]
                (worker/execute-task-scraping client connection-string active-t settings)))
            (let [all-tasks (task-repo/get-all-tasks connection-string)]
              {:status 200
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (dash/render-dashboard-content all-tasks))}))))

      ;; 9. GET /api/tasks/:id/detail (Detail & Timeline Modal)
      (and (= method "GET") (.EndsWith path "/detail"))
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

      ;; 10. GET /api/tasks/:id/history (History JSON)
      (and (= method "GET") (.EndsWith path "/history"))
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

      ;; 11. GET /api/settings/modal
      (and (= method "GET") (= path "/api/settings/modal"))
      (let [settings (settings-repo/get-settings connection-string)]
        {:status 200
         :content-type "text/html; charset=utf-8"
         :body (modals/render-settings-modal settings)})

      ;; 12. POST /api/settings
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
         :content-type "text/html; charset=utf-8"
         :body "<script>closeCurrentModal(); showToast('システム設定を保存しました', true);</script>"})

      ;; Default: 404
      :else
      {:status 404
       :content-type "text/plain; charset=utf-8"
       :body "Endpoint not found"})))


