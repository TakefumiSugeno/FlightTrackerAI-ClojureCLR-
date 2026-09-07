(ns flight-tracker-ai.infrastructure.flight-repository
  (:require [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto])
  (:import [System Guid DateTimeOffset String]
           [System.Data IDbCommand IDataParameterCollection IDataParameter IDataReader]))

(defn- add-param [^IDbCommand cmd ^String name val]
  (let [p (.CreateParameter cmd)]
    (set! (.ParameterName p) name)
    (set! (.Value p) (if (nil? val) System.DBNull/Value val))
    (.Add ^IDataParameterCollection (.Parameters cmd) p)))

(defn create-run-log [^String connection-string ^Guid task-id provider]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)
          run-log-id (Guid/NewGuid)
          now-str (.ToString (DateTimeOffset/UtcNow) "o")
          provider-str (domain/scraping-provider-to-string provider)]
      (try
        (set! (.CommandText cmd)
              "INSERT INTO task_run_logs (
                  id, task_id, executed_at, status, provider, found_offers_count,
                  lowest_price_jpy, duration_ms, error_message, ai_analysis_summary
              ) VALUES (
                  @Id, @TaskId, @ExecutedAt, 'Running', @Provider, 0, NULL, 0, NULL, NULL
              );")
        (add-param cmd "@Id" (.ToString run-log-id))
        (add-param cmd "@TaskId" (.ToString task-id))
        (add-param cmd "@ExecutedAt" now-str)
        (add-param cmd "@Provider" provider-str)
        (.ExecuteNonQuery cmd)
        run-log-id
        (finally (.Dispose cmd))))))

(defn complete-run-log [^String connection-string ^Guid run-log-id found-offers-count lowest-price duration-ms error-message]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)
          status (if error-message "Failed" "Success")]
      (try
        (set! (.CommandText cmd)
              "UPDATE task_run_logs SET
                  status = @Status,
                  found_offers_count = @FoundOffersCount,
                  lowest_price_jpy = @LowestPrice,
                  duration_ms = @DurationMs,
                  error_message = @ErrorMessage
              WHERE id = @Id;")
        (add-param cmd "@Id" (.ToString run-log-id))
        (add-param cmd "@Status" status)
        (add-param cmd "@FoundOffersCount" (int found-offers-count))
        (add-param cmd "@LowestPrice" lowest-price)
        (add-param cmd "@DurationMs" (int duration-ms))
        (add-param cmd "@ErrorMessage" error-message)
        (.ExecuteNonQuery cmd)
        nil
        (finally (.Dispose cmd))))))

(defn save-snapshots [^String connection-string offers]
  (when (seq offers)
    (with-open [conn (db/create-connection connection-string)]
      (doseq [offer offers]
        (let [^IDbCommand cmd (.CreateCommand conn)
              row (dto/offer->row offer)]
          (try
            (set! (.CommandText cmd)
                  "INSERT INTO flight_snapshots (
                      id, task_id, run_log_id, provider, airlines_summary,
                      departure_time, arrival_time, total_duration_minutes,
                      stops_count, segments_json, price_jpy, booking_url, captured_at
                  ) VALUES (
                      @Id, @TaskId, @RunLogId, @Provider, @AirlinesSummary,
                      @DepartureTime, @ArrivalTime, @TotalDurationMinutes,
                      @StopsCount, @SegmentsJson, @PriceJpy, @BookingUrl, @CapturedAt
                  );")
            (add-param cmd "@Id" (:id row))
            (add-param cmd "@TaskId" (:task_id row))
            (add-param cmd "@RunLogId" (:run_log_id row))
            (add-param cmd "@Provider" (:provider row))
            (add-param cmd "@AirlinesSummary" (:airlines_summary row))
            (add-param cmd "@DepartureTime" (:departure_time row))
            (add-param cmd "@ArrivalTime" (:arrival_time row))
            (add-param cmd "@TotalDurationMinutes" (:total_duration_minutes row))
            (add-param cmd "@StopsCount" (:stops_count row))
            (add-param cmd "@SegmentsJson" (:segments_json row))
            (add-param cmd "@PriceJpy" (:price_jpy row))
            (add-param cmd "@BookingUrl" (:booking_url row))
            (add-param cmd "@CapturedAt" (:captured_at row))
            (.ExecuteNonQuery cmd)
            (finally (.Dispose cmd))))))))

(defn- reader->snapshot-row [^IDataReader reader]
  {:id (.GetString reader 0)
   :task_id (.GetString reader 1)
   :run_log_id (.GetString reader 2)
   :provider (.GetString reader 3)
   :airlines_summary (.GetString reader 4)
   :departure_time (.GetString reader 5)
   :arrival_time (.GetString reader 6)
   :total_duration_minutes (.GetInt32 reader 7)
   :stops_count (.GetInt32 reader 8)
   :segments_json (.GetString reader 9)
   :price_jpy (.GetInt32 reader 10)
   :booking_url (.GetString reader 11)
   :captured_at (.GetString reader 12)})

(defn get-latest-offers-for-task [^String connection-string ^Guid task-id limit]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd)
              "SELECT
                  id, task_id, run_log_id, provider, airlines_summary,
                  departure_time, arrival_time, total_duration_minutes,
                  stops_count, segments_json, price_jpy, booking_url, captured_at
              FROM flight_snapshots
              WHERE task_id = @TaskId
              ORDER BY captured_at DESC, price_jpy ASC
              LIMIT @Limit;")
        (add-param cmd "@TaskId" (.ToString task-id))
        (add-param cmd "@Limit" (int limit))
        (with-open [reader (.ExecuteReader cmd)]
          (loop [acc []]
            (if (.Read reader)
              (let [row (reader->snapshot-row reader)
                    offer (dto/row->offer row)]
                (recur (conj acc offer)))
              acc)))
        (finally (.Dispose cmd))))))

(defn get-price-history [^String connection-string ^Guid task-id]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd)
              "SELECT
                  captured_at,
                  MIN(price_jpy) AS lowest_price_jpy,
                  provider,
                  airlines_summary
              FROM flight_snapshots
              WHERE task_id = @TaskId AND price_jpy > 0
              GROUP BY captured_at, provider
              ORDER BY captured_at ASC;")
        (add-param cmd "@TaskId" (.ToString task-id))
        (with-open [reader (.ExecuteReader cmd)]
          (loop [acc []]
            (if (.Read reader)
              (let [captured-str (.GetString reader 0)
                    lowest-price (.GetInt32 reader 1)
                    provider-str (.GetString reader 2)
                    airlines-summary (.GetString reader 3)
                    dt (try (DateTimeOffset/Parse captured-str) (catch Exception _ (DateTimeOffset/UtcNow)))
                    provider (domain/scraping-provider-from-string provider-str)]
                (recur (conj acc {:captured-at dt
                                  :lowest-price-jpy (long lowest-price)
                                  :provider provider
                                  :airlines-summary airlines-summary})))
              acc)))
        (finally (.Dispose cmd))))))

(defn purge-old-logs [^String connection-string days-to-keep]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)
          cutoff (.ToString (.AddDays (DateTimeOffset/UtcNow) (- (double days-to-keep))) "o")]
      (try
        (set! (.CommandText cmd) "DELETE FROM task_run_logs WHERE executed_at < @Cutoff;")
        (add-param cmd "@Cutoff" cutoff)
        (let [deleted (.ExecuteNonQuery cmd)]
          (long deleted))
        (finally (.Dispose cmd))))))
