(ns flight-tracker-ai.web.views.modals
  (:require [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System DateTimeOffset TimeSpan DateOnly]))

(defn- modal-backdrop
  ([title-str content] (modal-backdrop title-str content false))
  ([title-str content is-input-form]
   [:div {:id "active-modal"
          :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
          :onclick (if is-input-form
                     "if (event.target === this) { const form = this.querySelector('form'); if (!window.isFormDirty || !window.isFormDirty(form)) { closeCurrentModal(); } else { if (confirm('入力内容が変更されています。破棄して閉じますか？')) closeCurrentModal(); } }"
                     "if (event.target === this) closeCurrentModal();")}
    [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-lg rounded-2xl shadow-2xl p-6 space-y-4 cursor-default text-slate-100 max-h-[90vh] flex flex-col overflow-hidden"
           :onclick "event.stopPropagation();"}
     [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
      [:h3 {:class "text-sm font-bold text-white flex items-center space-x-2"}
       [:i {:data-lucide "plus-circle" :class "w-4 h-4 text-sky-400"}]
       [:span title-str]]
      [:button {:type "button"
                :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
                :onclick "closeCurrentModal()"}
       [:i {:data-lucide "x" :class "w-4 h-4"}]]]
     [:div {:class "overflow-y-auto space-y-4 flex-1 pr-1"}
      content]]]))

(def airports-datalist
  [:datalist {:id "airportsList"}
   [:option {:value "HND - 東京(羽田)"}]
   [:option {:value "NRT - 東京(成田)"}]
   [:option {:value "KIX - 大阪(関西)"}]
   [:option {:value "ITM - 大阪(伊丹)"}]
   [:option {:value "FUK - 福岡"}]
   [:option {:value "CTS - 札幌(新千歳)"}]
   [:option {:value "CDG - パリ(シャルル・ド・ゴール)"}]
   [:option {:value "LHR - ロンドン(ヒースロー)"}]
   [:option {:value "LAX - ロサンゼルス"}]
   [:option {:value "SFO - サンフランシスコ"}]
   [:option {:value "HNL - ホノルル"}]
   [:option {:value "BKK - バンコク(スワンナプーム)"}]
   [:option {:value "SIN - シンガポール(チャンギ)"}]
   [:option {:value "TPE - 台北(桃園)"}]])

(defn render-quick-note-modal [task-id current-note route-title]
  (h/render-html
    [:div {:id "quickNoteModal"
           :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
           :onclick "if (event.target === this) { const form = this.querySelector('form'); if (!window.isFormDirty || !window.isFormDirty(form)) { closeCurrentModal(); } else { if (confirm('入力内容が変更されています。破棄して閉じますか？')) closeCurrentModal(); } }"}
     [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-md rounded-2xl shadow-2xl p-6 space-y-4 text-slate-100 cursor-default"
            :onclick "event.stopPropagation();"}
      [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
       [:h3 {:class "text-sm font-bold text-white flex items-center space-x-2"}
        [:i {:data-lucide "file-text" :class "w-4 h-4 text-sky-400"}]
        [:span (str "タスクのメモ・要望編集: " (or route-title (str task-id)))]]
       [:button {:type "button"
                 :onclick "closeCurrentModal()"
                 :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"}
        [:i {:data-lucide "x" :class "w-4 h-4"}]]]
      [:form {:hx-patch (str "/api/tasks/" task-id "/notes")
              :hx-target "#modal-container"
              :hx-swap "innerHTML"
              :class "space-y-3 text-xs"}
       [:div
        [:label {:class "block text-slate-300 font-semibold mb-1"}
         "ユーザーメモ / 要望・制約（Markdown・箇条書き可）"]
        [:textarea {:id "quickNoteText"
                    :name "notes"
                    :rows "4"
                    :placeholder "例: - 家族旅行のため荷物預けあり必須\n- 現地午前着希望"
                    :class "w-full bg-slate-900 border border-slate-700 rounded-lg p-3 text-white font-mono text-xs focus:ring-1 focus:ring-sky-500 leading-relaxed"}
         (or current-note "")]]
       [:p {:class "text-[11px] text-slate-400"}
        "※ 保存するとカードおよび一覧リストのメモ表示が即時に更新されます。"]
       [:div {:class "flex justify-end space-x-2 pt-2 border-t border-slate-800"}
        [:button {:type "button"
                  :onclick "closeCurrentModal()"
                  :class "px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"}
         "キャンセル"]
        [:button {:type "submit"
                  :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold transition"}
         "メモを保存"]]]]]))

(defn render-delete-modal [task-id route-str]
  (h/render-html
    [:div {:id "deleteModal"
           :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
           :onclick "if (event.target === this) closeCurrentModal();"}
     [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-sm rounded-2xl shadow-2xl p-6 space-y-4 cursor-default text-slate-100"
            :onclick "event.stopPropagation();"}
      [:div {:class "w-12 h-12 rounded-full bg-rose-950 text-rose-400 flex items-center justify-center mx-auto border border-rose-800"}
       [:i {:data-lucide "alert-triangle" :class "w-6 h-6"}]]
      [:div {:class "text-center"}
       [:h3 {:class "text-sm font-bold text-white"} "監視タスクを削除しますか？"]
       [:p {:id "deleteTaskRoute" :class "text-xs text-rose-300 font-semibold mt-1"} route-str]
       [:p {:class "text-xs text-slate-400 mt-2"} "このタスクおよび蓄積された価格履歴データがすべて完全に削除されます。"]]
      [:div {:class "grid grid-cols-2 gap-2 pt-2 border-t border-slate-800"}
       [:button {:type "button"
                 :onclick "closeCurrentModal()"
                 :class "px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-medium transition"}
        "キャンセル"]
       [:button {:type "button"
                 :hx-delete (str "/api/tasks/" task-id)
                 :hx-target "#modal-container"
                 :class "px-3 py-2 bg-rose-600 hover:bg-rose-500 text-white rounded-lg text-xs font-semibold transition"}
        "削除する"]]]]))

