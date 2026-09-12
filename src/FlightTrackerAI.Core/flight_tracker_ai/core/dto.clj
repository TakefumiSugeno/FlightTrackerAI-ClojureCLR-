(ns flight-tracker-ai.core.dto
  (:require [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System Guid DateTimeOffset DateOnly TimeSpan]))

(defn- get-jst-today []
  (DateOnly/FromDateTime (.DateTime (.ToOffset (DateTimeOffset/UtcNow) (TimeSpan/FromHours 9.0)))))

;; -------------------------------------------------------------
;; 1. JSON Helpers (Pure Clojure & robust fallback)
;; -------------------------------------------------------------
(defn to-json [v]
  (cond
    (nil? v) "null"
    (string? v) (str "\"" (.. (str v) (Replace "\\" "\\\\") (Replace "\"" "\\\"")) "\"")
    (number? v) (str v)
    (boolean? v) (if v "true" "false")
    (keyword? v) (str "\"" (name v) "\"")
    (sequential? v) (str "[" (str/join "," (map to-json v)) "]")
    (map? v) (str "{" (str/join "," (map (fn [[k val]] (str "\"" (name k) "\":" (to-json val))) v)) "}")
    :else (str "\"" (str v) "\"")))

(defn from-json-array-strings [s]
  (if (or (nil? s) (str/blank? s))
    []
    (let [trimmed (.Trim (str s))]
      (if (and (.StartsWith trimmed "[") (.EndsWith trimmed "]"))
        (let [inner (.Trim (subs trimmed 1 (dec (count trimmed))))]
          (if (str/blank? inner)
            []
            (->> (str/split inner #",")
                 (map (fn [item]
                        (let [t (.Trim (str item))]
                          (if (and (.StartsWith t "\"") (.EndsWith t "\"") (>= (count t) 2))
                            (subs t 1 (dec (count t)))
                            t))))
                 (filter #(not (str/blank? %)))
                 vec)))
        []))))

;; -------------------------------------------------------------
;; 2. FlightTask <-> TaskRow
;; -------------------------------------------------------------
(defn task->row [task]
  (let [trip (or (:trip-type task) {})
        is-round (= (:kind trip) :round-trip)
        outbound-val (or (:outbound trip) (:outbound-date trip))
        inbound-val (or (:inbound trip) (:inbound-date trip))
        outbound-str (if (instance? DateOnly outbound-val)
                       (.ToString ^DateOnly outbound-val "yyyy-MM-dd")
                       (str (or outbound-val "")))
        inbound-str (when is-round
                      (if (instance? DateOnly inbound-val)
                        (.ToString ^DateOnly inbound-val "yyyy-MM-dd")
                        (when inbound-val (str inbound-val))))
        trip-type-str (if is-round "RoundTrip" "OneWay")
        status (:status task)
        [status-str error-msg] (cond
                                 (= status :active) ["Active" nil]
                                 (= status :paused) ["Paused" nil]
                                 (= status :completed) ["Completed" nil]
                                 (map? status) ["Failed" (or (:failed status) (:error status))]
                                 :else ["Active" nil])
        created-at (if (instance? DateTimeOffset (:created-at task))
                     (.ToString ^DateTimeOffset (:created-at task) "o")
                     (str (or (:created-at task) "")))
        updated-at (if (instance? DateTimeOffset (:updated-at task))
                     (.ToString ^DateTimeOffset (:updated-at task) "o")
                     (str (or (:updated-at task) "")))
        last-checked-at (when-let [dt (:last-checked-at task)]
                          (if (instance? DateTimeOffset dt)
                            (.ToString ^DateTimeOffset dt "o")
                            (str dt)))]
    {:id (str (:id task))
     :title (or (:title task) "")
     :origin (domain/iata-code-value (:origin task))
     :destination (domain/iata-code-value (:destination task))
     :trip_type trip-type-str
     :outbound_date outbound-str
     :inbound_date inbound-str
     :preferred_airlines (to-json (or (:preferred-airlines task) []))
     :max_stops (domain/max-stops-to-string (:max-stops task))
     :target_price_jpy (:target-price-jpy task)
     :check_interval_hours (or (:check-interval-hours task) 12)
     :webhook_url (:notification-webhook-url task)
     :user_notes (:user-notes task)
     :is_headless (if (false? (:is-headless task)) 0 1)
     :status status-str
     :error_message error-msg
     :consecutive_failures (or (:consecutive-failures task) 0)
     :created_at created-at
     :updated_at updated-at
     :last_checked_at last-checked-at
     :last_lowest_price_jpy (:last-lowest-price-jpy task)
     :last_lowest_airlines (:last-lowest-airlines task)
     :last_lowest_provider (when-let [p (:last-lowest-provider task)]
                             (domain/scraping-provider-to-string p))
     :ai_analysis_summary (:ai-analysis-summary task)}))

(defn row->task [row]
  (let [origin-res (domain/create-iata-code (or (:origin row) ""))
        dest-res (domain/create-iata-code (or (:destination row) ""))]
    (if (:error origin-res)
      origin-res
      (if (:error dest-res)
        dest-res
        (let [is-round (and (= (:trip_type row) "RoundTrip") (not (str/blank? (:inbound_date row))))
              outbound-date (try (DateOnly/Parse (:outbound_date row))
                                 (catch Exception _ (get-jst-today)))
              inbound-date (when is-round
                             (try (DateOnly/Parse (:inbound_date row))
                                  (catch Exception _ nil)))
              trip-type (if is-round
                          {:kind :round-trip
                           :outbound outbound-date
                           :outbound-date outbound-date
                           :inbound inbound-date
                           :inbound-date inbound-date}
                          {:kind :one-way
                           :outbound outbound-date
                           :outbound-date outbound-date})
              pref-airlines (from-json-array-strings (:preferred_airlines row))
              max-stops (domain/max-stops-from-string (:max_stops row))
              status (domain/task-status-from-string (:status row) (:error_message row))
              last-checked (when-not (str/blank? (:last_checked_at row))
                             (try (DateTimeOffset/Parse (:last_checked_at row)) (catch Exception _ nil)))
              last-provider (when-not (str/blank? (:last_lowest_provider row))
                              (domain/scraping-provider-from-string (:last_lowest_provider row)))
              created-at (try (DateTimeOffset/Parse (:created_at row)) (catch Exception _ (DateTimeOffset/UtcNow)))
              updated-at (try (DateTimeOffset/Parse (:updated_at row)) (catch Exception _ (DateTimeOffset/UtcNow)))]
          {:ok
           {:id (try (Guid/Parse (str (:id row))) (catch Exception _ (Guid/NewGuid)))
            :title (:title row)
            :origin (:ok origin-res)
            :destination (:ok dest-res)
            :trip-type trip-type
            :max-stops max-stops
            :preferred-airlines pref-airlines
            :target-price-jpy (when-let [p (:target_price_jpy row)] (long p))
            :check-interval-hours (long (or (:check_interval_hours row) 12))
            :notification-webhook-url (when-not (str/blank? (:webhook_url row)) (:webhook_url row))
            :user-notes (when-not (str/blank? (:user_notes row)) (:user_notes row))
            :is-headless (not= (:is_headless row) 0)
            :status status
            :consecutive-failures (long (or (:consecutive_failures row) 0))
            :created-at created-at
            :updated-at updated-at
            :last-checked-at last-checked
            :last-lowest-price-jpy (when-let [p (:last_lowest_price_jpy row)] (long p))
            :last-lowest-airlines (when-not (str/blank? (:last_lowest_airlines row)) (:last_lowest_airlines row))
            :last-lowest-provider last-provider
            :ai-analysis-summary (when-not (str/blank? (:ai_analysis_summary row)) (:ai_analysis_summary row))}})))))

;; -------------------------------------------------------------
;; 3. FlightOffer <-> FlightSnapshotRow
;; -------------------------------------------------------------
(defn offer->row [offer]
  (let [dep-str (if (instance? DateTimeOffset (:departure-time offer))
                  (.ToString ^DateTimeOffset (:departure-time offer) "o")
                  (str (or (:departure-time offer) "")))
        arr-str (if (instance? DateTimeOffset (:arrival-time offer))
                  (.ToString ^DateTimeOffset (:arrival-time offer) "o")
                  (str (or (:arrival-time offer) "")))
        cap-str (if (instance? DateTimeOffset (:captured-at offer))
                  (.ToString ^DateTimeOffset (:captured-at offer) "o")
                  (str (or (:captured-at offer) "")))]
    {:id (str (:id offer))
     :task_id (str (:task-id offer))
     :run_log_id (str (:run-log-id offer))
     :provider (domain/scraping-provider-to-string (:provider offer))
     :airlines_summary (or (:airlines-summary offer) "")
     :departure_time dep-str
     :arrival_time arr-str
     :total_duration_minutes (or (:total-duration-minutes offer) 0)
     :stops_count (or (:stops-count offer) 0)
     :segments_json (to-json (or (:segments offer) []))
     :price_jpy (or (:price-jpy offer) 0)
     :booking_url (or (:booking-url offer) "")
     :captured_at cap-str}))

(defn row->offer [row]
  (let [segments (try
                   ;; Simple deserializer for segments if needed
                   (let [json-str (or (:segments_json row) "[]")]
                     ;; if contains flight number or segments
                     (if (or (str/blank? json-str) (= json-str "[]"))
                       []
                       (let [items (re-seq #"\{[^{}]+\}" json-str)]
                         (mapv (fn [item]
                                 (let [get-val (fn [k]
                                                 (when-let [m (re-find (re-pattern (str "\"" k "\":\"?([^,\"}]+)\"?")) item)]
                                                   (second m)))]
                                   {:leg-index (long (try (long (read-string (or (get-val "leg-index") "0"))) (catch Exception _ 0)))
                                    :segment-index (long (try (long (read-string (or (get-val "segment-index") "0"))) (catch Exception _ 0)))
                                    :departure-airport (or (get-val "departure-airport") "")
                                    :arrival-airport (or (get-val "arrival-airport") "")
                                    :marketing-airline (or (get-val "marketing-airline") "")
                                    :operating-airline (get-val "operating-airline")
                                    :flight-number (get-val "flight-number")
                                    :flight-duration-minutes (long (try (long (read-string (or (get-val "flight-duration-minutes") "0"))) (catch Exception _ 0)))
                                    :layover-minutes-next (when-let [l (get-val "layover-minutes-next")]
                                                            (try (long (read-string l)) (catch Exception _ nil)))}))
                               items))))
                   (catch Exception _ []))]
    {:id (try (Guid/Parse (str (:id row))) (catch Exception _ (Guid/NewGuid)))
     :task-id (try (Guid/Parse (str (:task_id row))) (catch Exception _ (Guid/NewGuid)))
     :run-log-id (try (Guid/Parse (str (:run_log_id row))) (catch Exception _ (Guid/NewGuid)))
     :provider (domain/scraping-provider-from-string (:provider row))
     :airlines-summary (:airlines_summary row)
     :departure-time (try (DateTimeOffset/Parse (:departure_time row)) (catch Exception _ (DateTimeOffset/UtcNow)))
     :arrival-time (try (DateTimeOffset/Parse (:arrival_time row)) (catch Exception _ (DateTimeOffset/UtcNow)))
     :total-duration-minutes (long (or (:total_duration_minutes row) 0))
     :stops-count (long (or (:stops_count row) 0))
     :segments segments
     :price-jpy (long (or (:price_jpy row) 0))
     :booking-url (:booking_url row)
     :captured-at (try (DateTimeOffset/Parse (:captured_at row)) (catch Exception _ (DateTimeOffset/UtcNow)))}))

;; -------------------------------------------------------------
;; 4. SystemSettings <-> SystemSettingsRow
;; -------------------------------------------------------------
(defn settings->row [settings]
  {:id 1
   :default_check_interval_hours (or (:default-check-interval-hours settings) 12)
   :default_webhook_url (:default-webhook-url settings)
   :openrouter_api_key (:openrouter-api-key settings)
   :enable_google_flights (if (false? (:enable-google-flights settings)) 0 1)
   :enable_skyscanner (if (false? (:enable-skyscanner settings)) 0 1)
   :headless_mode (if (false? (:headless-mode settings)) 0 1)
   :updated_at (.ToString (DateTimeOffset/UtcNow) "o")})

(defn row->settings [row]
  {:default-check-interval-hours (long (or (:default_check_interval_hours row) 12))
   :default-webhook-url (when-not (str/blank? (:default_webhook_url row)) (:default_webhook_url row))
   :openrouter-api-key (when-not (str/blank? (:openrouter_api_key row)) (:openrouter_api_key row))
   :enable-google-flights (not= (:enable_google_flights row) 0)
   :enable-skyscanner (not= (:enable_skyscanner row) 0)
   :headless-mode (not= (:headless_mode row) 0)})
