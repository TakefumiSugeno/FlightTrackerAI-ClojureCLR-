(ns flight-tracker-ai.core.domain
  (:import [System Char String Guid DateTimeOffset DateOnly]))

;; -------------------------------------------------------------
;; 1. IATA Code (3文字英字)
;; -------------------------------------------------------------
(defn create-iata-code [input]
  (if (String/IsNullOrWhiteSpace input)
    {:error "IATAコードは空にできません。"}
    (let [trimmed (.ToUpperInvariant (.Trim (str input)))]
      (if (and (= 3 (count trimmed))
               (every? (fn [c] (and (>= (int c) (int \A)) (<= (int c) (int \Z)))) trimmed))
        {:ok trimmed}
        {:error (str "無効なIATAコード形式です: '" input "'。英字3文字である必要があります。")}))))

(defn iata-code-value [code]
  (if (map? code)
    (or (:ok code) (:value code) code)
    (str code)))

;; -------------------------------------------------------------
;; 2. MaxStops (乗継許容回数)
;; -------------------------------------------------------------
(defn max-stops-to-string [stops]
  (cond
    (= stops :direct-only) "DirectOnly"
    (= stops :one-stop) "OneStop"
    (= stops :any-stops) "Any"
    (= stops "DirectOnly") "DirectOnly"
    (= stops "OneStop") "OneStop"
    :else "Any"))

(defn max-stops-from-string [s]
  (if (nil? s)
    :any-stops
    (let [lower (.ToLowerInvariant (.Trim (str s)))]
      (cond
        (or (= lower "directonly") (= lower "0") (= lower "direct")) :direct-only
        (or (= lower "onestop") (= lower "1")) :one-stop
        :else :any-stops))))

;; -------------------------------------------------------------
;; 3. TaskStatus (タスク状態)
;; -------------------------------------------------------------
(defn task-status-to-string [status]
  (cond
    (= status :active) "Active"
    (= status :paused) "Paused"
    (= status :completed) "Completed"
    (and (map? status) (:failed status)) (str "Error: " (:failed status))
    (and (map? status) (:error status)) (str "Error: " (:error status))
    (= status "Active") "Active"
    (= status "Paused") "Paused"
    (= status "Completed") "Completed"
    :else "Active"))

(defn task-status-from-string [status-str error-msg-opt]
  (if (nil? status-str)
    :active
    (let [lower (.ToLowerInvariant (.Trim (str status-str)))]
      (cond
        (= lower "paused") :paused
        (= lower "completed") :completed
        (or (= lower "failed") (= lower "error"))
        {:failed (or error-msg-opt "Unknown Error")}
        :else :active))))

;; -------------------------------------------------------------
;; 4. ScrapingProvider (スクレイピングプロバイダー)
;; -------------------------------------------------------------
(defn scraping-provider-to-string [provider]
  (cond
    (= provider :google-flights) "GoogleFlights"
    (= provider :skyscanner) "Skyscanner"
    (= provider "GoogleFlights") "GoogleFlights"
    (= provider "Skyscanner") "Skyscanner"
    :else "GoogleFlights"))

(defn scraping-provider-from-string [s]
  (if (nil? s)
    :google-flights
    (let [lower (.ToLowerInvariant (.Trim (str s)))]
      (if (= lower "skyscanner")
        :skyscanner
        :google-flights))))

;; -------------------------------------------------------------
;; 5. 目標価格達成判定 (派生状態)
;; -------------------------------------------------------------
(defn target-achieved? [task]
  (let [status (:status task)
        target (:target-price-jpy task)
        lowest (:last-lowest-price-jpy task)]
    (and (= status :active)
         (number? target)
         (number? lowest)
         (> lowest 0)
         (<= lowest target))))

;; -------------------------------------------------------------
;; 6. デフォルト設定
;; -------------------------------------------------------------
(def default-system-settings
  {:default-check-interval-hours 12
   :default-webhook-url nil
   :openrouter-api-key nil
   :enable-google-flights true
   :enable-skyscanner true
   :headless-mode true})
