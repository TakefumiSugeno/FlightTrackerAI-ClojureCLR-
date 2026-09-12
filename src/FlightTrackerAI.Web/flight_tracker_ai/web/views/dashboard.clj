(ns flight-tracker-ai.web.views.dashboard
  (:require [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System DateTimeOffset TimeSpan DateOnly]))

(defn- format-jst [^DateTimeOffset dto-opt]
  (if dto-opt
    (let [jst (.ToOffset dto-opt (TimeSpan/FromHours 9.0))]
      [(.ToString jst "yyyy/MM/dd") (str (.ToString jst "HH:mm") " (JST)")])
    ["-" "未巡回"]))

(defn render-empty-state []
  [:div {:id "emptyStateView" :class "text-center py-16 bg-slate-950 rounded-2xl border border-dashed border-slate-800 my-6"}
   [:div {:class "w-14 h-14 bg-sky-950/60 border border-sky-800/40 rounded-2xl flex items-center justify-center mx-auto text-sky-400 mb-4"}
    [:i {:data-lucide "plane" :class "w-7 h-7 text-sky-400"}]]
   [:h3 {:class "text-base font-bold text-slate-100"} "監視中のタスクはありません"]
   [:p {:class "text-xs text-slate-400 mt-1 max-w-sm mx-auto leading-relaxed"}
    "上部のAI自然言語入力、または「新規タスク登録」ボタンから監視したい航空券ルートを登録してください。"]
   [:div {:class "mt-5 flex items-center justify-center gap-3"}
    [:button {:type "button"
              :class "bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold px-4 py-2 rounded-lg shadow-sm transition inline-flex items-center gap-1.5"
              :hx-get "/api/tasks/new-modal"
              :hx-target "#modal-container"}
     [:i {:data-lucide "plus" :class "w-4 h-4"}]
     "タスクを登録する"]
    [:button {:type "button"
              :class "bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold px-4 py-2 rounded-lg border border-slate-700 transition inline-flex items-center gap-1.5"
              :onclick "focusAiInput()"}
     [:i {:data-lucide "wand-2" :class "w-4 h-4 text-sky-400"}]
     "AIアシスタントへ移動"]]])

(defn render-manual-challenge-banner []
  [:div {:id "manualChallengeBanner"
         :class "bg-amber-950/80 border border-amber-500/50 rounded-xl p-4 shadow-lg flex flex-col md:flex-row items-start md:items-center justify-between gap-4 transition-all mb-6"}
   [:div {:class "flex items-start space-x-3"}
    [:div {:class "p-2 bg-amber-500/20 text-amber-400 rounded-lg shrink-0 mt-0.5 md:mt-0 animate-pulse"}
     [:i {:data-lucide "shield-alert" :class "w-5 h-5"}]]
    [:div
     [:div {:class "flex items-center space-x-2"}
      [:span {:class "text-sm font-bold text-amber-300"}
       "Skyscanner 認証チャレンジ（PRESS & HOLD）を検知しました"]
      [:span {:id "challengeCountdownBadge"
              :class "px-2 py-0.5 text-[10px] font-mono font-bold bg-amber-900/60 text-amber-200 border border-amber-600/50 rounded-full animate-pulse"}
       "残り " [:span {:id "challengeSeconds"} "54"] " 秒"]]
     [:p {:class "text-xs text-slate-300 mt-1 leading-relaxed"}
      "Kasada/PerimeterX によるセキュリティ検証が発生しています。画面上のブラウザウィンドウで「長押し（PRESS & HOLD）」を手動解除してください。解除を検知すると自動で巡回が再開されます。"]]]
   [:div {:class "flex items-center space-x-2 shrink-0 self-end md:self-auto"}
    [:button {:type "button"
              :onclick "simulateResolveChallenge()"
              :class "px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-semibold shadow transition flex items-center space-x-1"}
     [:i {:data-lucide "check-circle" :class "w-4 h-4"}]
     [:span "手動解除シミュレート"]]
    [:button {:type "button"
              :onclick "dismissChallengeBanner()"
              :class "p-1.5 text-slate-400 hover:text-slate-200 rounded-lg transition"
              :title "非表示"}
     [:i {:data-lucide "x" :class "w-4 h-4"}]]]])

(defn render-ai-assistant-box []
  [:div {:class "bg-slate-950 border border-slate-800 rounded-xl p-4 shadow-sm space-y-3 mb-6"}
   [:div {:class "flex flex-col sm:flex-row sm:items-center justify-between gap-2"}
    [:div {:class "flex items-center space-x-2 text-xs font-semibold text-sky-400"}
     [:i {:data-lucide "sparkles" :class "w-4 h-4"}]
     [:span "AI 構造化文書・自然言語解析アシスタント"]]
    [:div {:class "flex items-center space-x-2 text-[11px]"}
     [:span {:class "text-slate-500 hidden sm:inline"} "テンプレート入力:"]
     [:button {:type "button" :onclick "insertTemplate('markdown')"
               :class "px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 hover:text-white transition"}
      "箇条書き"]
     [:button {:type "button" :onclick "insertTemplate('yaml')"
               :class "px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 hover:text-white transition"}
      "YAML形式"]
     [:button {:type "button" :onclick "insertTemplate('natural')"
               :class "px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 hover:text-white transition"}
      "自然文"]]]
   [:div {:class "relative"}
    [:textarea {:id "aiInput"
                :rows "4"
                :placeholder "【自由な構造化テキストで条件を記述できます】\n例:\n- 出発地: 東京 (羽田 / HND)\n- 目的地: パリ (CDG)\n- 往路日: 2026/05/01, 復路日: 2026/05/08\n- 乗継: 直行便のみ\n- 目標予算: 160,000 円以下\n- 希望航空会社: ANA, JAL, エールフランス"
                :class "w-full bg-slate-900 border border-slate-700 rounded-lg p-3 text-xs text-white placeholder-slate-500 font-mono focus:outline-none focus:ring-1 focus:ring-sky-500 leading-relaxed resize-y min-h-[96px]"}]]
   [:div {:class "flex flex-col sm:flex-row sm:items-center justify-between gap-2 pt-1 border-t border-slate-800/80"}
    [:span {:class "text-[11px] text-slate-400"}
     "※ 箇条書きやMarkdown、長文からOpenRouter AIが自動で各パラメータを抽出して登録フォームへ展開します。"]
    [:button {:type "button"
              :id "btnParseWithAi"
              :onclick "parseWithAI()"
              :class "bg-sky-600 hover:bg-sky-500 active:scale-95 transition text-white px-4 py-2 rounded-lg text-xs font-semibold flex items-center justify-center space-x-1.5 whitespace-nowrap shadow-sm"}
     [:i {:data-lucide "wand-2" :class "w-3.5 h-3.5"}]
     [:span "AIで解析して新規登録フォームに反映"]]]])

