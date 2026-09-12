(System.Reflection.Assembly/Load "System.Net.Http")

(ns flight-tracker-ai.infrastructure.notification
  (:require [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System DateTimeOffset String]
           [System.Net.Http HttpClient StringContent]
           [System.Text Encoding]))

(defn send-discord-webhook [^HttpClient http-client webhook-url title description color fields url]
  (try
    (let [embed {:title title
                 :description description
                 :url url
                 :color color
                 :fields (mapv (fn [f] {:name (:name f) :value (:value f) :inline (boolean (:inline f))}) fields)
                 :footer {:text "FlightTrackerAI • 自動巡回通知"}
                 :timestamp (.ToString (DateTimeOffset/UtcNow) "o")}
          payload {:username "FlightTrackerAI"
                   :avatar_url "https://raw.githubusercontent.com/google/material-design-icons/master/png/maps/flight/materialicons/48dp/2x/baseline_flight_black_48dp.png"
                   :embeds [embed]}
          json-str (dto/to-json payload)
          content (StringContent. json-str Encoding/UTF8 "application/json")
          resp (.GetResult (.GetAwaiter (.PostAsync http-client ^String webhook-url content)))]
      (if (.IsSuccessStatusCode resp)
        {:ok nil}
        (let [err (.GetResult (.GetAwaiter (.ReadAsStringAsync (.Content resp))))]
          {:error (str "Discord Webhook 送信失敗 (HTTP " (int (.StatusCode resp)) "): " err)})))
    (catch Exception ex
      {:error (str "Discord Webhook 送信例外: " (.Message ex))})))

(defn send-slack-webhook [^HttpClient http-client webhook-url message]
  (try
    (let [payload {:text message}
          json-str (dto/to-json payload)
          content (StringContent. json-str Encoding/UTF8 "application/json")
          resp (.GetResult (.GetAwaiter (.PostAsync http-client ^String webhook-url content)))]
      (if (.IsSuccessStatusCode resp)
        {:ok nil}
        (let [err (.GetResult (.GetAwaiter (.ReadAsStringAsync (.Content resp))))]
          {:error (str "Slack Webhook 送信失敗 (HTTP " (int (.StatusCode resp)) "): " err)})))
    (catch Exception ex
      {:error (str "Slack Webhook 送信例外: " (.Message ex))})))

(defn dispatch-price-notification
  [^HttpClient http-client task-item lowest-offer price-change-percent is-target-met global-webhook]
  (let [task-webhook (:notification-webhook-url task-item)]
    (if (= task-webhook "DISABLED")
      {:ok nil}
      (let [target-webhook (or task-webhook global-webhook)]
        (if (or (nil? target-webhook) (str/blank? target-webhook))
          {:ok nil}
          (let [is-slack (.Contains ^String target-webhook "hooks.slack.com")
            price-str (str "¥" (.ToString (long (or (:price-jpy lowest-offer) 0)) "N0"))
            origin-str (domain/iata-code-value (:origin task-item))
            dest-str (domain/iata-code-value (:destination task-item))
            provider-str (domain/scraping-provider-to-string (:provider lowest-offer))
            airlines-str (or (:airlines-summary lowest-offer) "不明")
            booking-url (or (:booking-url lowest-offer) "https://www.google.com/travel/flights")]
        (if is-slack
          (let [headline (if is-target-met
                           (str "🎯 *【目標価格達成】* `" (:title task-item) "` (" origin-str " ➔ " dest-str ")")
                           (str "📉 *【価格下落検知】* `" (:title task-item) "` (" origin-str " ➔ " dest-str ")"))
                msg (str headline "\n"
                         "• 現在最安値: *" price-str "* (変動: " price-change-percent "%)\n"
                         "• 航空会社: " airlines-str " (" provider-str ")\n"
                         "• 予約リンク: <" booking-url "|予約ページを開く>")]
            (send-slack-webhook http-client target-webhook msg))
          ;; Discord
          (let [title (if is-target-met
                        (str "🎯 【目標価格達成】" (:title task-item))
                        (str "📉 【価格下落検知】" (:title task-item)))
                desc (str origin-str " ➔ " dest-str " の最安値が更新されました。")
                color (if is-target-met 0x10B981 0x3B82F6)
                fields [{:name "現在最安値" :value price-str :inline true}
                        {:name "価格変動" :value (str price-change-percent "%") :inline true}
                        {:name "航空会社" :value airlines-str :inline true}
                        {:name "提供元" :value provider-str :inline true}]]
            (send-discord-webhook http-client target-webhook title desc color fields booking-url)))))))))

