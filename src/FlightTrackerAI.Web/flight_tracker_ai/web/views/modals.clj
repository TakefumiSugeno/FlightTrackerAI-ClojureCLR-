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
    [:div {:class "bg-slate-950 rounded-2xl shadow-2xl max-w-lg w-full max-h-[90vh] flex flex-col overflow-hidden border border-slate-800 text-slate-100 cursor-default"
           :onclick "event.stopPropagation();"}
     [:div {:class "px-6 py-4 border-b border-slate-800 bg-slate-900 flex items-center justify-between"}
      [:h3 {:class "text-sm font-bold text-white flex items-center gap-2"}
       [:i {:class "fa-solid fa-circle-plus text-sky-400"}]
       title-str]
      [:button {:type "button"
                :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
                :onclick "closeCurrentModal()"}
       [:i {:class "fa-solid fa-xmark text-base"}]]]
     [:div {:class "p-6 overflow-y-auto space-y-4 flex-1"}
      content]]]))

(defn render-quick-note-modal [task-id current-note route-title]
  (h/render-html
    [:div {:id "quickNoteModal"
           :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
           :onclick "if (event.target === this) { const form = this.querySelector('form'); if (!window.isFormDirty || !window.isFormDirty(form)) { closeCurrentModal(); } else { if (confirm('入力内容が変更されています。破棄して閉じますか？')) closeCurrentModal(); } }"}
     [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-md rounded-2xl shadow-2xl p-6 space-y-4 text-slate-100 cursor-default"
            :onclick "event.stopPropagation();"}
      [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
       [:h3 {:class "text-sm font-bold text-white flex items-center space-x-2"}
        [:i {:class "fa-solid fa-file-lines text-sky-400"}]
        [:span (str "タスクのメモ・要望編集: " (or route-title (str task-id)))]]
       [:button {:type "button"
                 :onclick "closeCurrentModal()"
                 :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"}
        [:i {:class "fa-solid fa-xmark text-base"}]]]
      [:form {:hx-patch (str "/api/tasks/" task-id "/notes")
              :hx-target "#dashboard-container"
              :hx-swap "outerHTML"
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

(defn render-task-modal
  ([task-opt initial-params] (render-task-modal task-opt initial-params nil))
  ([task-opt initial-params error-msg]
   (let [is-edit (some? task-opt)
         title (if is-edit "タスク設定の変更" "新規フライト監視タスク登録")
         origin-val (or (when task-opt (domain/iata-code-value (:origin task-opt)))
                        (:origin initial-params) "")
         dest-val (or (when task-opt (domain/iata-code-value (:destination task-opt)))
                      (:destination initial-params) "")
         task-title (or (when task-opt (:title task-opt))
                        (:title initial-params) "")
         outbound-val (or (when task-opt
                            (let [trip (:trip-type task-opt)]
                              (when (:outbound trip) (.ToString ^DateOnly (:outbound trip) "yyyy-MM-dd"))))
                          (:outboundDate initial-params) "")
         inbound-val (or (when task-opt
                           (let [trip (:trip-type task-opt)]
                             (when (:inbound trip) (.ToString ^DateOnly (:inbound trip) "yyyy-MM-dd"))))
                         (:inboundDate initial-params) "")
         target-price-val (or (when task-opt (:target-price-jpy task-opt))
                              (:targetPriceJpy initial-params) "")
         check-interval-val (or (when task-opt (:check-interval-hours task-opt))
                                (:checkIntervalHours initial-params) 12)
         webhook-url-val (or (when task-opt (:notification-webhook-url task-opt))
                             (:webhookUrl initial-params) "")
         notes-val (or (when task-opt (:user-notes task-opt))
                       (:notes initial-params) "")]
     (h/render-html
       (modal-backdrop
         title
         [:form {:hx-post (if is-edit (str "/api/tasks/" (:id task-opt)) "/api/tasks")
                 :hx-target "#dashboard-container"
                 :hx-swap "outerHTML"
                 :class "space-y-4"}
          (when (and error-msg (not (str/blank? error-msg)))
            [:div {:class "p-3 rounded-lg bg-rose-950/60 border border-rose-800/80 text-xs text-rose-300 flex items-center gap-2"}
             [:i {:class "fa-solid fa-circle-exclamation text-rose-400"}]
             [:span error-msg]])
          [:div
           [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "タスク名"]
           [:input {:type "text" :name "title" :value task-title :required true
                    :placeholder "例: GW パリ往復"
                    :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]

          [:div {:class "grid grid-cols-2 gap-3"}
           [:div
            [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "出発地 (IATA)"]
            [:input {:type "text" :name "origin" :value origin-val :required true
                     :placeholder "HND"
                     :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white uppercase focus:outline-none focus:border-sky-500"}]]
           [:div
            [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "目的地 (IATA)"]
            [:input {:type "text" :name "destination" :value dest-val :required true
                     :placeholder "CDG"
                     :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white uppercase focus:outline-none focus:border-sky-500"}]]]

          [:div {:class "grid grid-cols-2 gap-3"}
           [:div
            [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "往路出発日"]
            [:input {:type "date" :name "outboundDate" :value outbound-val :required true
                     :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]
           [:div
            [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "復路出発日 (往復時)"]
            [:input {:type "date" :name "inboundDate" :value inbound-val
                     :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]]

          [:div {:class "grid grid-cols-2 gap-3"}
           [:div
            [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "目標価格 (JPY)"]
            [:input {:type "number" :name "targetPriceJpy" :value (str target-price-val)
                     :placeholder "150000"
                     :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]
           [:div
            [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "巡回間隔 (時間)"]
            [:input {:type "number" :name "checkIntervalHours" :value (str check-interval-val) :min "1" :max "168"
                     :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]]

          [:div
           [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "個別 Webhook URL (任意)"]
           [:input {:type "url" :name "webhookUrl" :value webhook-url-val
                    :placeholder "https://discord.com/api/webhooks/..."
                    :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]

          [:div
           [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "ユーザーメモ・要望"]
           [:textarea {:name "userNotes" :rows "2"
                       :placeholder "羽田発直行便希望、ANA優先"
                       :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}
            notes-val]]

          [:div {:class "flex justify-end gap-2 pt-2 border-t border-slate-900"}
           [:button {:type "button"
                     :onclick "closeCurrentModal()"
                     :class "px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-lg font-medium transition"}
            "キャンセル"]
           [:button {:type "submit"
                     :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white text-xs rounded-lg font-semibold transition"}
            (if is-edit "更新する" "登録する")]]]
         true)))))

(defn render-settings-modal [settings]
  (h/render-html
    (modal-backdrop
      "全体システム設定"
      [:form {:hx-post "/api/settings"
              :hx-target "#modal-container"
              :class "space-y-4"}
       [:div
        [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "デフォルト巡回間隔 (時間)"]
        [:input {:type "number" :name "defaultCheckIntervalHours"
                 :value (str (:default-check-interval-hours settings))
                 :min "1" :max "168"
                 :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]

       [:div
        [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "グローバル Webhook URL (Discord / Slack)"]
        [:input {:type "url" :name "defaultWebhookUrl"
                 :value (or (:default-webhook-url settings) "")
                 :placeholder "https://discord.com/api/webhooks/..."
                 :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]

       [:div
        [:label {:class "block text-xs font-medium text-slate-300 mb-1"} "OpenRouter API キー"]
        [:input {:type "password" :name "openRouterApiKey"
                 :value (or (:openrouter-api-key settings) "")
                 :placeholder "sk-or-v1-..."
                 :class "w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500"}]]

       [:div {:class "space-y-2 pt-2 border-t border-slate-900"}
        [:label {:class "flex items-center gap-2 text-xs text-slate-300 cursor-pointer"}
         [:input {:type "checkbox" :name "enableGoogleFlights" :value "1"
                  :checked (boolean (:enable-google-flights settings))}
          "Google Flights を巡回対象にする"]]
        [:label {:class "flex items-center gap-2 text-xs text-slate-300 cursor-pointer"}
         [:input {:type "checkbox" :name "enableSkyscanner" :value "1"
                  :checked (boolean (:enable-skyscanner settings))}
          "Skyscanner を巡回対象にする"]]
        [:label {:class "flex items-center gap-2 text-xs text-slate-300 cursor-pointer"}
         [:input {:type "checkbox" :name "headlessMode" :value "1"
                  :checked (boolean (:headless-mode settings))}
          "ヘッドレスモード (OFFにすると実ブラウザ表示)"]]]

       [:div {:class "flex justify-end gap-2 pt-2 border-t border-slate-900"}
        [:button {:type "button"
                  :onclick "closeCurrentModal()"
                  :class "px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-lg font-medium transition"}
         "閉じる"]
        [:button {:type "submit"
                  :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white text-xs rounded-lg font-semibold transition"}
         "設定を保存"]]]
      true)))

(defn render-timeline-modal [task-item latest-offers history]
  (let [trip (:trip-type task-item)
        is-round (= (:kind trip) :round-trip)
        origin-str (domain/iata-code-value (:origin task-item))
        dest-str (domain/iata-code-value (:destination task-item))
        target-price (:target-price-jpy task-item)
        history-json (dto/to-json (or history []))]
    (h/render-html
      [:div {:id "timelineModal"
             :class "modal-backdrop-clickable fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm cursor-pointer"
             :onclick "if (event.target === this) closeCurrentModal();"}
       [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-4xl max-h-[90vh] rounded-2xl shadow-2xl p-6 overflow-y-auto space-y-6 text-slate-100 cursor-default"
              :onclick "event.stopPropagation();"}
        ;; Header
        [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
         [:div
          [:h3 {:class "text-base font-bold text-white flex items-center gap-2"}
           [:i {:class "fa-solid fa-plane-departure text-sky-400"}]
           [:span (str (:title task-item) " (" origin-str " ➔ " dest-str ")")]]
          [:p {:class "text-xs text-slate-400 mt-0.5"}
           (str (if is-round "往復旅程" "片道旅程") " • 目標価格: " (if target-price (str "¥" (.ToString (long target-price) "N0")) "未設定"))]]
         [:button {:type "button"
                   :onclick "closeCurrentModal()"
                   :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800"}
          [:i {:class "fa-solid fa-xmark text-lg"}]]]

        ;; AI Summary if exists
        (when-let [summary (:ai-analysis-summary task-item)]
          [:div {:class "bg-sky-950/40 border border-sky-800/60 rounded-xl p-4 flex items-start gap-3"}
           [:div {:class "p-2 bg-sky-500/20 text-sky-400 rounded-lg shrink-0"}
            [:i {:class "fa-solid fa-wand-magic-sparkles text-sm"}]]
           [:div
            [:h4 {:class "text-xs font-bold text-sky-300"} "AI 買い時分析サマリー"]
            [:p {:class "text-xs text-slate-300 mt-1 leading-relaxed"} summary]]])

        ;; Leg details
        [:div {:class "bg-slate-900/60 border border-slate-800 rounded-xl p-4 space-y-3"}
         [:div {:class "flex items-center justify-between text-xs font-bold text-slate-300"}
          [:span {:class "px-2 py-0.5 rounded bg-sky-950 text-sky-300 border border-sky-800"} "往路"]
          [:span (str (.ToString ^DateOnly (:outbound trip) "yyyy/MM/dd") " 出発")]]
         [:div {:class "bg-slate-950 p-3 rounded-lg border border-slate-800 text-xs flex flex-col sm:flex-row items-center justify-between gap-2"}
          [:div {:class "flex items-center space-x-3"}
           [:span {:class "font-bold text-white text-sm"} origin-str]
           [:i {:class "fa-solid fa-arrow-right text-sky-400"}]
           [:span {:class "font-bold text-white text-sm"} dest-str]]
          [:span {:class "text-slate-400"} "直行または経由便 (最新候補便を参照)"]]
         (when is-round
           [:div {:class "pt-2 border-t border-slate-800 space-y-2"}
            [:div {:class "flex items-center justify-between text-xs font-bold text-slate-300"}
             [:span {:class "px-2 py-0.5 rounded bg-indigo-950 text-indigo-300 border border-indigo-800"} "復路"]
             [:span (str (.ToString ^DateOnly (:inbound trip) "yyyy/MM/dd") " 出発")]]
            [:div {:class "bg-slate-950 p-3 rounded-lg border border-slate-800 text-xs flex flex-col sm:flex-row items-center justify-between gap-2"}
             [:div {:class "flex items-center space-x-3"}
              [:span {:class "font-bold text-white text-sm"} dest-str]
              [:i {:class "fa-solid fa-arrow-right text-indigo-400"}]
              [:span {:class "font-bold text-white text-sm"} origin-str]]
             [:span {:class "text-slate-400"} "復路便"]]])]

        ;; Chart Section
        [:div {:class "bg-slate-900/60 p-4 rounded-xl border border-slate-800"}
         [:div {:class "flex items-center justify-between mb-2"}
          [:span {:class "text-xs font-bold text-slate-300"} "最安値 推移チャート (JPY)"]
          [:div {:class "flex items-center space-x-3 text-xs"}
           [:span {:class "text-emerald-400"} "● Google Flights"]
           [:span {:class "text-sky-400"} "● Skyscanner"]
           (when target-price
             [:span {:class "text-rose-400"} (str "-- 目標価格 (¥" (.ToString (long target-price) "N0") ")")])]]
         [:div {:class "h-52 relative"}
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

        ;; Offers comparison table
        [:div
         [:div {:class "flex items-center justify-between mb-2"}
          [:h4 {:class "text-xs font-bold text-white flex items-center space-x-1.5"}
           [:i {:class "fa-solid fa-list text-sky-400"}]
           [:span "同日・同区間の最新候補便一覧"]]
          [:span {:class "text-xs text-slate-400"} (str "取得件数: " (count latest-offers) "件")]]
         (if (empty? latest-offers)
           [:p {:class "text-xs text-slate-500 py-4 text-center"} "巡回による候補便データはまだ記録されていません。"]
           [:div {:class "overflow-x-auto border border-slate-800 rounded-lg"}
            [:table {:class "w-full text-left text-xs text-slate-300 min-w-max"}
             [:thead {:class "bg-slate-900 text-slate-400 font-semibold border-b border-slate-800"}
              [:tr
               [:th {:class "px-3 py-2"} "ソース"]
               [:th {:class "px-3 py-2"} "航空会社"]
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
                    "-")]])]]])]

        ;; Footer
        [:div {:class "flex justify-end pt-2 border-t border-slate-800"}
         [:button {:type "button"
                   :onclick "closeCurrentModal()"
                   :class "px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-lg font-medium"}
          "閉じる"]]]])))

(defn render-logs-modal [logs log-file-path]
  (let [log-content (str/join "\n" (or logs []))]
    (h/render-html
      (modal-backdrop
        "システム実行ログ (Debug & Activity Logs)"
        [:div {:class "space-y-4 text-xs"}
         [:div {:class "flex items-center justify-between text-slate-400"}
          [:span {:class "font-mono text-[11px] truncate max-w-xs"} (str "ログ: " (or log-file-path "app.log"))]
          [:span {:class "text-[11px] font-medium text-sky-400"} (str "最新 " (count (or logs [])) " 行を表示中")]]

         [:div {:class "relative"}
          [:pre {:id "logContentPre"
                 :class "w-full h-80 bg-slate-900 border border-slate-800 rounded-xl p-3 font-mono text-[11px] text-slate-200 overflow-y-auto leading-relaxed whitespace-pre-wrap select-all"}
           log-content]]

         [:div {:class "flex items-center justify-between pt-2 border-t border-slate-800"}
          [:div {:class "flex items-center space-x-2"}
           [:button {:type "button"
                     :class "px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium flex items-center gap-1.5 transition"
                     :hx-get "/api/logs/modal"
                     :hx-target "#modal-container"}
            [:i {:class "fa-solid fa-rotate-right text-xs"}]
            "再読込"]
           [:button {:type "button"
                     :class "px-3 py-1.5 bg-sky-600/30 hover:bg-sky-600 text-sky-200 hover:text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition border border-sky-500/40"
                     :onclick "navigator.clipboard.writeText(document.getElementById('logContentPre').innerText).then(() => showToast('ログをクリップボードにコピーしました（AI共有用）', true));"}
            [:i {:class "fa-regular fa-copy text-xs"}]
            "ログをコピー (AI共有用)"]]
          [:button {:type "button"
                    :class "px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"
                    :onclick "closeCurrentModal()"}
           "閉じる"]]]))))

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
         [:i {:class "fa-solid fa-plane-departure text-lg"}]]
        [:div
         [:h2 {:class "text-lg font-bold text-white"} "新規フライト監視タスク登録"]
         [:p {:class "text-xs text-slate-400"} "AI解析によって抽出された条件をご確認・調整の上、登録してください。"]]]
       [:a {:href "/"
            :class "text-xs text-slate-400 hover:text-white px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 transition flex items-center gap-1.5"}
        [:i {:class "fa-solid fa-arrow-left text-[10px]"}]
        "ダッシュボードへ戻る"]]

      [:form {:action "/api/tasks/standalone"
              :method "post"
              :class "space-y-5 text-xs"}
       ;; 旅行タイプ切り替えボタン
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
        [:input {:type "hidden" :id "formTripType" :name "tripType" :value (if is-one-way "OneWay" "RoundTrip")}]]

       ;; 空港選択
       [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "出発地 (都市名またはIATA) *"]
         [:input {:list "airportsList"
                  :type "text"
                  :name "origin"
                  :required true
                  :value origin
                  :placeholder "HND - 東京(羽田)"
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "目的地 (都市名またはIATA) *"]
         [:input {:list "airportsList"
                  :type "text"
                  :name "destination"
                  :required true
                  :value destination
                  :placeholder "CDG - パリ"
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]

       ;; 空港候補 Datalist
       [:datalist {:id "airportsList"}
        [:option {:value "HND - 東京(羽田)"}]
        [:option {:value "NRT - 東京(成田)"}]
        [:option {:value "KIX - 大阪(関西)"}]
        [:option {:value "ITM - 大阪(伊丹)"}]
        [:option {:value "FUK - 福岡"}]
        [:option {:value "CTS - 札幌(新千歳)"}]
        [:option {:value "MNL - マニラ(ニノイ・アキノ)"}]
        [:option {:value "CDG - パリ(シャルル・ド・ゴール)"}]
        [:option {:value "LHR - ロンドン(ヒースロー)"}]
        [:option {:value "LAX - ロサンゼルス"}]
        [:option {:value "SFO - サンフランシスコ"}]
        [:option {:value "HNL - ホノルル"}]
        [:option {:value "BKK - バンコク(スワンナプーム)"}]
        [:option {:value "SIN - シンガポール(チャンギ)"}]
        [:option {:value "TPE - 台北(桃園)"}]]

       ;; 日程入力
       [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1 flex items-center gap-1.5"}
          [:i {:class "fa-regular fa-calendar text-sky-400 text-xs"}]
          "往路出発日 *"]
         [:input {:type "date"
                  :name "outboundDate"
                  :required true
                  :value outbound-date
                  :class "w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3.5 py-2.5 text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]
        [:div {:id "inboundDateContainer"
               :style (if is-one-way "display: none;" "")}
         [:label {:class "block text-slate-400 font-medium mb-1 flex items-center gap-1.5"}
          [:i {:class "fa-regular fa-calendar text-indigo-400 text-xs"}]
          "復路出発日"]
         [:input {:type "date"
                  :name "inboundDate"
                  :value inbound-date
                  :class "w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3.5 py-2.5 text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]

       ;; 乗継 & 巡回間隔
       [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "許容乗継回数 (Max Stops)"]
         [:select {:name "maxStops"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
          [:option (merge {:value "Any"} (when (= max-stops "Any") {:selected true})) "乗継制限なし (最安重視・推奨)"]
          [:option (merge {:value "OneStop"} (when (= max-stops "OneStop") {:selected true})) "1回乗継まで"]
          [:option (merge {:value "DirectOnly"} (when (= max-stops "DirectOnly") {:selected true})) "直行便のみ (0回乗継)"]]]
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "巡回間隔"]
         [:select {:name "checkIntervalHours"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
          [:option {:value "12" :selected true} "全体設定に従う (現在 12h)"]
          [:option {:value "3"} "3時間ごと"]
          [:option {:value "6"} "6時間ごと"]
          [:option {:value "12"} "12時間ごと"]
          [:option {:value "24"} "24時間ごと"]]]]

       ;; 目標価格 & タスク名
       [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "目標アラート価格 (JPY)"]
         [:input {:type "number"
                  :name "targetPriceJpy"
                  :value max-price-jpy
                  :placeholder "例: 50000"
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "タスク名 (任意)"]
         [:input {:type "text"
                  :name "title"
                  :value (str (if (str/blank? origin) "東京" origin) " ➔ " (if (str/blank? destination) "目的地" destination))
                  :placeholder "例: 東京 ➔ マニラ 直行便旅行"
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]

       ;; メモ
       [:div
        [:label {:class "block text-slate-400 font-medium mb-1"} "構造化メモ / 要望・制約（任意）"]
        [:textarea {:name "userNotes"
                    :rows "3"
                    :placeholder "例: - 荷物制限なし希望\n- 週末旅行"
                    :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white font-mono text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none resize-y"}
         notes]]

       ;; ブラウザ表示・Webhook 設定
       [:div {:class "p-4 bg-slate-900 rounded-xl border border-slate-800 space-y-3"}
        [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
         [:input {:type "checkbox"
                  :name "useDefaultWebhook"
                  :value "true"
                  :checked true
                  :class "rounded border-slate-700 text-sky-600 focus:ring-sky-500 bg-slate-950"}]
         [:span "デフォルトの Discord / Slack Webhook に通知する"]]
        [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
         [:input {:type "checkbox"
                  :name "showBrowser"
                  :value "true"
                  :class "rounded border-slate-700 text-sky-600 focus:ring-sky-500 bg-slate-950"}]
         [:span {:class "text-sky-300 font-medium"} "定期巡回時もブラウザ画面を表示する (手動支援モード)"]]]

       ;; アクションボタン
       [:div {:class "pt-4 border-t border-slate-800 flex items-center justify-end space-x-3"}
        [:a {:href "/"
             :class "px-5 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition text-center"}
         "キャンセル"]
        [:button {:type "submit"
                  :class "px-6 py-2.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold shadow-md transition flex items-center gap-2"}
         [:i {:class "fa-solid fa-plane-departure"}]
         "登録して巡回開始"]]]]]))