(defn render-filter-bar [tasks]
  (let [total (count tasks)
        active-cnt (count (filter #(= (:status %) :active) tasks))
        paused-cnt (count (filter #(= (:status %) :paused) tasks))
        error-cnt (count (filter #(= (:status %) :error) tasks))]
    [:div {:class "flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-950/80 p-3 rounded-xl border border-slate-800 mb-6"}
     ;; Status Tabs
     [:div {:class "flex flex-wrap gap-1" :id "statusTabs"}
      [:button {:type "button" :onclick "setStatusFilter('all')" :id "tabAll"
                :class "px-3 py-1.5 rounded-md text-xs font-medium bg-sky-600 text-white"}
       (str "すべて (" total ")")]
      [:button {:type "button" :onclick "setStatusFilter('active')" :id "tabActive"
                :class "px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800"}
       (str "監視中 (" active-cnt ")")]
      [:button {:type "button" :onclick "setStatusFilter('paused')" :id "tabPaused"
                :class "px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800"}
       (str "一時停止 (" paused-cnt ")")]
      [:button {:type "button" :onclick "setStatusFilter('error')" :id "tabError"
                :class "px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800"}
       (str "エラー (" error-cnt ")")]
      [:button {:type "button" :onclick "setStatusFilter('empty')" :id "tabEmpty"
                :class "px-3 py-1.5 rounded-md text-xs font-medium text-slate-500 hover:text-slate-300 hover:bg-slate-800"}
       "未登録 (0件画面)"]]

     ;; View Toggle & Quick Search
     [:div {:class "flex items-center space-x-2 sm:space-x-3"}
      [:div {:class "relative flex-1 sm:flex-none"}
       [:i {:data-lucide "search" :class "w-3.5 h-3.5 absolute left-3 top-2.5 text-slate-400"}]
       [:input {:type "text"
                :id "quickSearchInput"
                :oninput "applyAllFilters()"
                :placeholder "都市・空港・航空会社で検索..."
                :class "bg-slate-900 border border-slate-700 rounded-lg pl-8 pr-3 py-1.5 text-xs text-white placeholder-slate-500 w-full sm:w-56 focus:outline-none focus:ring-1 focus:ring-sky-500"}]]
      [:div {:class "flex bg-slate-900 border border-slate-700 rounded-lg p-0.5"}
       [:button {:type "button" :id "btnViewCards" :onclick "switchView('cards')"
                 :class "px-2.5 py-1 rounded text-xs font-medium bg-slate-800 text-white flex items-center space-x-1"}
        [:i {:data-lucide "layout-grid" :class "w-3.5 h-3.5"}]
        [:span {:class "hidden sm:inline"} "カード"]]
       [:button {:type "button" :id "btnViewList" :onclick "switchView('list')"
                 :class "px-2.5 py-1 rounded text-xs font-medium text-slate-400 hover:text-white flex items-center space-x-1"}
        [:i {:data-lucide "table" :class "w-3.5 h-3.5"}]
        [:span {:class "hidden sm:inline"} "一覧リスト (Excel風)"]]]]]))

(defn render-task-card [task-item]
  (let [is-target-met (domain/target-achieved? task-item)
        trip (:trip-type task-item)
        is-round (= (:kind trip) :round-trip)
        origin-str (domain/iata-code-value (:origin task-item))
        dest-str (domain/iata-code-value (:destination task-item))
        route-key (str origin-str "-" dest-str)
        status (:status task-item)
        status-str (name status)
        [date-part time-part] (format-jst (:last-checked-at task-item))
        target-price (:target-price-jpy task-item)
        price-val (:last-lowest-price-jpy task-item)
        price-str (if price-val (str "¥" (.ToString (long price-val) "N0")) "---")
        notes-val (or (:user-notes task-item) "")
        airlines-val (or (:last-lowest-airlines task-item) "")
        provider-val (:last-lowest-provider task-item)
        provider-name (if provider-val (domain/scraping-provider-to-string provider-val) "---")
        card-class (str "taskCard bg-slate-950 border rounded-xl p-5 shadow-sm hover:border-slate-700 transition flex flex-col justify-between "
                        (if (= status :error) "border-rose-900/60" "border-slate-800"))
        search-corpus (str origin-str " " dest-str " " (:title task-item) " " airlines-val " " notes-val " " status-str)]
    [:div {:class card-class
           :data-status status-str
           :data-route route-key
           :data-airlines airlines-val
           :data-search search-corpus
           :data-price (str (or price-val 0))}
     [:div
      ;; Top badges & Action buttons
      [:div {:class "flex items-center justify-between"}
       [:div {:class "flex items-center space-x-2"}
        (cond
          is-target-met
          [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-emerald-950 text-emerald-300 border border-emerald-800/60 flex items-center space-x-1"}
           [:i {:data-lucide "check-circle" :class "w-3 h-3"}]
           [:span "目標達成"]]
          (= status :active)
          [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-sky-950 text-sky-300 border border-sky-800/60 flex items-center space-x-1"}
           [:i {:data-lucide "activity" :class "w-3 h-3"}]
           [:span "監視中"]]
          (= status :paused)
          [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 flex items-center space-x-1"}
           [:i {:data-lucide "pause-circle" :class "w-3 h-3"}]
           [:span "一時停止"]]
          :else
          [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-rose-950 text-rose-300 border border-rose-800/60 flex items-center space-x-1"}
           [:i {:data-lucide "alert-circle" :class "w-3 h-3"}]
           [:span "エラー"]])
        [:span {:class "text-xs text-slate-400"}
         (str (if is-round "往復" "片道") " (" (:check-interval-hours task-item) "h毎巡回)")]]
       [:div {:class "flex items-center space-x-1 text-slate-400"}
        [:button {:type "button"
                  :onclick (str "triggerImmediateRun('" (:id task-item) "')")
                  :title "今すぐ巡回実行"
                  :class "p-1.5 hover:text-sky-400 hover:bg-slate-800 rounded transition"}
         [:i {:data-lucide "refresh-cw" :class "w-3.5 h-3.5"}]]
        [:button {:type "button"
                  :hx-get (str "/api/tasks/" (:id task-item) "/modal")
                  :hx-target "#modal-container"
                  :title "タスク設定変更"
                  :class "p-1.5 hover:text-white hover:bg-slate-800 rounded transition"}
         [:i {:data-lucide "edit-3" :class "w-3.5 h-3.5"}]]
        [:button {:type "button"
                  :hx-get (str "/api/tasks/" (:id task-item) "/delete-modal")
                  :hx-target "#modal-container"
                  :title "削除"
                  :class "p-1.5 hover:text-rose-400 hover:bg-slate-800 rounded transition"}
         [:i {:data-lucide "trash-2" :class "w-3.5 h-3.5"}]]]]

      ;; Route & Schedule
      [:div {:class "mt-3 flex items-start justify-between"}
       [:div
        [:div {:class "text-xl font-bold text-white flex items-center space-x-2"}
         [:span origin-str]
         [:i {:data-lucide "arrow-right" :class "w-4 h-4 text-sky-400"}]
         [:span dest-str]]
        [:div {:class "text-xs text-slate-400 mt-0.5"} (:title task-item)]]
       [:div {:class "text-right"}
        [:div {:class "text-xs font-semibold text-white"}
         [:span {:class "text-sky-400"} "往: "]
         (.ToString ^DateOnly (:outbound trip) "MM/dd") " 出発"]
        (when is-round
          [:div {:class "text-xs font-semibold text-white mt-0.5"}
           [:span {:class "text-indigo-400"} "復: "]
           (.ToString ^DateOnly (:inbound trip) "MM/dd") " 出発"
           [:span {:class "text-slate-400 text-[10px] ml-1"} "(+1)"]])
        [:div {:class "text-[11px] text-slate-400 mt-1"}
         (if (= (:max-stops task-item) :direct-only) "直行便のみ" "乗継制限なし")]]]

      ;; Price summary
      [:div {:class "mt-4 pt-3 border-t border-slate-800/80 grid grid-cols-2 gap-2"}
       [:div
        [:div {:class "text-xs text-slate-400"} "最安航空会社"]
        [:div {:class "text-sm font-semibold text-white mt-0.5 flex items-center space-x-1.5"}
         [:span {:class (if is-target-met "w-2 h-2 rounded-full bg-emerald-400" "w-2 h-2 rounded-full bg-sky-400")}]
         [:span {:class "truncate"} (if (not (str/blank? airlines-val)) airlines-val "未取得")]]
        [:div {:class "text-xs text-slate-500 mt-1 flex items-center space-x-1"}
         [:i {:data-lucide "clock" :class "w-3 h-3"}]
         [:span (str "取得: " date-part " " time-part)]]]
       [:div {:class "text-right"}
        [:div {:class "text-xs text-slate-400"}
         "現在最安値 (" [:span {:class "text-sky-400 font-medium"} provider-name] ")"]
        [:div {:class (if is-target-met "text-xl font-bold text-emerald-400" "text-xl font-bold text-white")} price-str]
        [:div {:class "text-xs text-slate-400"}
         (if target-price (str "目標: ¥" (.ToString (long target-price) "N0") " 以下") "目標未設定")]]]

      ;; User Note Box
      [:div {:class "mt-3 p-2 bg-slate-900/90 border border-slate-800 rounded-lg text-[11px] text-slate-300 flex items-center justify-between"}
       [:div {:class "flex items-center space-x-1.5 truncate flex-1 mr-2"}
        [:i {:data-lucide "file-text" :class "w-3.5 h-3.5 text-sky-400 shrink-0"}]
        [:span {:class "truncate font-mono"}
         (if (str/blank? notes-val) "メモ: (未入力)" (str "メモ: " notes-val))]]
       [:button {:type "button"
                 :hx-get (str "/api/tasks/" (:id task-item) "/notes-modal")
                 :hx-target "#modal-container"
                 :title "メモを編集"
                 :class "text-slate-400 hover:text-white shrink-0 p-1 hover:bg-slate-800 rounded transition"}
        [:i {:data-lucide "edit-2" :class "w-3 h-3"}]]]]

     ;; Footer
     [:div {:class "mt-4 pt-3 border-t border-slate-800 flex items-center justify-between"}
      [:div {:class "flex items-center space-x-2 text-xs"}
       [:span {:class "px-2 py-0.5 rounded bg-emerald-950 border border-emerald-800 text-emerald-300 font-medium"} "Google"]
       [:span {:class "px-2 py-0.5 rounded bg-sky-950 border border-sky-800 text-sky-300 font-medium"} "Skyscanner"]]
      [:button {:type "button"
                :hx-get (str "/api/tasks/" (:id task-item) "/detail-modal")
                :hx-target "#modal-container"
                :class "text-xs font-semibold text-sky-400 hover:text-sky-300 flex items-center space-x-1"}
       [:span "所要時間・乗継詳細 & 便比較"]
       [:i {:data-lucide "chevron-right" :class "w-3.5 h-3.5"}]]]]))

(defn render-list-view [tasks]
  (let [unique-routes (distinct (map (fn [t] (str (domain/iata-code-value (:origin t)) "-" (domain/iata-code-value (:destination t)))) tasks))
        unique-airlines (distinct (remove str/blank? (mapcat #(when-let [a (:last-lowest-airlines %)] (str/split a #"[,\s/+]")) tasks)))]
    [:div {:id "listView" :class "hidden bg-slate-950 border border-slate-800 rounded-xl overflow-visible shadow-sm"}
     ;; Synchronized Active Filters Chips Bar
     [:div {:id "activeFilterChipsBar"
            :class "px-4 py-2.5 bg-slate-900/90 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2 text-xs"}
      [:div {:class "flex items-center space-x-2 flex-wrap gap-1.5"}
       [:span {:class "text-slate-400 flex items-center space-x-1 font-medium"}
        [:i {:data-lucide "filter" :class "w-3 h-3 text-sky-400"}]
        [:span "適用中のフィルタ:"]]
       [:span {:id "chipStatus" :class "hidden inline-flex items-center px-2 py-0.5 rounded-md bg-sky-950 text-sky-300 border border-sky-800 text-[11px]"}
        [:span "ステータス: " [:strong {:id "chipStatusVal"} "すべて"]]
        [:button {:type "button" :onclick "setStatusFilter('all')" :class "ml-1 text-sky-400 hover:text-white"} "✕"]]
       [:span {:id "chipRoute" :class "hidden inline-flex items-center px-2 py-0.5 rounded-md bg-sky-950 text-sky-300 border border-sky-800 text-[11px]"}
        [:span "区間: " [:strong {:id "chipRouteVal"} "選択中"]]
        [:button {:type "button" :onclick "clearRouteFilter()" :class "ml-1 text-sky-400 hover:text-white"} "✕"]]
       [:span {:id "chipAirline" :class "hidden inline-flex items-center px-2 py-0.5 rounded-md bg-sky-950 text-sky-300 border border-sky-800 text-[11px]"}
        [:span "航空会社: " [:strong {:id "chipAirlineVal"} "選択中"]]
        [:button {:type "button" :onclick "clearAirlineFilter()" :class "ml-1 text-sky-400 hover:text-white"} "✕"]]
       [:span {:id "noFilterLabel" :class "text-slate-500 italic text-[11px]"}
        "（すべてのタスクを表示中。上のタブまたは列の 🔽 から絞り込めます）"]]
      [:div {:class "flex items-center space-x-3 text-slate-400 text-[11px]"}
       [:span {:id "filterCountSummary"}
        "表示中: " [:strong {:class "text-white" :id "filterCountNum"} (str (count tasks))] (str " / 全" (count tasks) "件")]
       [:button {:type "button" :onclick "resetAllFilters()" :class "text-sky-400 hover:text-sky-300 underline font-medium"}
        "全解除"]]]

     ;; Table
     [:div {:class "overflow-x-auto"}
      [:table {:class "w-full text-left text-xs text-slate-300"}
       [:thead {:class "bg-slate-900 text-slate-400 font-semibold border-b border-slate-800 select-none"}
        [:tr
         ;; Col 1: Status Filter
         [:th {:class "px-3 py-2.5 relative whitespace-nowrap"}
          [:div {:class "flex items-center space-x-1"}
           [:span "ステータス"]
           [:button {:type "button" :id "btnFilterStatus" :onclick "toggleFilterMenu(event, 'statusDropdown')"
                     :title "ステータスで絞り込み" :class "p-1 rounded hover:bg-slate-800 hover:text-white transition flex items-center"}
            [:i {:data-lucide "filter" :class "w-3 h-3 text-slate-400"}]]]
          [:div {:id "statusDropdown" :class "hidden absolute top-full left-2 mt-1 w-52 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl z-50 p-3 text-xs space-y-2.5 whitespace-normal"}
           [:div {:class "flex items-center justify-between border-b border-slate-800 pb-1.5"}
            [:span {:class "font-bold text-white"} "ステータス絞り込み"]
            [:button {:type "button" :onclick "closeAllFilterMenus()" :class "text-slate-400 hover:text-white"} "✕"]]
           [:div {:class "space-y-1.5"}
            [:label {:class "flex items-center space-x-2 text-slate-300 hover:bg-slate-800/50 p-1 rounded cursor-pointer"}
             [:input {:type "radio" :name "statusFilterRadio" :onchange "setStatusFilter('all')" :class "statusRadio" :value "all" :checked true}]
             [:span "すべて表示"]]
            [:label {:class "flex items-center space-x-2 text-slate-300 hover:bg-slate-800/50 p-1 rounded cursor-pointer"}
             [:input {:type "radio" :name "statusFilterRadio" :onchange "setStatusFilter('active')" :class "statusRadio" :value "active"}]
             [:span {:class "px-1.5 py-0.5 rounded bg-sky-950 text-sky-300 text-[10px]"} "監視中"]]
            [:label {:class "flex items-center space-x-2 text-slate-300 hover:bg-slate-800/50 p-1 rounded cursor-pointer"}
             [:input {:type "radio" :name "statusFilterRadio" :onchange "setStatusFilter('paused')" :class "statusRadio" :value "paused"}]
             [:span {:class "px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 text-[10px]"} "一時停止中"]]
            [:label {:class "flex items-center space-x-2 text-slate-300 hover:bg-slate-800/50 p-1 rounded cursor-pointer"}
             [:input {:type "radio" :name "statusFilterRadio" :onchange "setStatusFilter('error')" :class "statusRadio" :value "error"}]
             [:span {:class "px-1.5 py-0.5 rounded bg-rose-950 text-rose-300 text-[10px]"} "エラー"]]]]]

         ;; Col 2: Route Filter
         [:th {:class "px-3 py-2.5 relative whitespace-nowrap"}
          [:div {:class "flex items-center space-x-1"}
           [:span "区間"]
           [:button {:type "button" :id "btnFilterRoute" :onclick "toggleFilterMenu(event, 'routeDropdown')"
                     :title "区間で絞り込み" :class "p-1 rounded hover:bg-slate-800 hover:text-white transition flex items-center"}
            [:i {:data-lucide "filter" :class "w-3 h-3 text-slate-400"}]]]
          [:div {:id "routeDropdown" :class "hidden absolute top-full left-2 mt-1 w-56 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl z-50 p-3 text-xs space-y-2.5 whitespace-normal"}
           [:div {:class "flex items-center justify-between border-b border-slate-800 pb-1.5"}
            [:span {:class "font-bold text-white"} "区間 絞り込み"]
            [:button {:type "button" :onclick "closeAllFilterMenus()" :class "text-slate-400 hover:text-white"} "✕"]]
           [:div {:class "space-y-1.5 max-h-40 overflow-y-auto"}
            (for [r unique-routes]
              [:label {:class "flex items-center space-x-2 text-slate-300 hover:bg-slate-800/50 p-1 rounded cursor-pointer"}
               [:input {:type "checkbox" :checked true :onchange "applyAllFilters()" :class "routeFilterCheck rounded border-slate-700 bg-slate-950 text-sky-500" :value r}]
               [:span r]])]]]

         ;; Col 3: Schedule
         [:th {:class "px-3 py-2.5 whitespace-nowrap"} "日程・発着時刻"]

         ;; Col 4: Stops
         [:th {:class "px-3 py-2.5 whitespace-nowrap"} "乗継 / 所要時間"]

         ;; Col 5: Airline Filter
         [:th {:class "px-3 py-2.5 relative whitespace-nowrap"}
          [:div {:class "flex items-center space-x-1"}
           [:span "最安航空会社"]
           [:button {:type "button" :id "btnFilterAirline" :onclick "toggleFilterMenu(event, 'airlineDropdown')"
                     :title "航空会社で絞り込み" :class "p-1 rounded hover:bg-slate-800 hover:text-white transition flex items-center"}
            [:i {:data-lucide "filter" :class "w-3 h-3 text-slate-400"}]]]
          [:div {:id "airlineDropdown" :class "hidden absolute top-full left-2 mt-1 w-60 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl z-50 p-3 text-xs space-y-2.5 whitespace-normal"}
           [:div {:class "flex items-center justify-between border-b border-slate-800 pb-1.5"}
            [:span {:class "font-bold text-white"} "航空会社 絞り込み"]
            [:button {:type "button" :onclick "closeAllFilterMenus()" :class "text-slate-400 hover:text-white"} "✕"]]
           [:div {:class "space-y-1.5 max-h-40 overflow-y-auto"}
            (for [al (if (empty? unique-airlines) ["ANA" "JAL" "AF"] unique-airlines)]
              [:label {:class "flex items-center space-x-2 text-slate-300 hover:bg-slate-800/50 p-1 rounded cursor-pointer"}
               [:input {:type "checkbox" :checked true :onchange "applyAllFilters()" :class "airlineFilterCheck rounded border-slate-700 bg-slate-950 text-sky-500" :value al}]
               [:span al]])]]]

         ;; Col 6: Price Sort
         [:th {:class "px-3 py-2.5 text-right whitespace-nowrap"}
          [:div {:class "flex items-center justify-end space-x-1"}
           [:span "現在最安値"]
           [:button {:type "button" :onclick "toggleSortPrice()" :title "価格順ソート" :class "p-1 rounded hover:bg-slate-800 hover:text-white transition"}
            [:i {:data-lucide "arrow-up-down" :class "w-3 h-3 text-slate-400"}]]]]

         ;; Col 7: Target Price
         [:th {:class "px-3 py-2.5 text-right whitespace-nowrap"} "目標価格"]

         ;; Col 8: Notes
         [:th {:class "px-3 py-2.5 whitespace-nowrap"} "メモ / 要望"]

         ;; Col 9: Date
         [:th {:class "px-3 py-2.5 whitespace-nowrap"} "取得日時"]

         ;; Col 10: Actions
         [:th {:class "px-3 py-2.5 text-center whitespace-nowrap"} "操作"]]]]

       [:tbody {:id "tableBody" :class "divide-y divide-slate-800/80"}
        (for [t tasks]
          (let [is-target-met (domain/target-achieved? t)
                trip (:trip-type t)
                is-round (= (:kind trip) :round-trip)
                origin-str (domain/iata-code-value (:origin t))
                dest-str (domain/iata-code-value (:destination t))
                status (:status t)
                status-str (name status)
                [date-part time-part] (format-jst (:last-checked-at t))
                price-val (:last-lowest-price-jpy t)
                notes-val (or (:user-notes t) "")
                airlines-val (or (:last-lowest-airlines t) "")
                search-corpus (str origin-str " " dest-str " " (:title t) " " airlines-val " " notes-val " " status-str)]
            [:tr {:class "tableRow hover:bg-slate-900/60 transition"
                  :data-status status-str
                  :data-route (str origin-str "-" dest-str)
                  :data-airlines airlines-val
                  :data-search search-corpus
                  :data-price (str (or price-val 0))}
             ;; Status
             [:td {:class "px-3 py-2.5 whitespace-nowrap"}
              (cond
                is-target-met
                [:span {:class "inline-block px-2 py-0.5 rounded text-xs font-medium bg-emerald-950 text-emerald-300 border border-emerald-800/60 whitespace-nowrap"} "目標達成"]
                (= status :active)
                [:span {:class "inline-block px-2 py-0.5 rounded text-xs font-medium bg-sky-950 text-sky-300 border border-sky-800/60 whitespace-nowrap"} "監視中"]
                (= status :paused)
                [:span {:class "inline-block px-2 py-0.5 rounded text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 whitespace-nowrap"} "一時停止"]
                :else
                [:span {:class "inline-block px-2 py-0.5 rounded text-xs font-medium bg-rose-950 text-rose-300 border border-rose-800/60 whitespace-nowrap"} "エラー"])]
             ;; Route
             [:td {:class "px-3 py-2.5 whitespace-nowrap"}
              [:div {:class "font-bold text-white flex items-center space-x-1"}
               [:span origin-str]
               [:i {:data-lucide "arrow-right" :class "w-3 h-3 text-sky-400"}]
               [:span dest-str]]
              [:div {:class "text-[10px] text-slate-400 mt-0.5 truncate max-w-[140px]"} (:title t)]]
             ;; Schedule
             [:td {:class "px-3 py-2.5 whitespace-nowrap"}
              [:div {:class "text-white font-medium"}
               [:span {:class "px-1 py-0.2 rounded bg-sky-950 text-sky-300 border border-sky-800 text-[9px] mr-1"} "往"]
               (.ToString ^DateOnly (:outbound trip) "MM/dd")]
              (when is-round
                [:div {:class "text-slate-300 text-[11px] mt-0.5"}
                 [:span {:class "px-1 py-0.2 rounded bg-indigo-950 text-indigo-300 border border-indigo-800 text-[9px] mr-1"} "復"]
                 (.ToString ^DateOnly (:inbound trip) "MM/dd")
                 [:span {:class "text-slate-400 text-[9px] ml-0.5"} "(+1)"]])]
             ;; Stops
             [:td {:class "px-3 py-2.5 whitespace-nowrap"}
              [:div {:class "text-slate-200 font-medium"}
               (if (= (:max-stops t) :direct-only) "直行便のみ" "制限なし")]
              [:div {:class "text-[10px] text-slate-400 mt-0.5"} (str (:check-interval-hours t) "h間隔")]]
             ;; Airlines
             [:td {:class "px-3 py-2.5 whitespace-nowrap"}
              [:span {:class "text-slate-200 font-medium"} (if (not (str/blank? airlines-val)) airlines-val "未取得")]]
             ;; Price
             [:td {:class "px-3 py-2.5 text-right whitespace-nowrap"}
              [:div {:class (if is-target-met "font-bold text-emerald-400" "font-bold text-white")}
               (if price-val (str "¥" (.ToString (long price-val) "N0")) "---")]
              [:div {:class "mt-0.5"}
               [:span {:class "inline-block px-1.5 py-0.2 rounded bg-sky-950 border border-sky-800 text-sky-300 text-[9px] font-semibold"}
                (if-let [p (:last-lowest-provider t)] (domain/scraping-provider-to-string p) "---")]]]
             ;; Target
             [:td {:class "px-3 py-2.5 text-right text-slate-400 whitespace-nowrap"}
              (if-let [tp (:target-price-jpy t)] (str "¥" (.ToString (long tp) "N0")) "-")]
             ;; Notes
             [:td {:class "px-3 py-2.5 max-w-[160px] whitespace-nowrap"}
              [:div {:class "truncate text-[11px] text-slate-300 flex items-center space-x-1 cursor-pointer hover:text-sky-300"
                     :hx-get (str "/api/tasks/" (:id t) "/notes-modal")
                     :hx-target "#modal-container"}
               [:i {:data-lucide "file-text" :class "w-3.5 h-3.5 text-sky-400 shrink-0"}]
               [:span {:class "truncate"} (if (str/blank? notes-val) "(メモ追加)" notes-val)]]]
             ;; Date
             [:td {:class "px-3 py-2.5 text-slate-300 text-xs whitespace-nowrap"}
              [:div date-part]
              [:div {:class "text-[10px] text-slate-400 mt-0.5"} time-part]]
             ;; Actions
             [:td {:class "px-3 py-2.5 text-center whitespace-nowrap space-x-1 text-slate-400"}
              [:button {:type "button" :onclick (str "triggerImmediateRun('" (:id t) "')") :title "即時巡回" :class "hover:text-sky-400 p-1 rounded transition"}
               [:i {:data-lucide "refresh-cw" :class "w-3.5 h-3.5"}]]
              [:button {:type "button" :hx-get (str "/api/tasks/" (:id t) "/detail-modal") :hx-target "#modal-container" :title "詳細" :class "hover:text-sky-400 p-1 rounded transition"}
               [:i {:data-lucide "external-link" :class "w-3.5 h-3.5"}]]
              [:button {:type "button" :hx-get (str "/api/tasks/" (:id t) "/modal") :hx-target "#modal-container" :title "編集" :class "hover:text-white p-1 rounded transition"}
               [:i {:data-lucide "edit-3" :class "w-3.5 h-3.5"}]]
              [:button {:type "button" :hx-get (str "/api/tasks/" (:id t) "/delete-modal") :hx-target "#modal-container" :title "削除" :class "hover:text-rose-400 p-1 rounded transition"}
               [:i {:data-lucide "trash-2" :class "w-3.5 h-3.5"}]]]]))]]]))

(defn render-dashboard-content
  ([tasks] (render-dashboard-content tasks nil))
  ([tasks post-script]
   [:div {:id "dashboard-container"}
    ;; Challenge Banner
    (render-manual-challenge-banner)

    ;; AI Assistant Box
    (render-ai-assistant-box)

    ;; Filter Bar
    (render-filter-bar tasks)

    ;; Card View
    [:div {:id "cardsView" :class "grid grid-cols-1 lg:grid-cols-2 gap-5 mb-6"}
     (if (empty? tasks)
       (render-empty-state)
       (for [t tasks]
         (render-task-card t)))]

    ;; List View (Excel-like Table)
    (render-list-view tasks)

    ;; Dashboard Scripts
    [:script (h/raw "
      const state = {
        status: 'all',
        view: localStorage.getItem('ft_view') || 'cards',
        selectedRoutes: [],
        selectedAirlines: [],
        sortPriceAsc: false
      };

      const statusLabels = {
        all: 'すべて',
        active: '監視中',
        paused: '一時停止',
        error: 'エラー'
      };

      // Initialize route & airline filter selections
      function initStateFilters() {
        state.selectedRoutes = Array.from(document.querySelectorAll('.routeFilterCheck')).map(cb => cb.value);
        state.selectedAirlines = Array.from(document.querySelectorAll('.airlineFilterCheck')).map(cb => cb.value);
      }
      initStateFilters();

      function setStatusFilter(status) {
        state.status = status;
        closeAllFilterMenus();
        renderDashboard();
      }

      function switchView(view) {
        state.view = view;
        localStorage.setItem('ft_view', view);
        renderDashboard();
      }

      function renderDashboard() {
        const cardsView = document.getElementById('cardsView');
        const listView = document.getElementById('listView');
        const emptyStateView = document.getElementById('emptyStateView');
        const btnCards = document.getElementById('btnViewCards');
        const btnList = document.getElementById('btnViewList');

        // 1. Status Tab Styles
        ['all', 'active', 'paused', 'error', 'empty'].forEach(tab => {
          const el = document.getElementById('tab' + tab.charAt(0).toUpperCase() + tab.slice(1));
          if (el) {
            if (tab === state.status) {
              el.className = 'px-3 py-1.5 rounded-md text-xs font-medium bg-sky-600 text-white';
            } else {
              el.className = 'px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800';
            }
          }
        });

        // 2. Status Radios
        document.querySelectorAll('.statusRadio').forEach(r => {
          r.checked = (r.value === state.status);
        });

        // 3. View Toggle Buttons
        if (state.view === 'cards') {
          if (btnCards) btnCards.className = 'px-2.5 py-1 rounded text-xs font-medium bg-slate-800 text-white flex items-center space-x-1';
          if (btnList) btnList.className = 'px-2.5 py-1 rounded text-xs font-medium text-slate-400 hover:text-white flex items-center space-x-1';
        } else {
          if (btnList) btnList.className = 'px-2.5 py-1 rounded text-xs font-medium bg-slate-800 text-white flex items-center space-x-1';
          if (btnCards) btnCards.className = 'px-2.5 py-1 rounded text-xs font-medium text-slate-400 hover:text-white flex items-center space-x-1';
        }

        // 4. Empty State Mode
        if (state.status === 'empty') {
          if (cardsView) cardsView.classList.add('hidden');
          if (listView) listView.classList.add('hidden');
          if (emptyStateView) emptyStateView.classList.remove('hidden');
          updateFilterChipsBar(0);
          return;
        }
        if (emptyStateView) emptyStateView.classList.add('hidden');

        // 5. Filter Data
        const searchInput = document.getElementById('quickSearchInput');
        const query = (searchInput ? searchInput.value : '').trim().toLowerCase();
        let visibleCount = 0;

        document.querySelectorAll('.taskCard').forEach(card => {
          const cStatus = card.getAttribute('data-status') || '';
          const cRoute = card.getAttribute('data-route') || '';
          const rawAirlines = (card.getAttribute('data-airlines') || '').trim();
          const cAirlines = rawAirlines ? rawAirlines.split(/[,\\s/+]+/) : [];
          const cSearch = (card.getAttribute('data-search') || '').toLowerCase();

          const matchStatus = (state.status === 'all') || (cStatus === state.status);
          const matchRoute = state.selectedRoutes.length === 0 || state.selectedRoutes.includes(cRoute);
          const matchAirline = state.selectedAirlines.length === 0 || cAirlines.length === 0 || cAirlines.some(a => state.selectedAirlines.includes(a));
          const matchQuery = !query || cSearch.includes(query);

          if (matchStatus && matchRoute && matchAirline && matchQuery) {
            card.classList.remove('hidden');
            visibleCount++;
          } else {
            card.classList.add('hidden');
          }
        });

        document.querySelectorAll('.tableRow').forEach(row => {
          const rStatus = row.getAttribute('data-status') || '';
          const rRoute = row.getAttribute('data-route') || '';
          const rawAirlines = (row.getAttribute('data-airlines') || '').trim();
          const rAirlines = rawAirlines ? rawAirlines.split(/[,\\s/+]+/) : [];
          const rSearch = (row.getAttribute('data-search') || '').toLowerCase();

          const matchStatus = (state.status === 'all') || (rStatus === state.status);
          const matchRoute = state.selectedRoutes.length === 0 || state.selectedRoutes.includes(rRoute);
          const matchAirline = state.selectedAirlines.length === 0 || rAirlines.length === 0 || rAirlines.some(a => state.selectedAirlines.includes(a));
          const matchQuery = !query || rSearch.includes(query);

          if (matchStatus && matchRoute && matchAirline && matchQuery) {
            row.classList.remove('hidden');
          } else {
            row.classList.add('hidden');
          }
        });

        updateFilterChipsBar(visibleCount);

        if (state.view === 'cards') {
          if (cardsView) cardsView.classList.remove('hidden');
          if (listView) listView.classList.add('hidden');
        } else {
          if (cardsView) cardsView.classList.add('hidden');
          if (listView) listView.classList.remove('hidden');
        }

        if (window.lucide) lucide.createIcons();
      }

      function updateFilterChipsBar(visibleCount) {
        const totalCards = document.querySelectorAll('.taskCard').length;
        const countSummary = document.getElementById('filterCountSummary');
        if (countSummary) {
          countSummary.innerHTML = `表示中: <strong class=\"text-white\">${visibleCount}</strong> / 全${totalCards}件`;
        }

        const chipStatus = document.getElementById('chipStatus');
        const chipRoute = document.getElementById('chipRoute');
        const chipAirline = document.getElementById('chipAirline');
        const noFilter = document.getElementById('noFilterLabel');

        const allRoutesCount = document.querySelectorAll('.routeFilterCheck').length;
        const allAirlinesCount = document.querySelectorAll('.airlineFilterCheck').length;

        const isStatusFiltered = state.status !== 'all';
        const isRouteFiltered = allRoutesCount > 0 && state.selectedRoutes.length < allRoutesCount;
        const isAirlineFiltered = allAirlinesCount > 0 && state.selectedAirlines.length < allAirlinesCount;

        if (chipStatus) {
          if (isStatusFiltered) {
            chipStatus.classList.remove('hidden');
            const valEl = document.getElementById('chipStatusVal');
            if (valEl) valEl.innerText = statusLabels[state.status] || state.status;
            const btn = document.getElementById('btnFilterStatus');
            if (btn) btn.classList.add('text-sky-400', 'bg-slate-800');
          } else {
            chipStatus.classList.add('hidden');
            const btn = document.getElementById('btnFilterStatus');
            if (btn) btn.classList.remove('text-sky-400', 'bg-slate-800');
          }
        }

        if (chipRoute) {
          if (isRouteFiltered) {
            chipRoute.classList.remove('hidden');
            const valEl = document.getElementById('chipRouteVal');
            if (valEl) valEl.innerText = `${state.selectedRoutes.length}区間選択中`;
            const btn = document.getElementById('btnFilterRoute');
            if (btn) btn.classList.add('text-sky-400', 'bg-slate-800');
          } else {
            chipRoute.classList.add('hidden');
            const btn = document.getElementById('btnFilterRoute');
            if (btn) btn.classList.remove('text-sky-400', 'bg-slate-800');
          }
        }

        if (chipAirline) {
          if (isAirlineFiltered) {
            chipAirline.classList.remove('hidden');
            const valEl = document.getElementById('chipAirlineVal');
            if (valEl) valEl.innerText = `${state.selectedAirlines.length}社選択中`;
            const btn = document.getElementById('btnFilterAirline');
            if (btn) btn.classList.add('text-sky-400', 'bg-slate-800');
          } else {
            chipAirline.classList.add('hidden');
            const btn = document.getElementById('btnFilterAirline');
            if (btn) btn.classList.remove('text-sky-400', 'bg-slate-800');
          }
        }

        if (noFilter) {
          if (!isStatusFiltered && !isRouteFiltered && !isAirlineFiltered) {
            noFilter.classList.remove('hidden');
          } else {
            noFilter.classList.add('hidden');
          }
        }
      }

      function applyAllFilters() {
        state.selectedRoutes = Array.from(document.querySelectorAll('.routeFilterCheck:checked')).map(cb => cb.value);
        state.selectedAirlines = Array.from(document.querySelectorAll('.airlineFilterCheck:checked')).map(cb => cb.value);
        renderDashboard();
      }

      function clearRouteFilter() {
        document.querySelectorAll('.routeFilterCheck').forEach(cb => cb.checked = true);
        applyAllFilters();
      }

      function clearAirlineFilter() {
        document.querySelectorAll('.airlineFilterCheck').forEach(cb => cb.checked = true);
        applyAllFilters();
      }

      function resetAllFilters() {
        state.status = 'all';
        document.querySelectorAll('.routeFilterCheck, .airlineFilterCheck').forEach(cb => cb.checked = true);
        initStateFilters();
        const searchInput = document.getElementById('quickSearchInput');
        if (searchInput) searchInput.value = '';
        renderDashboard();
      }

      function toggleFilterMenu(e, menuId) {
        e.stopPropagation();
        const menu = document.getElementById(menuId);
        if (!menu) return;
        const isHidden = menu.classList.contains('hidden');
        closeAllFilterMenus();
        if (isHidden) {
          menu.classList.remove('hidden');
        }
      }

      function closeAllFilterMenus() {
        ['statusDropdown', 'routeDropdown', 'airlineDropdown'].forEach(id => {
          const el = document.getElementById(id);
          if (el) el.classList.add('hidden');
        });
      }

      document.addEventListener('click', e => {
        if (!e.target.closest('#statusDropdown') &&
            !e.target.closest('#routeDropdown') &&
            !e.target.closest('#airlineDropdown')) {
          closeAllFilterMenus();
        }
      });

      function toggleSortPrice() {
        state.sortPriceAsc = !state.sortPriceAsc;
        const tbody = document.getElementById('tableBody');
        if (!tbody) return;
        const rows = Array.from(tbody.querySelectorAll('.tableRow'));
        rows.sort((a, b) => {
          const pA = parseInt(a.getAttribute('data-price')) || 0;
          const pB = parseInt(b.getAttribute('data-price')) || 0;
          return state.sortPriceAsc ? pA - pB : pB - pA;
        });
        rows.forEach(r => tbody.appendChild(r));
        showToast(`価格を${state.sortPriceAsc ? '安い順 (昇順)' : '高い順 (降順)'}に並べ替えました`, true);
      }

      function triggerImmediateRun(taskId) {
        showToast('巡回リクエストを送信しました...', true);
        fetch('/api/tasks/' + taskId + '/run', { method: 'POST' })
          .then(r => {
            if (r.status === 409) {
              showToast('他のタスクが巡回中です。完了までお待ちください。', false);
              return null;
            }
            return r.text();
          })
          .then(html => {
            if (html) {
              const dash = document.getElementById('dashboard-container');
              if (dash) dash.outerHTML = html;
              if (window.lucide) lucide.createIcons();
              showToast('巡回が完了しました', true);
            }
          })
          .catch(err => showToast('巡回エラー: ' + err.message, false));
      }

      function parseWithAI() {
        const aiInput = document.getElementById('aiInput');
        const prompt = aiInput ? aiInput.value.trim() : '';
        if (!prompt) {
          showToast('AI解析用の条件を入力してください', false);
          return;
        }
        const btn = document.getElementById('btnParseWithAi') || ((typeof event !== 'undefined' && event && event.currentTarget) ? event.currentTarget : null);
        const origHtml = btn ? btn.innerHTML : '';
        if (btn) {
          btn.disabled = true;
          btn.innerHTML = '<i data-lucide=\"loader-2\" class=\"w-3.5 h-3.5 animate-spin\"></i><span>AI解析中...</span>';
          if (window.lucide) lucide.createIcons();
        }
        showToast('AI解析を実行中...', true);
        fetch('/api/tasks/new-modal?prompt=' + encodeURIComponent(prompt))
          .then(async r => {
            if (!r.ok) throw new Error(await r.text() || `HTTP ${r.status}`);
            return r.text();
          })
          .then(html => {
            const container = document.getElementById('modal-container');
            if (container) {
              container.innerHTML = html;
              if (window.htmx) htmx.process(container);
              if (window.openModalSync) window.openModalSync(true);
            }
          })
          .catch(err => {
            showToast('AI解析に失敗しました: ' + err.message, false);
          })
          .finally(() => {
            if (btn) {
              btn.disabled = false;
              btn.innerHTML = origHtml;
              if (window.lucide) lucide.createIcons();
            }
          });
      }

      function focusAiInput() {
        const aiInput = document.getElementById('aiInput');
        if (aiInput) {
          aiInput.scrollIntoView({ behavior: 'smooth' });
          aiInput.focus();
        }
      }

      function simulateResolveChallenge() {
        const banner = document.getElementById('manualChallengeBanner');
        if (banner) {
          banner.style.opacity = '0';
          setTimeout(() => banner.style.display = 'none', 300);
          showToast('手動認証解除を検知しました。巡回を再開します。', true);
        }
      }

      function dismissChallengeBanner() {
        const banner = document.getElementById('manualChallengeBanner');
        if (banner) banner.style.display = 'none';
      }

      // Initial execution on load
      renderDashboard();
    ")]
    (when post-script
      [:script (h/raw post-script)])]))
