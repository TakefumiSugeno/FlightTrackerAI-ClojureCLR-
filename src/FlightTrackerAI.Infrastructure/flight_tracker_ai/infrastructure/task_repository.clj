(ns flight-tracker-ai.infrastructure.task-repository
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

(defn- reader->task-row [^IDataReader reader]
  {:id (.GetString reader 0)
   :title (.GetString reader 1)
   :origin (.GetString reader 2)
   :destination (.GetString reader 3)
   :trip_type (.GetString reader 4)
   :outbound_date (.GetString reader 5)
   :inbound_date (if (.IsDBNull reader 6) nil (.GetString reader 6))
   :preferred_airlines (.GetString reader 7)
   :max_stops (.GetString reader 8)
   :target_price_jpy (if (.IsDBNull reader 9) nil (.GetInt32 reader 9))
   :check_interval_hours (.GetInt32 reader 10)
   :webhook_url (if (.IsDBNull reader 11) nil (.GetString reader 11))
   :user_notes (if (.IsDBNull reader 12) nil (.GetString reader 12))
   :is_headless (.GetInt32 reader 13)
   :status (.GetString reader 14)
   :error_message (if (.IsDBNull reader 15) nil (.GetString reader 15))
   :consecutive_failures (.GetInt32 reader 16)
   :created_at (.GetString reader 17)
   :updated_at (.GetString reader 18)
   :last_checked_at (if (.IsDBNull reader 19) nil (.GetString reader 19))
   :last_lowest_price_jpy (if (.IsDBNull reader 20) nil (.GetInt32 reader 20))
   :last_lowest_airlines (if (.IsDBNull reader 21) nil (.GetString reader 21))
   :last_lowest_provider (if (.IsDBNull reader 22) nil (.GetString reader 22))
   :ai_analysis_summary (if (.IsDBNull reader 23) nil (.GetString reader 23))})

(def ^:private select-columns
  "id, title, origin, destination, trip_type, outbound_date, inbound_date,
   preferred_airlines, max_stops, target_price_jpy, check_interval_hours,
   webhook_url, user_notes, is_headless, status, error_message, consecutive_failures,
   created_at, updated_at, last_checked_at, last_lowest_price_jpy,
   last_lowest_airlines, last_lowest_provider, ai_analysis_summary")

(defn get-all-tasks [^String connection-string]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd) (str "SELECT " select-columns " FROM tasks ORDER BY created_at DESC;"))
        (with-open [reader (.ExecuteReader cmd)]
          (loop [acc []]
            (if (.Read reader)
              (let [row (reader->task-row reader)
                    task-res (dto/row->task row)]
                (if (:ok task-res)
                  (recur (conj acc (:ok task-res)))
                  (recur acc)))
              acc)))
        (finally (.Dispose cmd))))))

(defn get-task-by-id [^String connection-string ^Guid task-id]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd) (str "SELECT " select-columns " FROM tasks WHERE id = @Id;"))
        (add-param cmd "@Id" (.ToString task-id))
        (with-open [reader (.ExecuteReader cmd)]
          (if (.Read reader)
            (let [row (reader->task-row reader)
                  task-res (dto/row->task row)]
              (:ok task-res))
            nil))
        (finally (.Dispose cmd))))))

(defn get-active-tasks [^String connection-string]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd)
              (str "SELECT " select-columns " FROM tasks WHERE status = 'Active' ORDER BY last_checked_at ASC NULLS FIRST;"))
        (with-open [reader (.ExecuteReader cmd)]
          (loop [acc []]
            (if (.Read reader)
              (let [row (reader->task-row reader)
                    task-res (dto/row->task row)]
                (if (:ok task-res)
                  (recur (conj acc (:ok task-res)))
                  (recur acc)))
              acc)))
        (finally (.Dispose cmd))))))

