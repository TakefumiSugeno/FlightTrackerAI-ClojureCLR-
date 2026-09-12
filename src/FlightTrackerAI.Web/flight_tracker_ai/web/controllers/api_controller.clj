(ns flight-tracker-ai.web.controllers.api-controller
  (:require [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.settings-repository :as settings-repo]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.flight-repository :as flight-repo]
            [flight-tracker-ai.infrastructure.ai-client :as ai-client]
            [flight-tracker-ai.infrastructure.scraper-common :as scraper-common]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.infrastructure.app-logger :as logger]
            [flight-tracker-ai.web.views.layout :as layout]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.web.views.modals :as modals]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System Guid DateTime DateTimeOffset DateOnly Uri TimeSpan]
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

(defn extract-iata-code [^String s]
  (if (str/blank? s)
    ""
    (let [trimmed (str/trim s)]
      (cond
        (re-matches #"(?i)^[A-Za-z]{3}$" trimmed)
        (.ToUpperInvariant trimmed)

        (re-matches #"(?i)^([A-Za-z]{3})[\s\-–—/(].*" trimmed)
        (let [code (second (re-find #"(?i)^([A-Za-z]{3})" trimmed))]
          (.ToUpperInvariant code))

        :else trimmed))))

(defn- extract-prompt [^String body-str form]
  (if-let [p (:prompt form)]
    p
    (when-let [m (re-find #"(?i)\"prompt\"\s*:\s*\"((?:\\\"|[^\"])*)\"" (or body-str ""))]
      (.. (second m) (Replace "\\\"" "\"") (Replace "\\n" "\n")))))

(defn- render-with-modal-close [dashboard-node msg]
  [:div
   dashboard-node
   [:script (h/raw (str "closeCurrentModal(); showToast('" msg "', true);"))]])

(defn- get-jst-today []
  (DateOnly/FromDateTime (.DateTime (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0)))))

(defn- build-task-item-from-form [form origin-res dest-res]
  (let [is-round (and (= (:tripType form) "RoundTrip") (not (str/blank? (:inboundDate form))))
        outbound (try (DateOnly/Parse (:outboundDate form)) (catch Exception _ (get-jst-today)))
        inbound (when is-round (try (DateOnly/Parse (:inboundDate form)) (catch Exception _ nil)))
        trip (if is-round
               {:kind :round-trip
                :outbound outbound
                :outbound-date outbound
                :inbound inbound
                :inbound-date inbound}
               {:kind :one-way
                :outbound outbound
                :outbound-date outbound})
        target-price (when-not (str/blank? (:targetPriceJpy form))
                       (try (long (read-string (:targetPriceJpy form))) (catch Exception _ nil)))
        interval (try (long (read-string (or (:checkIntervalHours form) "12"))) (catch Exception _ 12))
        max-stops (case (:maxStops form)
                    "DirectOnly" :direct-only
                    "OneStop" :one-stop
                    :any-stops)
        now (DateTimeOffset/UtcNow)
        task-title (if-not (str/blank? (:title form))
                     (:title form)
                     (str (:ok origin-res) " ➔ " (:ok dest-res)))
        webhook-url (if (or (= (:useDefaultWebhook form) "true")
                            (= (:useDefaultWebhook form) "on"))
                      (when-not (str/blank? (:webhookUrl form)) (:webhookUrl form))
                      "DISABLED")]
    {:id (Guid/NewGuid)
     :title task-title
     :origin (:ok origin-res)
     :destination (:ok dest-res)
     :trip-type trip
     :max-stops max-stops
     :preferred-airlines []
     :target-price-jpy target-price
     :check-interval-hours interval
     :notification-webhook-url webhook-url
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
     :ai-analysis-summary nil}))

(defn handle-api-request [^String connection-string ^String method ^String raw-url ^String body-str]
  (let [uri (try (Uri. (str "http://localhost" raw-url)) (catch Exception _ (Uri. "http://localhost/")))
        path (.AbsolutePath uri)
        query (parse-query-string (.Query uri))
        form (when (and body-str (not (str/blank? body-str)))
               (parse-form-data body-str))]
    (cond
      ;; 1. GET /api/tasks または /api/tasks/view (ダッシュボード部分更新)
      (and (= method "GET") (or (= path "/api/tasks") (= path "/api/tasks/view")))
      (let [mode (or (:mode query) "card")
            status (or (:status query) "all")
            q-str (or (:query query) "")
            all-tasks (task-repo/get-all-tasks connection-string)]
        {:status 200
         :content-type "text/html; charset=utf-8"
         :body (h/render-html (dash/render-dashboard all-tasks mode status q-str))})

      ;; 2. GET /api/tasks/new-modal
      (and (= method "GET") (= path "/api/tasks/new-modal"))
      (let [settings (settings-repo/get-settings connection-string)
            interval-str (str (:default-check-interval-hours settings))
            prompt (:prompt query)]
        (if (and prompt (not (str/blank? prompt)))
          (let [client (HttpClient.)
                ai-res (ai-client/parse-flight-query client (:openrouter-api-key settings) prompt (get-jst-today))
                params (if (:ok ai-res)
                         (let [ai-data (:ok ai-res)
                               orig (:Origin ai-data)
                               dest (:Destination ai-data)
                               ttl (or (:Title ai-data)
                                       (when (and (not (str/blank? orig)) (not (str/blank? dest)))
                                         (str orig " ➔ " dest)))]
                           {:origin orig
                            :destination dest
                            :tripType (:TripType ai-data)
                            :outboundDate (:OutboundDate ai-data)
                            :inboundDate (:InboundDate ai-data)
                            :maxStops (:MaxStops ai-data)
                            :targetPriceJpy (:MaxPriceJpy ai-data)
                            :title ttl
                            :userNotes (:Notes ai-data)
                            :notes (:Notes ai-data)})
                         {:userNotes prompt :notes prompt})]
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body (h/render-html (modals/render-task-modal nil params nil interval-str))})
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (h/render-html (modals/render-task-modal nil {} nil interval-str))}))

      ;; 3. GET /api/tasks/:id/modal (編集モーダル)
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (or (.EndsWith path "/modal") (.EndsWith path "/edit-modal")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (h/render-html (modals/render-task-modal t {}))}
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 4. GET /api/tasks/:id/notes-modal (メモ編集モーダル)
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (or (.EndsWith path "/notes-modal") (.EndsWith path "/quick-note-modal")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          {:status 200
           :content-type "text/html; charset=utf-8"
           :body (h/render-html (modals/render-notes-modal t))}
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 5. POST or PATCH /api/tasks/:id/notes (メモ更新)
      (and (or (= method "POST") (= method "PATCH"))
           (.StartsWith path "/api/tasks/")
           (.EndsWith path "/notes"))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [notes-text (or (:userNotes form) (:notes form) "")
                updated-t (assoc t :user-notes notes-text :updated-at (DateTimeOffset/UtcNow))]
            (task-repo/update-task connection-string updated-t)
            (let [all-tasks (task-repo/get-all-tasks connection-string)
                  node (dash/render-dashboard all-tasks "card" "all" "")]
              {:status 200
               :headers {"HX-Trigger" "closeModal"}
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (render-with-modal-close node "メモを保存しました！"))}))
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 6. POST /api/tasks/:id/toggle-status (ステータストグル)
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
                  msg (if (= new-status :paused) "タスクの巡回を一時停止しました。" "タスクの巡回を再開しました！")
                  node (dash/render-dashboard all-tasks "card" "all" "")]
              {:status 200
               :headers {"HX-Trigger" "closeModal"}
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (render-with-modal-close node msg))}))

          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 7. POST /api/tasks (新規作成)
      (and (= method "POST") (= path "/api/tasks"))
      (let [clean-origin (extract-iata-code (:origin form))
            clean-dest (extract-iata-code (:destination form))
            origin-res (domain/create-iata-code clean-origin)
            dest-res (domain/create-iata-code clean-dest)]
        (if (or (:error origin-res) (:error dest-res))
          (let [settings (settings-repo/get-settings connection-string)
                interval-str (str (:default-check-interval-hours settings))]
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body (h/render-html (modals/render-task-modal nil form (or (:error origin-res) (:error dest-res)) interval-str))})
          (let [task-item (build-task-item-from-form form origin-res dest-res)]
            (task-repo/create-task connection-string task-item)
            (let [all-tasks (task-repo/get-all-tasks connection-string)
                  node (dash/render-dashboard all-tasks "card" "all" "")]
              {:status 200
               :headers {"HX-Trigger" "closeModal"}
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (render-with-modal-close node "新規タスクを登録しました！"))}))))

      ;; 8. POST /api/tasks/standalone (スタンドアロン登録画面用)
      (and (= method "POST") (= path "/api/tasks/standalone"))
      (let [clean-origin (extract-iata-code (:origin form))
            clean-dest (extract-iata-code (:destination form))
            origin-res (domain/create-iata-code clean-origin)
            dest-res (domain/create-iata-code clean-dest)]
        (if (or (:error origin-res) (:error dest-res))
          (let [settings (settings-repo/get-settings connection-string)
                interval-str (str (:default-check-interval-hours settings))]
            {:status 400
             :content-type "text/html; charset=utf-8"
             :body (layout/base-layout "新規タスク登録" (modals/render-standalone-new-task-page form (or (:error origin-res) (:error dest-res)) interval-str))})
          (let [task-item (build-task-item-from-form form origin-res dest-res)]
            (task-repo/create-task connection-string task-item)
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body "<script>window.location.href='/';</script>"})))

      ;; 9. POST /api/tasks/:id (タスク更新)
      (and (= method "POST")
           (not (.Contains path "/run"))
           (not (.Contains path "/retry"))
           (not (.Contains path "/notes"))
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
          (let [clean-origin (when (:origin form) (extract-iata-code (:origin form)))
                clean-dest (when (:destination form) (extract-iata-code (:destination form)))
                origin-res (if clean-origin (domain/create-iata-code clean-origin) {:ok (:origin t)})
                dest-res (if clean-dest (domain/create-iata-code clean-dest) {:ok (:destination t)})]
            (if (or (:error origin-res) (:error dest-res))
              {:status 200
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (modals/render-task-modal t form (or (:error origin-res) (:error dest-res))))}
              (let [target-price (if-not (str/blank? (:targetPriceJpy form))
                                   (try (long (read-string (:targetPriceJpy form))) (catch Exception _ (:target-price-jpy t)))
                                   (:target-price-jpy t))
                    interval (try (long (read-string (or (:checkIntervalHours form) "12"))) (catch Exception _ (:check-interval-hours t)))
                    updated-stops (if-let [ms (:maxStops form)]
                                    (case ms
                                      "DirectOnly" :direct-only
                                      "OneStop" :one-stop
                                      :any-stops)
                                    (:max-stops t))
                    is-full-form (or (some? (:origin form)) (some? (:title form)))
                    updated-webhook (if is-full-form
                                      (if (or (= (:useDefaultWebhook form) "true") (= (:useDefaultWebhook form) "on"))
                                        (when-not (str/blank? (:webhookUrl form)) (:webhookUrl form))
                                        "DISABLED")
                                      (cond
                                        (some? (:useDefaultWebhook form))
                                        (if (or (= (:useDefaultWebhook form) "true") (= (:useDefaultWebhook form) "on"))
                                          (when-not (str/blank? (:webhookUrl form)) (:webhookUrl form))
                                          "DISABLED")
                                        (not (str/blank? (:webhookUrl form)))
                                        (:webhookUrl form)
                                        :else
                                        (:notification-webhook-url t)))
                    updated-headless (if is-full-form
                                       (not (or (= (:showBrowser form) "true") (= (:showBrowser form) "on")))
                                       (if (some? (:showBrowser form))
                                         (not= (:showBrowser form) "true")
                                         (:is-headless t)))
                    updated-t (assoc t
                                     :title (or (:title form) (:title t))
                                     :origin (:ok origin-res)
                                     :destination (:ok dest-res)
                                     :max-stops updated-stops
                                     :target-price-jpy target-price
                                     :check-interval-hours interval
                                     :notification-webhook-url updated-webhook
                                     :user-notes (if-not (nil? (:userNotes form)) (:userNotes form) (:user-notes t))
                                     :is-headless updated-headless
                                     :updated-at (DateTimeOffset/UtcNow))]
                (task-repo/update-task connection-string updated-t)
                (let [all-tasks (task-repo/get-all-tasks connection-string)
                      node (dash/render-dashboard all-tasks "card" "all" "")]
                  {:status 200
                   :headers {"HX-Trigger" "closeModal"}
                   :content-type "text/html; charset=utf-8"
                   :body (h/render-html (render-with-modal-close node "タスク設定を更新しました！"))}))))
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 10. DELETE /api/tasks/:id
      (and (= method "DELETE") (.StartsWith path "/api/tasks/"))
      (let [id-str (subs path (count "/api/tasks/"))
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (when task-id
          (task-repo/delete-task connection-string task-id))
        (let [all-tasks (task-repo/get-all-tasks connection-string)
              node (dash/render-dashboard all-tasks "card" "all" "")]
          {:status 200
           :headers {"HX-Trigger" "closeModal"}
           :content-type "text/html; charset=utf-8"
           :body (h/render-html (render-with-modal-close node "タスクを削除しました。"))}))

      ;; 11. POST /api/tasks/:id/run (即時巡回 - ヘッドレス / ブラウザ表示支援)
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
            (let [all-tasks (task-repo/get-all-tasks connection-string)
                  msg (if is-headless "即時巡回が完了しました！" "ブラウザ表示巡回が完了しました！")
                  node (dash/render-dashboard all-tasks "card" "all" "")]
              {:status 200
               :headers {"HX-Trigger" "closeModal"}
               :content-type "text/html; charset=utf-8"
               :body (h/render-html (render-with-modal-close node msg))}))))

      ;; 12. GET /api/tasks/:id/detail-modal or /detail
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (or (.EndsWith path "/detail") (.EndsWith path "/detail-modal")))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if-let [t (and task-id (task-repo/get-task-by-id connection-string task-id))]
          (let [latest (flight-repo/get-latest-offers-for-task connection-string task-id 10)]
            {:status 200
             :content-type "text/html; charset=utf-8"
             :body (h/render-html (modals/render-detail-modal t latest))})
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 13. GET /api/tasks/:id/history (Chart.js 用 JSON API)
      (and (= method "GET")
           (.StartsWith path "/api/tasks/")
           (.EndsWith path "/history"))
      (let [parts (str/split path #"/")
            id-str (nth parts 3)
            task-id (try (Guid/Parse id-str) (catch Exception _ nil))]
        (if task-id
          (let [history (flight-repo/get-price-history connection-string task-id)
                labels (mapv (fn [h]
                               (if-let [cap (:captured-at h)]
                                 (.ToString (.ToOffset ^DateTimeOffset cap (TimeSpan/FromHours 9.0)) "MM/dd HH:mm")
                                 ""))
                             history)
                prices (mapv #(or (:lowest-price-jpy %) 0) history)]
            {:status 200
             :content-type "application/json; charset=utf-8"
             :body (dto/to-json {:labels labels :prices prices})})
          {:status 404 :content-type "text/plain; charset=utf-8" :body "Task not found"}))

      ;; 14. GET /api/settings/modal
      (and (= method "GET") (= path "/api/settings/modal"))
      (let [settings (settings-repo/get-settings connection-string)]
        {:status 200
         :content-type "text/html; charset=utf-8"
         :body (h/render-html (modals/render-settings-modal settings))})

      ;; 15. POST /api/settings
      (and (= method "POST") (= path "/api/settings"))
      (let [interval (try (long (read-string (or (:defaultCheckIntervalHours form) "12"))) (catch Exception _ 12))
            webhook-url (when-not (str/blank? (:defaultWebhookUrl form)) (:defaultWebhookUrl form))
            api-key (when-not (str/blank? (:openRouterApiKey form)) (:openRouterApiKey form))
            enable-gf (or (= (:enableGoogleFlights form) "true") (= (:enableGoogleFlights form) "1"))
            enable-ss (or (= (:enableSkyscanner form) "true") (= (:enableSkyscanner form) "1"))
            headless (not= (:showBrowser form) "true")
            settings {:default-check-interval-hours interval
                      :default-webhook-url webhook-url
                      :openrouter-api-key api-key
                      :enable-google-flights enable-gf
                      :enable-skyscanner enable-ss
                      :headless-mode headless}]
        (settings-repo/update-settings connection-string settings)
        (let [all-tasks (task-repo/get-all-tasks connection-string)
              node (dash/render-dashboard all-tasks "card" "all" "")]
          {:status 200
           :headers {"HX-Trigger" "closeModal"}
           :content-type "text/html; charset=utf-8"
           :body (h/render-html (render-with-modal-close node "システム設定を保存しました！"))}))


      ;; 16. GET /api/logs/modal
      (and (= method "GET") (= path "/api/logs/modal"))
      (let [logs (logger/get-recent-logs 150)
            log-path (logger/get-log-file-path)]
        {:status 200
         :content-type "text/html; charset=utf-8"
         :body (h/render-html (modals/render-logs-modal logs log-path))})

      ;; 17. GET /api/logs/text
      (and (= method "GET") (= path "/api/logs/text"))
      (let [logs (logger/get-recent-logs 150)]
        {:status 200
         :content-type "text/plain; charset=utf-8"
         :body (str/join "\n" logs)})

      ;; 18. POST /api/ai/parse (JSON API: OpenRouter 解析結果返却)
      (and (= method "POST") (= path "/api/ai/parse"))
      (let [prompt (extract-prompt body-str form)]
        (if (str/blank? prompt)
          {:status 400
           :content-type "application/json; charset=utf-8"
           :body "{\"error\":\"プロンプトを入力してください。\"}"}
          (let [settings (settings-repo/get-settings connection-string)
                client (HttpClient.)
                ai-res (ai-client/parse-flight-query client (:openrouter-api-key settings) prompt (get-jst-today))]
            (if (:ok ai-res)
              {:status 200
               :content-type "application/json; charset=utf-8"
               :body (dto/to-json (:ok ai-res))}
              {:status 400
               :content-type "application/json; charset=utf-8"
               :body (dto/to-json {:error (or (:error ai-res) "AI解析に失敗しました。")})}))))

      ;; Default: 404
      :else
      {:status 404
       :content-type "text/plain; charset=utf-8"
       :body "Endpoint not found"})))