(defn render-task-modal
  ([task-opt initial-params] (render-task-modal task-opt initial-params nil))
  ([task-opt initial-params error-msg]
   (let [is-edit (some? task-opt)
         title (if is-edit "監視タスク設定変更" "新規フライト監視タスク登録")
         trip-type (cond
                     task-opt (if (= (:kind (:trip-type task-opt)) :one-way) "OneWay" "RoundTrip")
                     (:tripType initial-params) (:tripType initial-params)
                     :else "RoundTrip")
         is-one-way (= trip-type "OneWay")
         origin-val (or (when task-opt (domain/iata-code-value (:origin task-opt)))
                        (:origin initial-params) "HND - 東京(羽田)")
         dest-val (or (when task-opt (domain/iata-code-value (:destination task-opt)))
                      (:destination initial-params) "CDG - パリ(シャルル・ド・ゴール)")
         task-title (or (when task-opt (:title task-opt))
                        (:title initial-params) "")
         outbound-val (or (when task-opt
                            (let [trip (:trip-type task-opt)]
                              (when (:outbound trip) (.ToString ^DateOnly (:outbound trip) "yyyy-MM-dd"))))
                          (:outboundDate initial-params) "2026-05-01")
         inbound-val (or (when task-opt
                           (let [trip (:trip-type task-opt)]
                             (when (:inbound trip) (.ToString ^DateOnly (:inbound trip) "yyyy-MM-dd"))))
                         (:inboundDate initial-params) "2026-05-08")
         max-stops-val (cond
                         task-opt (cond
                                    (= (:max-stops task-opt) :direct-only) "DirectOnly"
                                    (= (:max-stops task-opt) :one-stop) "1"
                                    :else "Any")
                         (:maxStops initial-params) (:maxStops initial-params)
                         :else "1")
         interval-val (or (when task-opt (str (:check-interval-hours task-opt)))
                          (:checkIntervalHours initial-params) "12")
         target-price-val (or (when task-opt (:target-price-jpy task-opt))
                              (:targetPriceJpy initial-params) "160000")
         airlines-val (or (when task-opt (:last-lowest-airlines task-opt))
                          (:preferredAirlines initial-params) "")
         notes-val (or (when task-opt (:user-notes task-opt))
                       (:notes initial-params) "")
         use-default-webhook? true]
     (h/render-html
       [:div {:id "active-modal"
              :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
              :onclick "if (event.target === this) { const form = this.querySelector('form'); if (!window.isFormDirty || !window.isFormDirty(form)) { closeCurrentModal(); } else { if (confirm('入力内容が変更されています。破棄して閉じますか？')) closeCurrentModal(); } }"}
        [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-lg rounded-2xl shadow-2xl p-6 space-y-4 cursor-default text-slate-100 max-h-[90vh] flex flex-col overflow-hidden"
               :onclick "event.stopPropagation();"}
         [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
          [:h3 {:class "text-sm font-bold text-white flex items-center space-x-2"}
           [:i {:data-lucide (if is-edit "edit-3" "plus-circle") :class "w-4 h-4 text-sky-400"}]
           [:span title]]
          [:button {:type "button"
                    :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
                    :onclick "closeCurrentModal()"}
           [:i {:data-lucide "x" :class "w-4 h-4"}]]]

         [:form {:id "taskForm"
                 :hx-post (if is-edit (str "/api/tasks/" (:id task-opt)) "/api/tasks")
                 :hx-target "#modal-container"
                 :hx-swap "innerHTML"
                 :class "space-y-4 text-xs overflow-y-auto pr-1 flex-1"}
          (when (and error-msg (not (str/blank? error-msg)))
            [:div {:class "p-3 rounded-lg bg-rose-950/60 border border-rose-800/80 text-xs text-rose-300 flex items-center gap-2"}
             [:i {:data-lucide "alert-circle" :class "w-4 h-4 text-rose-400"}]
             [:span error-msg]])

          ;; 旅行タイプ切り替え
          [:div
           [:label {:class "block text-slate-400 font-medium mb-1"} "旅行タイプ"]
           [:div {:class "grid grid-cols-2 gap-2 bg-slate-900 p-1 rounded-lg border border-slate-800"}
            [:button {:type "button"
                      :id "btnRoundTrip"
                      :class (if-not is-one-way "py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition" "py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                      :onclick "document.getElementById('formTripType').value='RoundTrip'; document.getElementById('btnRoundTrip').className='py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnOneWay').className='py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='block';"}
             "往復"]
            [:button {:type "button"
                      :id "btnOneWay"
                      :class (if is-one-way "py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition" "py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                      :onclick "document.getElementById('formTripType').value='OneWay'; document.getElementById('btnOneWay').className='py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnRoundTrip').className='py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='none';"}
             "片道"]]
           [:input {:type "hidden" :name "tripType" :id "formTripType" :value trip-type}]
           [:input {:type "hidden" :name "title" :value task-title}]]

          ;; 出発地・目的地
          [:div {:class "grid grid-cols-2 gap-3"}
           [:div
            [:label {:class "block text-slate-400 font-medium mb-1"} "出発地 (都市名またはIATA)"]
            [:input {:list "airportsList" :type "text" :name "origin" :id "formOrigin"
                     :value origin-val :required true
                     :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white focus:ring-1 focus:ring-sky-500"}]]
           [:div
            [:label {:class "block text-slate-400 font-medium mb-1"} "目的地 (都市名またはIATA)"]
            [:input {:list "airportsList" :type "text" :name "destination" :id "formDestination"
                     :value dest-val :required true
                     :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white focus:ring-1 focus:ring-sky-500"}]]]

          airports-datalist

          ;; 日程
          [:div {:class "grid grid-cols-2 gap-3"}
           [:div
            [:label {:class "block text-slate-400 font-medium mb-1"} "往路出発日"]
            [:input {:type "date" :name "outboundDate" :id "formOutboundDate" :value outbound-val :required true
                     :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}]]
           [:div {:id "inboundDateContainer" :style (if is-one-way "display: none;" "display: block;")}
            [:label {:class "block text-slate-400 font-medium mb-1"} "復路出発日"]
            [:input {:type "date" :name "inboundDate" :id "formInboundDate" :value inbound-val
                     :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}]]]

          ;; 乗継 & 巡回間隔
          [:div {:class "grid grid-cols-2 gap-3"}
           [:div
            [:label {:class "block text-slate-400 font-medium mb-1"} "許容乗継回数 (Max Stops)"]
            [:select {:name "maxStops" :id "formMaxStops"
                      :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}
             [:option {:value "Any" :selected (= max-stops-val "Any")} "乗継制限なし (最安重視・推奨)"]
             [:option {:value "1" :selected (= max-stops-val "1")} "1回乗継まで"]
             [:option {:value "DirectOnly" :selected (= max-stops-val "DirectOnly")} "直行便のみ (0回乗継)"]]]
           [:div
            [:label {:class "block text-slate-400 font-medium mb-1"} "巡回間隔"]
            [:select {:name "checkIntervalHours" :id "formInterval"
                      :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}
             [:option {:value "default"} "全体設定に従う (現在 12h)"]
             [:option {:value "3" :selected (= interval-val "3")} "3時間ごと"]
             [:option {:value "6" :selected (= interval-val "6")} "6時間ごと"]
             [:option {:value "12" :selected (= interval-val "12")} "12時間ごと"]
             [:option {:value "24" :selected (= interval-val "24")} "24時間ごと"]]]]

          ;; 目標アラート価格 & 優先航空会社
          [:div {:class "grid grid-cols-2 gap-3"}
           [:div
            [:label {:class "block text-slate-400 font-medium mb-1"} "目標アラート価格 (JPY)"]
            [:input {:type "number" :name "targetPriceJpy" :id "formTargetPrice"
                     :value (str target-price-val) :placeholder "例: 160000"
                     :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}]]
           [:div
            [:label {:class "block text-slate-400 font-medium mb-1"} "優先航空会社 (任意)"]
            [:input {:type "text" :name "preferredAirlines" :id "formAirlines"
                     :value airlines-val :placeholder "例: ANA, JAL, エールフランス"
                     :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}]]]

          ;; 構造化メモ
          [:div
           [:label {:class "block text-slate-400 font-medium mb-1"} "構造化メモ / 要望・制約（任意）"]
           [:textarea {:name "userNotes" :id "formNotes" :rows "2"
                       :placeholder "例: - 荷物制限なし希望\n- ホテル最寄り空港優先"
                       :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white font-mono text-xs focus:ring-1 focus:ring-sky-500"}
            notes-val]]

          ;; Webhook チェックボックス
          [:div {:class "space-y-1.5"}
           [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
            [:input {:type "checkbox" :name "useDefaultWebhook" :value "1"
                     :checked use-default-webhook?
                     :class "rounded border-slate-700 text-sky-600 focus:ring-sky-500 bg-slate-900"}]
            [:span "デフォルトの Discord Webhook に通知する"]]]

          [:div {:class "pt-3 border-t border-slate-800 flex justify-end space-x-2"}
           [:button {:type "button"
                     :onclick "closeCurrentModal()"
                     :class "px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"}
            "キャンセル"]
           [:button {:type "submit"
                     :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold transition"}
            (if is-edit "タスク設定を更新" "登録して巡回開始")]]]]]))))

(defn render-settings-modal [settings]
  (h/render-html
    [:div {:id "active-modal"
           :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
           :onclick "if (event.target === this) { const form = this.querySelector('form'); if (!window.isFormDirty || !window.isFormDirty(form)) { closeCurrentModal(); } else { if (confirm('入力内容が変更されています。破棄して閉じますか？')) closeCurrentModal(); } }"}
     [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-lg rounded-2xl shadow-2xl p-6 space-y-4 cursor-default text-slate-100 max-h-[90vh] flex flex-col overflow-hidden"
            :onclick "event.stopPropagation();"}
      [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
       [:h3 {:class "text-sm font-bold text-white flex items-center space-x-2"}
        [:i {:data-lucide "sliders" :class "w-4 h-4 text-sky-400"}]
        [:span "システム全体設定 (Global Settings)"]]
       [:button {:type "button"
                 :onclick "closeCurrentModal()"
                 :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"}
        [:i {:data-lucide "x" :class "w-4 h-4"}]]]
      [:form {:hx-post "/api/settings"
              :hx-target "#modal-container"
              :class "space-y-4 text-xs overflow-y-auto pr-1 flex-1"}
       [:div
        [:label {:class "block text-slate-300 font-semibold mb-1"} "全体デフォルト巡回間隔 (Default Check Interval)"]
        [:select {:name "defaultCheckIntervalHours"
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}
         [:option {:value "3" :selected (= (:default-check-interval-hours settings) 3)} "3時間ごと"]
         [:option {:value "6" :selected (= (:default-check-interval-hours settings) 6)} "6時間ごと"]
         [:option {:value "12" :selected (or (= (:default-check-interval-hours settings) 12) (nil? (:default-check-interval-hours settings)))} "12時間ごと (推奨・初期値)"]
         [:option {:value "24" :selected (= (:default-check-interval-hours settings) 24)} "24時間ごと"]]
        [:p {:class "text-[11px] text-slate-400 mt-1"}
         "※ タスク登録時に個別の巡回間隔を指定しなかった場合、この間隔で定期巡回されます。"]]
       [:div
        [:label {:class "block text-slate-300 font-semibold mb-1"} "デフォルト Discord Webhook URL"]
        [:div {:class "flex gap-2"}
         [:input {:type "url" :name "defaultWebhookUrl"
                  :value (or (:default-webhook-url settings) "")
                  :placeholder "https://discord.com/api/webhooks/123456789/xxxxxx"
                  :class "flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}]
         [:button {:type "button"
                   :onclick "showToast('テスト通知を送信しました', true)"
                   :class "px-3 py-2 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-lg text-sky-300 font-medium transition"}
          "テスト送信"]]]
       [:div
        [:label {:class "block text-slate-300 font-semibold mb-1"} "OpenRouter API Key (AI支援機能用)"]
        [:input {:type "password" :name "openRouterApiKey"
                 :value (or (:openrouter-api-key settings) "")
                 :placeholder "sk-or-v1-xxxxxxxxxxxxxxxxxxxx"
                 :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white"}]]
       [:div
        [:label {:class "block text-slate-300 font-semibold mb-1"} "スクレイピングプロバイダー設定"]
        [:div {:class "p-3 bg-slate-900 rounded-lg border border-slate-800 space-y-2"}
         [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
          [:input {:type "checkbox" :name "enableGoogleFlights" :value "1"
                   :checked (boolean (:enable-google-flights settings))
                   :class "rounded border-slate-700 text-sky-600 bg-slate-950"}]
          [:span "Google Flights 巡回を有効化"]]
         [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
          [:input {:type "checkbox" :name "enableSkyscanner" :value "1"
                   :checked (boolean (:enable-skyscanner settings))
                   :class "rounded border-slate-700 text-sky-600 bg-slate-950"}]
          [:span "Skyscanner 巡回を有効化"]]]]
       [:div {:class "pt-3 border-t border-slate-800 flex justify-end space-x-2"}
        [:button {:type "button"
                  :onclick "closeCurrentModal()"
                  :class "px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"}
         "閉じる"]
        [:button {:type "submit"
                  :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold transition"}
         "設定を保存"]]]]]))

(defn render-timeline-modal [task-item latest-offers history]
  (let [trip (:trip-type task-item)
        is-round (= (:kind trip) :round-trip)
        origin-str (domain/iata-code-value (:origin task-item))
        dest-str (domain/iata-code-value (:destination task-item))
        target-price (:target-price-jpy task-item)
        history-json (dto/to-json (or history []))
        summary-text (:ai-analysis-summary task-item)]
    (h/render-html
      [:div {:id "timelineModal"
             :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
             :onclick "if (event.target === this) closeCurrentModal();"}
       [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-4xl max-h-[90vh] rounded-2xl shadow-2xl overflow-hidden flex flex-col cursor-default text-slate-100"
              :onclick "event.stopPropagation();"}
        ;; Header
        [:div {:class "px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-900"}
         [:div {:class "flex items-center space-x-3"}
          [:div {:class "p-2 bg-sky-600/20 text-sky-400 rounded-lg"}
           [:i {:data-lucide "trending-down" :class "w-5 h-5"}]]
          [:div
           [:h3 {:class "text-base font-bold text-white"}
            (str origin-str " → " dest-str " (" (:title task-item) ")")]
           [:p {:class "text-xs text-slate-400"}
            (str (if is-round "往復旅程" "片道旅程") " • 目標価格: " (if target-price (str "¥" (.ToString (long target-price) "N0")) "未設定"))]]]
         [:button {:type "button"
                   :onclick "closeCurrentModal()"
                   :class "text-slate-400 hover:text-white p-2 rounded-lg hover:bg-slate-800 transition"}
          [:i {:data-lucide "x" :class "w-5 h-5"}]]]

        [:div {:class "p-6 overflow-y-auto space-y-6 flex-1"}
         ;; AI Advice Box
         (when summary-text
           [:div {:class "p-4 rounded-xl bg-slate-900 border border-slate-800 flex items-start space-x-3"}
            [:div {:class "p-1.5 bg-sky-600 rounded text-white mt-0.5"}
             [:i {:data-lucide "bot" :class "w-4 h-4"}]]
            [:div
             [:h4 {:class "text-xs font-bold text-sky-400"} "AI 買い時診断サマリー (キャッシュ保持中)"]
             [:p {:class "text-xs text-slate-300 mt-0.5 leading-relaxed"} summary-text]]])

         ;; Timeline Section
         [:div {:class "bg-slate-900/60 p-4 rounded-xl border border-slate-800 space-y-4"}
          [:div {:class "flex items-center justify-between"}
           [:span {:class "text-xs font-bold text-slate-300"} "旅程タイムライン (複数航空会社 & 乗継詳細)"]
           [:span {:class "text-xs text-slate-400 font-mono"}
            (if (= (:max-stops task-item) :direct-only) "直行便のみ" "乗継便含む")]]

          ;; Outbound Leg
          [:div {:class "space-y-2"}
           [:div {:class "flex items-center justify-between text-xs font-bold text-slate-300"}
            [:div {:class "flex items-center space-x-2"}
             [:span {:class "px-2 py-0.5 rounded bg-sky-950 text-sky-300 border border-sky-800"} "往路"]
             [:span (str (.ToString ^DateOnly (:outbound trip) "yyyy年MM月dd日"))]]
            [:span {:class "text-slate-400 font-normal"} "出発地 ➔ 目的地"]]
           [:div {:class "bg-slate-950 p-3 rounded-lg border border-slate-800 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2"}
            [:div {:class "flex items-center space-x-3"}
             [:div {:class "font-bold text-white"} origin-str]
             [:i {:data-lucide "arrow-right" :class "w-3.5 h-3.5 text-slate-500"}]
             [:div {:class "font-bold text-white"} dest-str]]
            [:div {:class "flex items-center space-x-3 text-slate-300"}
             [:span {:class "px-2 py-0.5 rounded bg-slate-900 border border-slate-700 font-medium"}
              (or (:last-lowest-airlines task-item) "航空会社各社")]]]]

          ;; Inbound Leg (if round trip)
          (when is-round
            [:div {:class "space-y-2 pt-2 border-t border-slate-800/80"}
             [:div {:class "flex items-center justify-between text-xs font-bold text-slate-300"}
              [:div {:class "flex items-center space-x-2"}
               [:span {:class "px-2 py-0.5 rounded bg-indigo-950 text-indigo-300 border border-indigo-800"} "復路"]
               [:span (str (.ToString ^DateOnly (:inbound trip) "yyyy年MM月dd日"))]]
              [:span {:class "text-slate-400 font-normal"} "目的地 ➔ 出発地"]]
             [:div {:class "bg-slate-950 p-3 rounded-lg border border-slate-800 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2"}
              [:div {:class "flex items-center space-x-3"}
               [:div {:class "font-bold text-white"} dest-str]
               [:i {:data-lucide "arrow-right" :class "w-3.5 h-3.5 text-slate-500"}]
               [:div {:class "font-bold text-white"} origin-str]]
              [:div {:class "flex items-center space-x-3 text-slate-300"}
               [:span {:class "px-2 py-0.5 rounded bg-slate-900 border border-slate-700 font-medium"} "復路便"]]]])]

         ;; Chart Section
         [:div {:class "bg-slate-900/60 p-4 rounded-xl border border-slate-800"}
          [:div {:class "flex items-center justify-between mb-2"}
           [:span {:class "text-xs font-bold text-slate-300"} "最安値 推移チャート (JPY)"]
           [:div {:class "flex items-center space-x-4 text-xs font-medium"}
            [:span {:class "text-emerald-400 flex items-center space-x-1"}
             [:span {:class "w-2 h-2 rounded-full bg-emerald-400"}]
             [:span "Google Flights"]]
            [:span {:class "text-sky-400 flex items-center space-x-1"}
             [:span {:class "w-2 h-2 rounded-full bg-sky-400"}]
             [:span "Skyscanner"]]
            (when target-price
              [:span {:class "text-rose-400 flex items-center space-x-1"}
               [:span {:class "w-2 h-2 border-t-2 border-dashed border-rose-400"}]
               [:span (str "目標価格 (¥" (.ToString (long target-price) "N0") ")")]])]]
          [:div {:class "h-52"}
           [:canvas {:id "priceChart"}]]
          [:script (h/raw (str "
            (function() {
              const ctx = document.getElementById('priceChart');
              if (!ctx) return;
              const history = " history-json ";
              const labels = history.map(h => (h.RecordedAtJst || '').substring(5, 16));
              const gfPrices = history.map(h => h.GoogleFlightsPriceJpy);
              const ssPrices = history.map(h => h.SkyscannerPriceJpy);
              new Chart(ctx, {
                type: 'line',
                data: {
                  labels: labels.length ? labels : ['現在'],
                  datasets: [
                    {
                      label: 'Google Flights',
                      data: gfPrices.length ? gfPrices : [" (or (:last-lowest-price-jpy task-item) 0) "],
                      borderColor: '#10b981',
                      backgroundColor: 'rgba(16, 185, 129, 0.1)',
                      tension: 0.2
                    },
                    {
                      label: 'Skyscanner',
                      data: ssPrices.length ? ssPrices : [" (or (:last-lowest-price-jpy task-item) 0) "],
                      borderColor: '#0284c7',
                      backgroundColor: 'rgba(2, 132, 199, 0.1)',
                      tension: 0.2
                    }
                  ]
                },
                options: {
                  responsive: true,
                  maintainAspectRatio: false,
                  scales: {
                    y: {
                      ticks: { color: '#94a3b8', callback: v => '¥' + v.toLocaleString() },
                      grid: { color: '#334155' }
                    },
                    x: {
                      ticks: { color: '#94a3b8' },
                      grid: { color: '#334155' }
                    }
                  },
                  plugins: { legend: { display: false } }
                }
              });
            })();
          "))]]

         ;; Multi-Flight Offer Table
         [:div
          [:div {:class "flex items-center justify-between mb-2"}
           [:h4 {:class "text-xs font-bold text-white flex items-center space-x-1.5"}
            [:i {:data-lucide "list" :class "w-3.5 h-3.5 text-sky-400"}]
            [:span "同日・同区間の候補便一覧"]]
           [:span {:class "text-xs text-slate-400"} (str "取得件数: " (count latest-offers) "件")]]
          (if (empty? latest-offers)
            [:p {:class "text-xs text-slate-500 py-4 text-center"} "巡回による候補便データはまだ記録されていません。"]
            [:div {:class "overflow-x-auto border border-slate-800 rounded-lg"}
             [:table {:class "w-full text-left text-xs text-slate-300 min-w-max"}
              [:thead {:class "bg-slate-900 text-slate-400 font-semibold border-b border-slate-800 whitespace-nowrap"}
               [:tr
                [:th {:class "px-3 py-2"} "ソース"]
                [:th {:class "px-3 py-2"} "航空会社 (往復/乗継)"]
                [:th {:class "px-3 py-2"} "乗継"]
                [:th {:class "px-3 py-2"} "所要時間"]
                [:th {:class "px-3 py-2 text-right"} "価格 (総額)"]
                [:th {:class "px-3 py-2 text-center"} "リンク"]]]
              [:tbody {:class "divide-y divide-slate-800"}
               (for [o latest-offers]
                 [:tr {:class "hover:bg-slate-900/50"}
                  [:td {:class "px-3 py-2"}
                   [:span {:class (if (= (:provider o) :google-flights)
                                    "px-2 py-0.5 rounded bg-emerald-950 border border-emerald-800 text-emerald-300 font-semibold text-[10px]"
                                    "px-2 py-0.5 rounded bg-sky-950 border border-sky-800 text-sky-300 font-semibold text-[10px]")}
                    (domain/scraping-provider-to-string (:provider o))]]
                  [:td {:class "px-3 py-2 font-semibold text-white"} (:airlines-summary o)]
                  [:td {:class "px-3 py-2 text-amber-300 font-medium"} (str (:stops-count o) "回乗継")]
                  [:td {:class "px-3 py-2"} (str (:total-duration-minutes o) "分")]
                  [:td {:class "px-3 py-2 text-right font-bold text-emerald-400 font-mono"}
                   (str "¥" (.ToString (long (:price-jpy o)) "N0"))]
                  [:td {:class "px-3 py-2 text-center"}
                   (if-let [u (:booking-url o)]
                     [:a {:href u :target "_blank" :class "text-sky-400 hover:underline text-xs"} "予約リンク ↗"]
                     "-")]])]]])]]

        ;; Footer
        [:div {:class "px-6 py-4 border-t border-slate-800 flex justify-end bg-slate-950"}
         [:button {:type "button"
                   :onclick "closeCurrentModal()"
                   :class "px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-lg font-medium transition"}
          "閉じる"]]]])))

(defn render-logs-modal [logs log-file-path]
  (let [log-content (str/join "\n" (or logs []))]
    (h/render-html
      [:div {:id "active-modal"
             :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
             :onclick "if (event.target === this) closeCurrentModal();"}
       [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-2xl rounded-2xl shadow-2xl p-6 space-y-4 cursor-default text-slate-100 max-h-[90vh] flex flex-col"
              :onclick "event.stopPropagation();"}
        [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
         [:h3 {:class "text-sm font-bold text-white flex items-center space-x-2"}
          [:i {:data-lucide "terminal" :class "w-4 h-4 text-sky-400"}]
          [:span "システム実行ログ (Debug & Activity Logs)"]]
         [:button {:type "button"
                   :onclick "closeCurrentModal()"
                   :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"}
          [:i {:data-lucide "x" :class "w-4 h-4"}]]]
        [:div {:class "space-y-4 text-xs flex-1 overflow-hidden flex flex-col"}
         [:div {:class "flex items-center justify-between text-slate-400"}
          [:span {:class "font-mono text-[11px] truncate max-w-xs"} (str "ログ: " (or log-file-path "app.log"))]
          [:span {:class "text-[11px] font-medium text-sky-400"} (str "最新 " (count (or logs [])) " 行を表示中")]]
         [:pre {:id "logContentPre"
                :class "w-full flex-1 bg-slate-900 border border-slate-800 rounded-xl p-3 font-mono text-[11px] text-slate-200 overflow-y-auto leading-relaxed whitespace-pre-wrap select-all"}
          log-content]
         [:div {:class "flex items-center justify-between pt-2 border-t border-slate-800"}
          [:div {:class "flex items-center space-x-2"}
           [:button {:type "button"
                     :class "px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium flex items-center gap-1.5 transition"
                     :hx-get "/api/logs/modal"
                     :hx-target "#modal-container"}
            [:i {:data-lucide "refresh-cw" :class "w-3.5 h-3.5"}]
            "再読込"]
           [:button {:type "button"
                     :class "px-3 py-1.5 bg-sky-600/30 hover:bg-sky-600 text-sky-200 hover:text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition border border-sky-500/40"
                     :onclick "navigator.clipboard.writeText(document.getElementById('logContentPre').innerText).then(() => showToast('ログをクリップボードにコピーしました（AI共有用）', true));"}
            [:i {:data-lucide "copy" :class "w-3.5 h-3.5"}]
            "ログをコピー (AI共有用)"]]
          [:button {:type "button"
                    :class "px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"
                    :onclick "closeCurrentModal()"}
           "閉じる"]]]]])))

(defn render-standalone-new-task-page [params]
  (let [origin (or (:origin params) "")
        destination (or (:destination params) "")
        outbound-date (or (:outboundDate params) "")
        inbound-date (or (:inboundDate params) "")
        trip-type (or (:tripType params) "RoundTrip")
        max-stops (or (:maxStops params) "Any")
        max-price-jpy (or (:maxPriceJpy params) "")
        notes (or (:notes params) "")
        is-one-way (= trip-type "OneWay")]
    [:div {:class "max-w-2xl mx-auto py-8 px-4"}
     [:div {:class "bg-slate-950 rounded-2xl shadow-2xl border border-slate-800 p-6 md:p-8 space-y-6 text-slate-100"}
      [:div {:class "flex items-center justify-between border-b border-slate-800 pb-4"}
       [:div {:class "flex items-center gap-3"}
        [:span {:class "w-10 h-10 rounded-xl bg-sky-600/20 border border-sky-500/30 flex items-center justify-center text-sky-400"}
         [:i {:data-lucide "plane" :class "w-5 h-5"}]]
        [:div
         [:h2 {:class "text-lg font-bold text-white"} "新規フライト監視タスク登録"]
         [:p {:class "text-xs text-slate-400"} "AI解析によって抽出された条件をご確認・調整の上、登録してください。"]]]
       [:a {:href "/"
            :class "text-xs text-slate-400 hover:text-white px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 transition flex items-center gap-1.5"}
        [:i {:data-lucide "arrow-left" :class "w-3.5 h-3.5"}]
        "ダッシュボードへ戻る"]]

      [:form {:action "/api/tasks/standalone"
              :method "post"
              :class "space-y-5 text-xs"}
       [:div
        [:label {:class "block text-slate-400 font-medium mb-1"} "旅行タイプ"]
        [:div {:class "grid grid-cols-2 gap-2 bg-slate-900 p-1 rounded-lg border border-slate-800"}
         [:button {:type "button"
                   :id "btnRoundTrip"
                   :class (if-not is-one-way "py-2 text-center rounded-md font-medium bg-sky-600 text-white transition" "py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                   :onclick "document.getElementById('formTripType').value='RoundTrip'; document.getElementById('btnRoundTrip').className='py-2 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnOneWay').className='py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='block';"}
          "往復"]
         [:button {:type "button"
                   :id "btnOneWay"
                   :class (if is-one-way "py-2 text-center rounded-md font-medium bg-sky-600 text-white transition" "py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                   :onclick "document.getElementById('formTripType').value='OneWay'; document.getElementById('btnOneWay').className='py-2 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnRoundTrip').className='py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='none';"}
          "片道"]]
        [:input {:type "hidden" :name "tripType" :id "formTripType" :value trip-type}]]

       [:div {:class "grid grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "出発地 (都市名またはIATA)"]
         [:input {:list "airportsList" :type "text" :name "origin" :value origin :required true
                  :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"}]]
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "目的地 (都市名またはIATA)"]
         [:input {:list "airportsList" :type "text" :name "destination" :value destination :required true
                  :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"}]]]

       airports-datalist

       [:div {:class "grid grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "往路出発日"]
         [:input {:type "date" :name "outboundDate" :value outbound-date :required true
                  :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"}]]
        [:div {:id "inboundDateContainer" :style (if is-one-way "display: none;" "display: block;")}
         [:label {:class "block text-slate-400 font-medium mb-1"} "復路出発日"]
         [:input {:type "date" :name "inboundDate" :value inbound-date
                  :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"}]]]

       [:div {:class "grid grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "許容乗継回数"]
         [:select {:name "maxStops" :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"}
          [:option {:value "Any" :selected (= max-stops "Any")} "乗継制限なし"]
          [:option {:value "1" :selected (= max-stops "1")} "1回乗継まで"]
          [:option {:value "DirectOnly" :selected (= max-stops "DirectOnly")} "直行便のみ"]]]
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "目標予算 (JPY)"]
         [:input {:type "number" :name "maxPriceJpy" :value max-price-jpy
                  :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"}]]]

       [:div
        [:label {:class "block text-slate-400 font-medium mb-1"} "ユーザーメモ / 要望・制約"]
        [:textarea {:name "notes" :rows "3"
                    :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"}
         notes]]

       [:div {:class "flex justify-end gap-3 pt-4 border-t border-slate-800"}
        [:a {:href "/" :class "px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"} "キャンセル"]
        [:button {:type "submit" :class "px-5 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold transition"} "登録して巡回開始"]]]]]))