(defn create-task [^String connection-string task-item]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)
          row (dto/task->row task-item)]
      (try
        (set! (.CommandText cmd)
              "INSERT INTO tasks (
                  id, title, origin, destination, trip_type, outbound_date, inbound_date,
                  preferred_airlines, max_stops, target_price_jpy, check_interval_hours,
                  webhook_url, user_notes, is_headless, status, error_message, consecutive_failures,
                  created_at, updated_at, last_checked_at, last_lowest_price_jpy,
                  last_lowest_airlines, last_lowest_provider, ai_analysis_summary
              ) VALUES (
                  @Id, @Title, @Origin, @Destination, @TripType, @OutboundDate, @InboundDate,
                  @PreferredAirlines, @MaxStops, @TargetPriceJpy, @CheckIntervalHours,
                  @WebhookUrl, @UserNotes, @IsHeadless, @Status, @ErrorMessage, @ConsecutiveFailures,
                  @CreatedAt, @UpdatedAt, @LastCheckedAt, @LastLowestPriceJpy,
                  @LastLowestAirlines, @LastLowestProvider, @AiAnalysisSummary
              );")
        (add-param cmd "@Id" (:id row))
        (add-param cmd "@Title" (:title row))
        (add-param cmd "@Origin" (:origin row))
        (add-param cmd "@Destination" (:destination row))
        (add-param cmd "@TripType" (:trip_type row))
        (add-param cmd "@OutboundDate" (:outbound_date row))
        (add-param cmd "@InboundDate" (:inbound_date row))
        (add-param cmd "@PreferredAirlines" (:preferred_airlines row))
        (add-param cmd "@MaxStops" (:max_stops row))
        (add-param cmd "@TargetPriceJpy" (:target_price_jpy row))
        (add-param cmd "@CheckIntervalHours" (:check_interval_hours row))
        (add-param cmd "@WebhookUrl" (:webhook_url row))
        (add-param cmd "@UserNotes" (:user_notes row))
        (add-param cmd "@IsHeadless" (:is_headless row))
        (add-param cmd "@Status" (:status row))
        (add-param cmd "@ErrorMessage" (:error_message row))
        (add-param cmd "@ConsecutiveFailures" (:consecutive_failures row))
        (add-param cmd "@CreatedAt" (:created_at row))
        (add-param cmd "@UpdatedAt" (:updated_at row))
        (add-param cmd "@LastCheckedAt" (:last_checked_at row))
        (add-param cmd "@LastLowestPriceJpy" (:last_lowest_price_jpy row))
        (add-param cmd "@LastLowestAirlines" (:last_lowest_airlines row))
        (add-param cmd "@LastLowestProvider" (:last_lowest_provider row))
        (add-param cmd "@AiAnalysisSummary" (:ai_analysis_summary row))
        (.ExecuteNonQuery cmd)
        nil
        (finally (.Dispose cmd))))))

