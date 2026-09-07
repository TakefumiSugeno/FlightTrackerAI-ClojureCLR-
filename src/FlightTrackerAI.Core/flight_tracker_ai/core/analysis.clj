(ns flight-tracker-ai.core.analysis
  (:import [System Math DateTimeOffset TimeSpan]))

(defn calculate-elapsed-minutes [^DateTimeOffset departure ^DateTimeOffset arrival]
  (let [span (.Subtract arrival departure)
        total-min (.TotalMinutes span)]
    (max 0 (int (Math/Round total-min)))))

(defn summarize-leg [segments]
  (let [sorted (sort-by :segment-index segments)
        flight-minutes (reduce + 0 (map #(or (:flight-duration-minutes %) 0) sorted))
        layover-minutes (reduce + 0 (map #(or (:layover-minutes-next %) 0) sorted))
        total-duration (+ flight-minutes layover-minutes)]
    {:total-flight-minutes flight-minutes
     :total-layover-minutes layover-minutes
     :total-duration-minutes total-duration}))

(defn find-lowest-offer [offers]
  (->> offers
       (filter (fn [o] (and (:price-jpy o) (> (:price-jpy o) 0))))
       (sort-by :price-jpy)
       first))

(defn calculate-price-change-percent [previous-price current-price]
  (if (or (nil? previous-price) (<= previous-price 0))
    0.0
    (let [diff (- (double current-price) (double previous-price))
          rate (* (/ diff (double previous-price)) 100.0)]
      (Math/Round (double rate) 1))))

(defn is-target-price-met [target-price-opt current-price]
  (if (and (number? target-price-opt) (> target-price-opt 0) (> current-price 0))
    (<= current-price target-price-opt)
    false))

(defn evaluate-price-opportunity [task latest-lowest]
  (let [current-price (or (:price-jpy latest-lowest) 0)
        target-met (is-target-price-met (:target-price-jpy task) current-price)
        last-price (:last-lowest-price-jpy task)
        price-change (if (and last-price (> last-price 0))
                       (calculate-price-change-percent last-price current-price)
                       0.0)
        should-notify (or target-met (<= price-change -5.0))]
    {:is-target-met target-met
     :price-change-percent price-change
     :should-notify should-notify}))

(defn humanize-error-message [err]
  (let [msg (str err)]
    (cond
      (re-find #"(?i)timeout" msg) "巡回タイムアウト（ページの読み込み・Cookie同意待機超過）。次回定期巡回で自動再試行されます。"
      (re-find #"(?i)press.*hold|challenge|kasada|perimeterx|captcha" msg) "セキュリティ認証チャレンジ（PRESS & HOLD）を検知しました。手動支援モードでの解除が必要です。"
      (re-find #"(?i)429|rate.*limit" msg) "スクレイピング先のアクセス制限（レートリミット）を検知しました。待機後に再試行します。"
      (re-find #"(?i)network|connection" msg) "ネットワーク接続エラーが発生しました。インターネット接続を確認してください。"
      :else (str "巡回エラー: " (subs msg 0 (min (count msg) 120))))))
