(System.Reflection.Assembly/Load "System.Net.Http")
(ns flight-tracker-ai.infrastructure.ai-client
  (:require [flight-tracker-ai.infrastructure.app-logger :as logger]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System Environment String DateOnly DateTimeOffset DayOfWeek]
           [System.Net.Http HttpClient StringContent]
           [System.Text Encoding]))

(defn- get-openrouter-model []
  (let [env-model (Environment/GetEnvironmentVariable "OPENROUTER_MODEL")]
    (if-not (String/IsNullOrWhiteSpace env-model)
      (.Trim env-model)
      "nvidia/nemotron-3-ultra-550b-a55b:free")))

(defn- extract-json-from-markdown [text]
  (if (or (nil? text) (str/blank? text))
    ""
    (let [trimmed (.Trim (str text))]
      (if (and (.StartsWith trimmed "```") (.Contains trimmed "\n"))
        (let [idx (.IndexOf trimmed "\n")
              without-header (.Substring trimmed (inc idx))
              end-idx (.LastIndexOf without-header "```")]
          (if (>= end-idx 0)
            (.Trim (.Substring without-header 0 end-idx))
            (.Trim without-header)))
        trimmed))))

(defn parse-flight-query-json [raw-text]
  (try
    (let [cleaned (extract-json-from-markdown raw-text)
          get-val (fn [k]
                    (when-let [m (re-find (re-pattern (str "(?i)\"" k "\"[ \\t]*:[ \\t]*\"?([^,}\"]+)\"?")) cleaned)]
                      (let [v (.Trim (str (second m)))]
                        (if (or (= v "null") (str/blank? v)) nil v))))
          get-num (fn [k]
                    (when-let [v (get-val k)]
                      (try (long (read-string v)) (catch Exception _ nil))))
          origin (or (get-val "Origin") (get-val "origin") "")
          dest (or (get-val "Destination") (get-val "destination") "")
          trip-type (or (get-val "TripType") (get-val "tripType") (get-val "trip_type") "OneWay")
          outbound (or (get-val "OutboundDate") (get-val "outboundDate") (get-val "outbound_date") "")
          inbound (or (get-val "InboundDate") (get-val "inboundDate") (get-val "inbound_date"))
          max-stops (or (get-val "MaxStops") (get-val "maxStops") "Any")
          max-price (get-num "MaxPriceJpy")
          title (or (get-val "Title") (get-val "title"))
          notes (or (get-val "Notes") (get-val "notes"))]
      {:ok {:Title title
            :Origin origin
            :Destination dest
            :TripType trip-type
            :OutboundDate outbound
            :InboundDate inbound
            :MaxStops max-stops
            :MaxPriceJpy max-price
            :PreferredAirlines []
            :Notes notes}})
    (catch Exception ex
      {:error (str "JSONパース例外: " (.Message ex))})))

(defn- extract-content-from-response [response-body]
  (try
    (if-let [m (re-find #"\"content\"\s*:\s*\"((?:\\\"|[^\"])*)\"" response-body)]
      (let [raw (second m)]
        (.. raw (Replace "\\\"" "\"") (Replace "\\n" "\n") (Replace "\\r" "\r") (Replace "\\\\" "\\")))
      (if-let [m (re-find #"\"text\"\s*:\s*\"((?:\\\"|[^\"])*)\"" response-body)]
        (let [raw (second m)]
          (.. raw (Replace "\\\"" "\"") (Replace "\\n" "\n") (Replace "\\r" "\r") (Replace "\\\\" "\\")))
        nil))
    (catch Exception _ nil)))

(defn parse-flight-query [^HttpClient http-client api-key-opt prompt-text ^DateOnly today]
  (logger/info "AI" (str "フライトクエリ解析要求を受信しました (文字数: " (count prompt-text) ")"))
  (if (or (nil? api-key-opt) (str/blank? api-key-opt))
    (let [err-msg "OpenRouter API キーが設定されていません。.env ファイル (OPENROUTER_API_KEY) またはシステム全体設定で API キーを登録してください。"]
      (logger/warn "AI" err-msg)
      {:error err-msg})
    (try
      (let [today-str (.ToString today "yyyy-MM-dd")
            model (get-openrouter-model)
            system-prompt (str "あなたは航空券検索・価格監視の専門アシスタントAIです。\n"
                              "ユーザーが入力した自然言語文、箇条書き、またはYAML風テキストを厳密に解析し、必ず指定の純粋な JSON フォーマットのみで回答してください。\n"
                              "【基準日情報】\n- 本日: " today-str "\n"
                              "【出力JSONフォーマット】\n"
                              "{\"Title\":\"HND ➔ CDG 休暇旅行\",\"Origin\":\"HND\",\"Destination\":\"CDG\",\"TripType\":\"RoundTrip\",\"OutboundDate\":\"2026-05-01\",\"InboundDate\":\"2026-05-08\",\"MaxStops\":\"OneStop\",\"MaxPriceJpy\":150000,\"PreferredAirlines\":[\"ANA\"],\"Notes\":\"羽田発希望\"}")
            payload {:model model
                     :messages [{:role "system" :content system-prompt}
                                {:role "user" :content prompt-text}]
                     :temperature 0.1}
            json-str (dto/to-json payload)
            req (StringContent. json-str Encoding/UTF8 "application/json")]
        (.Add (.DefaultRequestHeaders http-client) "Authorization" (str "Bearer " api-key-opt))
        (let [resp (.GetResult (.GetAwaiter (.PostAsync http-client "https://openrouter.ai/api/v1/chat/completions" req)))]
          (if (.IsSuccessStatusCode resp)
            (let [body (.GetResult (.GetAwaiter (.ReadAsStringAsync (.Content resp))))
                  content (extract-content-from-response body)]
              (if content
                (parse-flight-query-json content)
                {:error "AI応答から content を抽出できませんでした"}))
            (let [err (.GetResult (.GetAwaiter (.ReadAsStringAsync (.Content resp))))]
              {:error (str "OpenRouter API 呼び出し失敗 (HTTP " (int (.StatusCode resp)) "): " err)}))))
      (catch Exception ex
        (logger/error-ex "AI" "OpenRouter API 呼び出し例外" ex)
        {:error (str "AI呼び出し例外: " (.Message ex))}))))