(defn update-task [^String connection-string task-item]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)
          row (dto/task->row task-item)]
      (try
        (set! (.CommandText cmd)
              "UPDATE tasks SET
                  title = @Title,
                  origin = @Origin,
                  destination = @Destination,
                  trip_type = @TripType,
                  outbound_date = @OutboundDate,
                  inbound_date = @InboundDate,
                  preferred_airlines = @PreferredAirlines,
                  max_stops = @MaxStops,
                  target_price_jpy = @TargetPriceJpy,
                  check_interval_hours = @CheckIntervalHours,
                  webhook_url = @WebhookUrl,
                  user_notes = @UserNotes,
                  is_headless = @IsHeadless,
                  status = @Status,
                  error_message = @ErrorMessage,
                  consecutive_failures = @ConsecutiveFailures,
                  updated_at = @UpdatedAt,
                  last_checked_at = @LastCheckedAt,
                  last_lowest_price_jpy = @LastLowestPriceJpy,
                  last_lowest_airlines = @LastLowestAirlines,
                  last_lowest_provider = @LastLowestProvider,
                  ai_analysis_summary = @AiAnalysisSummary
              WHERE id = @Id;")
        (add-param cmd "@Id" (:id row))
        (add-param cmd "@Title" (:title row))
        (add-param cmd "@Origin" (:origin row))
        (add-param cmd "@Destination" (:destination row))
        (add-param cmd "@TripType" (:trip_type row))
        (add-param cmd "@OutboundDate" (:outbound_date row))
        (add-param cmd "@InboundDate" (:inbound_date row))
        (add-param cmd "@PreferredAirlines" (:preferred_airlines row))
        (add-param cmd "@MaxStops" (:max_stops row))
        (add-param cmd "@TargetPriceJpy" (:target_price_jpy row))
        (add-param cmd "@CheckIntervalHours" (:check_interval_hours row))
        (add-param cmd "@WebhookUrl" (:webhook_url row))
        (add-param cmd "@UserNotes" (:user_notes row))
        (add-param cmd "@IsHeadless" (:is_headless row))
        (add-param cmd "@Status" (:status row))
        (add-param cmd "@ErrorMessage" (:error_message row))
        (add-param cmd "@ConsecutiveFailures" (:consecutive_failures row))
        (add-param cmd "@UpdatedAt" (.ToString (DateTimeOffset/UtcNow) "o"))
        (add-param cmd "@LastCheckedAt" (:last_checked_at row))
        (add-param cmd "@LastLowestPriceJpy" (:last_lowest_price_jpy row))
        (add-param cmd "@LastLowestAirlines" (:last_lowest_airlines row))
        (add-param cmd "@LastLowestProvider" (:last_lowest_provider row))
        (add-param cmd "@AiAnalysisSummary" (:ai_analysis_summary row))
        (.ExecuteNonQuery cmd)
        nil
        (finally (.Dispose cmd))))))

(defn update-check-result
  [^String connection-string ^Guid task-id ^DateTimeOffset checked-at lowest-price lowest-airlines lowest-provider]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)
          checked-str (.ToString checked-at "o")
          provider-str (when lowest-provider (domain/scraping-provider-to-string lowest-provider))]
      (try
        (set! (.CommandText cmd)
              "UPDATE tasks SET
                  last_checked_at = @CheckedAt,
                  last_lowest_price_jpy = COALESCE(@LowestPrice, last_lowest_price_jpy),
                  last_lowest_airlines = COALESCE(@LowestAirlines, last_lowest_airlines),
                  last_lowest_provider = COALESCE(@LowestProvider, last_lowest_provider),
                  consecutive_failures = 0,
                  error_message = NULL,
                  updated_at = @CheckedAt
              WHERE id = @Id;")
        (add-param cmd "@Id" (.ToString task-id))
        (add-param cmd "@CheckedAt" checked-str)
        (add-param cmd "@LowestPrice" lowest-price)
        (add-param cmd "@LowestAirlines" lowest-airlines)
        (add-param cmd "@LowestProvider" provider-str)
        (.ExecuteNonQuery cmd)
        nil
        (finally (.Dispose cmd))))))

(defn record-failure [^String connection-string ^Guid task-id ^String error-message]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd)
              "UPDATE tasks SET
                  consecutive_failures = consecutive_failures + 1,
                  error_message = @ErrorMessage,
                  updated_at = @UpdatedAt
              WHERE id = @Id;")
        (add-param cmd "@Id" (.ToString task-id))
        (add-param cmd "@ErrorMessage" error-message)
        (add-param cmd "@UpdatedAt" (.ToString (DateTimeOffset/UtcNow) "o"))
        (.ExecuteNonQuery cmd)
        nil
        (finally (.Dispose cmd))))))

(defn delete-task [^String connection-string ^Guid task-id]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd) "DELETE FROM tasks WHERE id = @Id;")
        (add-param cmd "@Id" (.ToString task-id))
        (.ExecuteNonQuery cmd)
        nil
        (finally (.Dispose cmd))))))
