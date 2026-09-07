(ns flight-tracker-ai.core.validation
  (:require [flight-tracker-ai.core.domain :as domain])
  (:import [System DateOnly String]))

(defn validate-dates [today trip-type]
  (let [errors (atom [])
        today-str (if (instance? DateOnly today) (.ToString ^DateOnly today "yyyy-MM-dd") (str today))
        trip-kind (if (map? trip-type) (:kind trip-type) :round-trip)
        outbound (if (map? trip-type) (:outbound trip-type) (first trip-type))
        inbound (if (map? trip-type) (:inbound trip-type) (second trip-type))
        outbound-str (if (instance? DateOnly outbound) (.ToString ^DateOnly outbound "yyyy-MM-dd") (str outbound))
        inbound-str (if (and inbound (instance? DateOnly inbound)) (.ToString ^DateOnly inbound "yyyy-MM-dd") (when inbound (str inbound)))]

    ;; 往路出発日は本日以降
    (when (and outbound-str (< (compare outbound-str today-str) 0))
      (swap! errors conj (str "往路出発日 (" outbound-str ") は本日 (" today-str ") 以降を指定してください。")))

    ;; 復路出発日は往路出発日以降
    (when (and (= trip-kind :round-trip) inbound-str outbound-str)
      (when (< (compare inbound-str outbound-str) 0)
        (swap! errors conj (str "復路出発日 (" inbound-str ") は往路出発日 (" outbound-str ") 以降の日付を指定してください。"))))

    (if (empty? @errors)
      {:ok trip-type}
      {:error @errors})))

(defn validate-target-price [price-opt]
  (cond
    (nil? price-opt) {:ok nil}
    (and (number? price-opt) (> price-opt 0)) {:ok price-opt}
    :else {:error (str "目標価格は1円以上を指定してください (入力値: ¥" price-opt ")。")}))

(defn validate-check-interval [interval-hours]
  (if (and (number? interval-hours) (>= interval-hours 1) (<= interval-hours 168))
    {:ok interval-hours}
    {:error (str "巡回間隔は1時間〜168時間（7日間）の範囲で指定してください (入力値: " interval-hours "時間)。")}))

(defn validate-route [origin dest]
  (let [origin-code (domain/iata-code-value origin)
        dest-code (domain/iata-code-value dest)]
    (if (= origin-code dest-code)
      {:error (str "出発地と目的地に同一の空港 (" origin-code ") を指定することはできません。")}
      {:ok [origin dest]})))

(defn validate-task [today task-item]
  (let [route-res (validate-route (:origin task-item) (:destination task-item))]
    (if (:error route-res)
      route-res
      (let [dates-res (validate-dates today (:trip-type task-item))]
        (if (:error dates-res)
          {:error (clojure.string/join " " (:error dates-res))}
          (let [price-res (validate-target-price (:target-price-jpy task-item))]
            (if (:error price-res)
              price-res
              (validate-check-interval (:check-interval-hours task-item)))))))))
