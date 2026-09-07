(ns flight-tracker-ai.infrastructure.settings-repository
  (:require [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.core.dto :as dto])
  (:import [System Environment String DateTimeOffset]
           [System.Data IDbCommand IDataParameterCollection IDataParameter]))

(defn- add-param [^IDbCommand cmd ^String name val]
  (let [p (.CreateParameter cmd)]
    (set! (.ParameterName p) name)
    (set! (.Value p) (if (nil? val) System.DBNull/Value val))
    (.Add ^IDataParameterCollection (.Parameters cmd) p)))

(defn get-settings [^String connection-string]
  (with-open [conn (db/create-connection connection-string)]
    (with-open [cmd (.CreateCommand conn)]
      (set! (.CommandText cmd)
            "SELECT id, default_check_interval_hours, default_webhook_url, openrouter_api_key, enable_google_flights, enable_skyscanner, headless_mode, updated_at FROM system_settings WHERE id = 1;")
      (with-open [reader (.ExecuteReader cmd)]
        (let [env-api-key (let [v (Environment/GetEnvironmentVariable "OPENROUTER_API_KEY")]
                            (if (String/IsNullOrWhiteSpace v) nil v))]
          (if (.Read reader)
            (let [check-interval (.GetInt32 reader 1)
                  webhook-url (if (.IsDBNull reader 2) nil (.GetString reader 2))
                  db-api-key (if (.IsDBNull reader 3) nil (.GetString reader 3))
                  enable-gf (not= (.GetInt32 reader 4) 0)
                  enable-ss (not= (.GetInt32 reader 5) 0)
                  headless (not= (.GetInt32 reader 6) 0)
                  effective-key (if (and db-api-key (not (String/IsNullOrWhiteSpace db-api-key)))
                                  db-api-key
                                  env-api-key)]
              {:default-check-interval-hours (long check-interval)
               :default-webhook-url (when-not (String/IsNullOrWhiteSpace webhook-url) webhook-url)
               :openrouter-api-key effective-key
               :enable-google-flights enable-gf
               :enable-skyscanner enable-ss
               :headless-mode headless})
            ;; Fallback defaults
            {:default-check-interval-hours 12
             :default-webhook-url nil
             :openrouter-api-key env-api-key
             :enable-google-flights true
             :enable-skyscanner true
             :headless-mode true}))))))

(defn update-settings [^String connection-string settings]
  (with-open [conn (db/create-connection connection-string)]
    (let [^IDbCommand cmd (.CreateCommand conn)]
      (try
        (set! (.CommandText cmd)
              "UPDATE system_settings SET
                  default_check_interval_hours = @DefaultCheckIntervalHours,
                  default_webhook_url = @DefaultWebhookUrl,
                  openrouter_api_key = @OpenrouterApiKey,
                  enable_google_flights = @EnableGoogleFlights,
                  enable_skyscanner = @EnableSkyscanner,
                  headless_mode = @HeadlessMode,
                  updated_at = @UpdatedAt
              WHERE id = 1;")
        (add-param cmd "@DefaultCheckIntervalHours" (or (:default-check-interval-hours settings) 12))
        (add-param cmd "@DefaultWebhookUrl" (:default-webhook-url settings))
        (add-param cmd "@OpenrouterApiKey" (:openrouter-api-key settings))
        (add-param cmd "@EnableGoogleFlights" (if (false? (:enable-google-flights settings)) 0 1))
        (add-param cmd "@EnableSkyscanner" (if (false? (:enable-skyscanner settings)) 0 1))
        (add-param cmd "@HeadlessMode" (if (false? (:headless-mode settings)) 0 1))
        (add-param cmd "@UpdatedAt" (.ToString (DateTimeOffset/UtcNow) "o"))
        (.ExecuteNonQuery cmd)
        nil
        (finally
          (.Dispose cmd))))))
