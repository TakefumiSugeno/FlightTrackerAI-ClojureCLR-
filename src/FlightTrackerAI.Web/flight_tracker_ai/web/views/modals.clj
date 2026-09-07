(ns flight-tracker-ai.web.views.modals
  (:require [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System DateTimeOffset TimeSpan DateOnly]))

(defn- modal-backdrop [title-str content]
  [:div {:id "active-modal"
         :class "fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm"
         :onclick "if (event.target === this) closeCurrentModal();"}
   [:div {:class "bg-slate-950 rounded-2xl shadow-2xl max-w-lg w-full max-h-[90vh] flex flex-col overflow-hidden border border-slate-800 text-slate-100"}
    [:div {:class "px-6 py-4 border-b border-slate-800 bg-slate-900 flex items-center justify-between"}
     [:h3 {:class "text-sm font-bold text-white flex items-center gap-2"}
      [:i {:class "fa-solid fa-circle-plus text-sky-400"}]
      title-str]
     [:button {:type "button"
               :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
               :onclick "closeCurrentModal()"}
      [:i {:class "fa-solid fa-xmark text-base"}]]]
    [:div {:class "p-6 overflow-y-auto space-y-4 flex-1"}
     content]]])

(defn render-quick-note-modal [task-id current-note route-title]
  (h/render-html
    [:div {:id "quickNoteModal"
           :class "fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm"
           :onclick "if (event.target === this) closeCurrentModal();"}
     [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-md rounded-2xl shadow-2xl p-6 space-y-4 text-slate-100"}
      [:div {:class "flex items-center justify-between border-b border-slate-800 pb-3"}
       [:h3 {:class "text-sm font-bold text-white flex items-center space-x-2"}
        [:i {:class "fa-solid fa-file-lines text-sky-400"}]
        [:span (str "タスクのメモ・要望編集: " (or route-title (str task-id)))]]
       [:button {:type "button"
                 :onclick "closeCurrentModal()"
                 :class "text-slate-400 hover:text-white p-1"}
        [:i {:class "fa-solid fa-xmark text-base"}]]]
      [:form {:hx-patch (str "/api/tasks/" task-id "/notes")
              :hx-target "#dashboard-container"
              :onsubmit "closeCurrentModal()"
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
                  :class "px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium"}
         "キャンセル"]
        [:button {:type "submit"
                  :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold"}
         "メモを保存"]]]]]))

(defn render-task-modal [task-opt initial-params]
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
                :onsubmit "closeCurrentModal()"
                :class "space-y-4"}
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
           (if is-edit "更新する" "登録する")]]]))))

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
         "設定を保存"]]])))

(defn render-timeline-modal [task-item latest-offers history]
  (let [trip (:trip-type task-item)
        is-round (= (:kind trip) :round-trip)
        origin-str (domain/iata-code-value (:origin task-item))
        dest-str (domain/iata-code-value (:destination task-item))
        target-price (:target-price-jpy task-item)
        history-json (dto/to-json (or history []))]
    (h/render-html
      [:div {:id "timelineModal"
             :class "fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm"
             :onclick "if (event.target === this) closeCurrentModal();"}
       [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-4xl max-h-[90vh] rounded-2xl shadow-2xl p-6 overflow-y-auto space-y-6 text-slate-100"}
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
