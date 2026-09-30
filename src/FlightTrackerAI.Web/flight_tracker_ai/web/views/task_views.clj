(ns flight-tracker-ai.web.views.task-views
  (:require [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [clojure.string :as str]))

(defn render-flight-number-badge [flight-number]
  (when (and (string? flight-number) (not (str/blank? flight-number)))
    [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono bg-sky-950/80 text-sky-300 border border-sky-800/60 font-semibold"}
     [:i {:class "fa-solid fa-plane text-[10px]"}]
     [:span flight-number]]))

(defn render-task-form-controls
  "時間帯レンジ、経由地数、取得件数のUIフォームコントロール (モック準拠)"
  [field-values & [{:keys [trip-type-val]}]]
  (let [is-one-way? (= trip-type-val "OneWay")
        ob-range (or (:outboundTimeRange field-values) "Any")
        ib-range (or (:inboundTimeRange field-values) "Any")
        stops-val (or (:maxStops field-values) "OneStop")
        max-results (or (:maxResultsCount field-values) 10)]
    [:div {:class "space-y-4"}
     ;; 時間帯レンジ (出発・到着)
     [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
      [:div
       [:label {:class "block text-slate-400 font-medium mb-1 flex items-center gap-1.5"}
        [:i {:class "fa-regular fa-clock text-sky-400 text-xs"}]
        "出発時間 (レンジ)"]
       [:select {:name "outboundTimeRange"
                 :id "formOutboundTimeRange"
                 :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
        [:option (merge {:value "Any"} (when (= ob-range "Any") {:selected true})) "指定なし (終日)"]
        [:option (merge {:value "EarlyMorning"} (when (= ob-range "EarlyMorning") {:selected true})) "早朝 (00:00 - 06:00)"]
        [:option (merge {:value "Morning"} (when (= ob-range "Morning") {:selected true})) "午前 (06:00 - 12:00)"]
        [:option (merge {:value "Afternoon"} (when (= ob-range "Afternoon") {:selected true})) "午後 (12:00 - 18:00)"]
        [:option (merge {:value "Evening"} (when (= ob-range "Evening") {:selected true})) "夜間 (18:00 - 24:00)"]]]
      [:div {:id "inboundTimeRangeContainer"
             :style (if is-one-way? "display: none;" "")}
       [:label {:class "block text-slate-400 font-medium mb-1 flex items-center gap-1.5"}
        [:i {:class "fa-regular fa-clock text-indigo-400 text-xs"}]
        "到着時間 (レンジ)"]
       [:select {:name "inboundTimeRange"
                 :id "formInboundTimeRange"
                 :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
        [:option (merge {:value "Any"} (when (= ib-range "Any") {:selected true})) "指定なし (終日)"]
        [:option (merge {:value "EarlyMorning"} (when (= ib-range "EarlyMorning") {:selected true})) "早朝 (00:00 - 06:00)"]
        [:option (merge {:value "Morning"} (when (= ib-range "Morning") {:selected true})) "午前 (06:00 - 12:00)"]
        [:option (merge {:value "Afternoon"} (when (= ib-range "Afternoon") {:selected true})) "午後 (12:00 - 18:00)"]
        [:option (merge {:value "Evening"} (when (= ib-range "Evening") {:selected true})) "夜間 (18:00 - 24:00)"]]]]

     ;; 経由地数 & 取得件数
     [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
      [:div
       [:label {:class "block text-slate-400 font-medium mb-1"} "経由地数 (Max Stops)"]
       [:select {:name "maxStops"
                 :id "formMaxStops"
                 :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
        [:option (merge {:value "Any"} (when (= stops-val "Any") {:selected true})) "指定なし (すべての経由地)"]
        [:option (merge {:value "DirectOnly"} (when (= stops-val "DirectOnly") {:selected true})) "直行便のみ (0回)"]
        [:option (merge {:value "OneStop"} (when (= stops-val "OneStop") {:selected true})) "1箇所まで (1回以下の経由)"]]]
      [:div
       [:label {:class "block text-slate-400 font-medium mb-1"} "巡回時取得件数 (Max Results)"]
       [:input {:type "number"
                :name "maxResultsCount"
                :id "formMaxResultsCount"
                :value (str max-results)
                :min "1"
                :max "50"
                :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]]))
