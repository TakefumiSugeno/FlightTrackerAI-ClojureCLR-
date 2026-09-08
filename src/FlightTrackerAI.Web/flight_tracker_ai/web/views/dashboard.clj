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
    [:i {:class "fa-solid fa-plane-slash text-2xl"}]]
   [:h3 {:class "text-base font-bold text-slate-100"} "監視中のタスクはありません"]
   [:p {:class "text-xs text-slate-400 mt-1 max-w-sm mx-auto leading-relaxed"}
    "上部のAI自然言語入力、または「新規タスク登録」ボタンから監視したい航空券ルートを登録してください。"]
   [:div {:class "mt-5 flex items-center justify-center gap-3"}
    [:button {:type "button"
              :class "bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold px-4 py-2 rounded-lg shadow-sm transition inline-flex items-center gap-1.5"
              :onclick "openNewTaskModal()"}
     [:i {:class "fa-solid fa-plus text-xs"}]
     "タスクを登録する"]
    [:button {:type "button"
              :class "bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold px-4 py-2 rounded-lg border border-slate-700 transition inline-flex items-center gap-1.5"
              :onclick "focusAiInput()"}
     [:i {:class "fa-solid fa-wand-magic-sparkles text-xs text-sky-400"}]
     "AIアシスタントへ移動"]]])

(defn render-manual-challenge-banner []
  [:div {:id "manualChallengeBanner"
         :class "bg-amber-950/80 border border-amber-500/50 rounded-xl p-4 shadow-lg flex flex-col md:flex-row items-start md:items-center justify-between gap-4 transition-all mb-6"}
   [:div {:class "flex items-start space-x-3"}
    [:div {:class "p-2 bg-amber-500/20 text-amber-400 rounded-lg shrink-0 mt-0.5 md:mt-0 animate-pulse"}
     [:i {:class "fa-solid fa-shield-halved text-lg"}]]
    [:div
     [:div {:class "flex items-center space-x-2"}
      [:span {:class "text-sm font-bold text-amber-300"}
       "Skyscanner 認証チャレンジ（PRESS & HOLD）を検知しました"]
      [:span {:id "challengeCountdownBadge"
              :class "px-2 py-0.5 text-[10px] font-mono font-bold bg-amber-900/60 text-amber-200 border border-amber-600/50 rounded-full animate-pulse"}
       "残り " [:span {:id "challengeSeconds"} "58"] " 秒"]]
     [:p {:class "text-xs text-slate-300 mt-1 leading-relaxed"}
      "Kasada/PerimeterX によるセキュリティ検証が発生しています。画面上のブラウザウィンドウで「長押し（PRESS & HOLD）」を手動解除してください。解除を検知すると自動で巡回が再開されます。"]]]
   [:div {:class "flex items-center space-x-2 shrink-0 self-end md:self-auto"}
    [:button {:type "button"
              :onclick "simulateResolveChallenge()"
              :class "px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-semibold shadow transition flex items-center space-x-1"}
     [:i {:class "fa-solid fa-circle-check text-xs"}]
     [:span "手動解除シミュレート"]]
    [:button {:type "button"
              :onclick "dismissChallengeBanner()"
              :class "p-1.5 text-slate-400 hover:text-slate-200 rounded-lg transition"
              :title "非表示"}
     [:i {:class "fa-solid fa-xmark text-sm"}]]]])

(defn render-ai-assistant-box []
  [:div {:class "bg-slate-950 border border-slate-800 rounded-xl p-4 shadow-sm space-y-3 mb-6"}
   [:div {:class "flex flex-col sm:flex-row sm:items-center justify-between gap-2"}
    [:div {:class "flex items-center space-x-2 text-xs font-semibold text-sky-400"}
     [:i {:class "fa-solid fa-wand-magic-sparkles text-xs"}]
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
              :onclick "parseWithAI()"
              :class "bg-sky-600 hover:bg-sky-500 active:scale-95 transition text-white px-4 py-2 rounded-lg text-xs font-semibold flex items-center justify-center space-x-1.5 whitespace-nowrap shadow-sm"}
     [:i {:class "fa-solid fa-wand-magic-sparkles text-xs"}]
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
       [:i {:class "fa-solid fa-magnifying-glass w-3.5 h-3.5 absolute left-3 top-2.5 text-slate-400 text-xs"}]
       [:input {:type "text"
                :id "quickSearchInput"
                :oninput "applyAllFilters()"
                :placeholder "都市・空港・航空会社で検索..."
                :class "bg-slate-900 border border-slate-700 rounded-lg pl-8 pr-3 py-1.5 text-xs text-white placeholder-slate-500 w-full sm:w-56 focus:outline-none focus:ring-1 focus:ring-sky-500"}]]
      [:div {:class "flex bg-slate-900 border border-slate-700 rounded-lg p-0.5"}
       [:button {:type "button" :id "btnViewCards" :onclick "switchView('cards')"
                 :class "px-2.5 py-1 rounded text-xs font-medium bg-slate-800 text-white flex items-center space-x-1"}
        [:i {:class "fa-solid fa-border-all text-xs"}]
        [:span {:class "hidden sm:inline"} "カード"]]
       [:button {:type "button" :id "btnViewList" :onclick "switchView('list')"
                 :class "px-2.5 py-1 rounded text-xs font-medium text-slate-400 hover:text-white flex items-center space-x-1"}
        [:i {:class "fa-solid fa-table-list text-xs"}]
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
        card-class (str "taskCard bg-slate-950 border rounded-xl p-5 shadow-sm transition flex flex-col justify-between "
                        (if (= status :error)
                          "border-rose-900/60"
                          "border-slate-800 hover:border-slate-700"))
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
           [:i {:class "fa-solid fa-circle-check text-xs"}]
           [:span "🎯 目標達成"]]
          (= status :active)
          [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-sky-950 text-sky-300 border border-sky-800/60 flex items-center space-x-1"}
           [:span "● 監視中"]]
          (= status :paused)
          [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 flex items-center space-x-1"}
           [:i {:class "fa-solid fa-circle-pause text-xs"}]
           [:span "一時停止中"]]
          :else
          [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-rose-950 text-rose-300 border border-rose-800/60 flex items-center space-x-1"}
           [:i {:class "fa-solid fa-triangle-exclamation text-xs"}]
           [:span "巡回エラー"]])
        [:span {:class "text-xs text-slate-400"}
         (str (if is-round "往復" "片道") " (" (:check-interval-hours task-item) "h毎巡回)")]]
       [:div {:class "flex items-center space-x-1 text-slate-400"}
        [:button {:type "button"
                  :onclick (str "toggleTaskStatus('" (:id task-item) "')")
                  :title (if (= status :paused) "巡回再開" "一時停止")
                  :class (str "p-1.5 hover:bg-slate-800 rounded transition " (if (= status :paused) "text-amber-400 hover:text-amber-300" "text-slate-400 hover:text-amber-400"))}
         [:i {:class (if (= status :paused) "fa-solid fa-play text-xs" "fa-solid fa-pause text-xs")}]]
        [:button {:type "button"
                  :onclick (str "triggerImmediateRun('" (:id task-item) "')")
                  :title (if (= status :error) "手動再試行" "即時巡回 (ヘッドレス)")
                  :class (str "p-1.5 hover:bg-slate-800 rounded transition " (if (= status :error) "text-rose-400 hover:text-rose-300" "hover:text-sky-400"))}
         [:i {:class "fa-solid fa-arrows-rotate text-xs"}]]
        [:button {:type "button"
                  :onclick (str "triggerImmediateRunWithBrowser('" (:id task-item) "')")
                  :title "ブラウザを開いて巡回 (ユーザー手動支援)"
                  :class "p-1.5 text-slate-400 hover:text-sky-300 hover:bg-slate-800 rounded transition"}
         [:i {:class "fa-solid fa-window-restore text-sky-400 text-xs"}]]
        [:button {:type "button"
                  :onclick (str "openEditModal('" (:id task-item) "')")
                  :title "タスク設定変更"
                  :class "p-1.5 hover:text-white hover:bg-slate-800 rounded transition"}
         [:i {:class "fa-solid fa-pen-to-square text-xs"}]]
        [:button {:type "button"
                  :onclick (str "openDeleteModal('" (:id task-item) "', '" origin-str " ➔ " dest-str "')")
                  :title "削除"
                  :class "p-1.5 hover:text-rose-400 hover:bg-slate-800 rounded transition"}
         [:i {:class "fa-solid fa-trash-can text-xs"}]]]]

      ;; Route & Dates
      [:div {:class "mt-3 flex items-start justify-between"}
       [:div
        [:div {:class "text-xl font-bold text-white flex items-center space-x-2"}
         [:span origin-str]
         [:i {:class "fa-solid fa-arrow-right text-xs text-sky-400"}]
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

      ;; Error reason box if error
      (when (= status :error)
        [:div {:class "mt-4 p-3 rounded-lg bg-rose-950/40 border border-rose-800/40 text-xs text-rose-300"}
         [:span {:class "font-bold"} "エラー理由: "]
         (or (:ai-analysis-summary task-item)
             "巡回タイムアウトまたは接続失敗。次回定期実行で自動再試行されます。")])

      ;; Price summary
      [:div {:class "mt-4 pt-3 border-t border-slate-800/80 grid grid-cols-2 gap-2"}
       [:div
        [:div {:class "text-xs text-slate-400"} "最安航空会社"]
        [:div {:class "text-sm font-semibold text-white mt-0.5 flex items-center space-x-1.5"}
         [:span {:class (if (= status :active) "w-2 h-2 rounded-full bg-sky-400" "w-2 h-2 rounded-full bg-slate-500")}]
         [:span (if (not (str/blank? airlines-val)) airlines-val "未取得")]]
        [:div {:class "text-xs text-slate-500 mt-1 flex items-center space-x-1"}
         [:i {:class "fa-regular fa-clock text-[10px]"}]
         [:span (str "取得: " date-part " " time-part)]]]
       [:div {:class "text-right"}
        [:div {:class "text-xs text-slate-400"}
         "現在最安値 (" [:span {:class "text-sky-400 font-medium"} provider-name] ")"]
        [:div {:class (if is-target-met "text-xl font-bold text-emerald-400 font-mono" "text-xl font-bold text-white font-mono")} price-str]
        [:div {:class "text-xs text-slate-400"}
         (if target-price (str "目標: ¥" (.ToString (long target-price) "N0") " 以下") "目標未設定")]]]

      ;; User Note Box
      [:div {:class "mt-3 p-2 bg-slate-900/90 border border-slate-800 rounded-lg text-[11px] text-slate-300 flex items-center justify-between"}
       [:div {:class "flex items-center space-x-1.5 truncate flex-1 mr-2"}
        [:i {:class "fa-solid fa-file-lines text-sky-400 shrink-0 text-xs"}]
        [:span {:class "truncate font-mono"}
         (if (str/blank? notes-val) "メモ: (未入力)" (str "メモ: " notes-val))]]
       [:button {:type "button"
                 :onclick (str "openQuickNoteModal('" (:id task-item) "', '" (str/replace notes-val "'" "\\'") "', '" origin-str " ➔ " dest-str "')")
                 :title "メモを編集"
                 :class "text-slate-400 hover:text-white shrink-0 p-1 hover:bg-slate-800 rounded transition"}
        [:i {:class "fa-solid fa-pen text-xs"}]]]]

     ;; Footer
     [:div {:class "mt-4 pt-3 border-t border-slate-800 flex items-center justify-between"}
      (if (= status :error)
        [:button {:type "button"
                  :onclick (str "triggerImmediateRun('" (:id task-item) "')")
                  :class "px-3 py-1.5 bg-rose-600/30 hover:bg-rose-600 text-rose-200 hover:text-white rounded-lg text-xs font-semibold flex items-center space-x-1.5 transition"}
         [:i {:class "fa-solid fa-rotate-right text-xs"}]
         [:span "今すぐ再試行"]]
        [:div {:class "flex items-center space-x-2 text-xs"}
         [:span {:class "px-2 py-0.5 rounded bg-emerald-950 border border-emerald-800 text-emerald-300 font-medium text-[10px]"} "Google"]
         [:span {:class "px-2 py-0.5 rounded bg-sky-950 border border-sky-800 text-sky-300 font-medium text-[10px]"} "Skyscanner"]])
      [:button {:type "button"
                :onclick (str "openDetailModal('" (:id task-item) "')")
                :class "text-xs font-semibold text-sky-400 hover:text-sky-300 flex items-center space-x-1"}
       [:span "所要時間・乗継詳細 & 便比較"]
       [:i {:class "fa-solid fa-chevron-right text-xs"}]]]]))

(defn render-list-view [tasks]
  [:div {:id "listView" :class "hidden bg-slate-950 border border-slate-800 rounded-xl overflow-visible shadow-sm"}
   ;; Active Filters Chips Bar
   [:div {:id "activeFilterChipsBar"
          :class "px-4 py-2.5 bg-slate-900/90 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2 text-xs"}
    [:div {:class "flex items-center space-x-2 flex-wrap gap-1.5"}
     [:span {:class "text-slate-400 flex items-center space-x-1 font-medium"}
      [:i {:class "fa-solid fa-filter text-sky-400 text-xs"}]
      [:span "適用中のフィルタ:"]]
     [:span {:id "chipStatus" :class "hidden inline-flex items-center px-2 py-0.5 rounded-md bg-sky-950 text-sky-300 border border-sky-800 text-[11px]"}
      [:span "ステータス: " [:strong {:id "chipStatusVal"} "すべて"]]
      [:button {:type "button" :onclick "setStatusFilter('all')" :class "ml-1 text-sky-400 hover:text-white"} "✕"]]
     [:span {:id "noFilterLabel" :class "text-slate-500 italic text-[11px]"}
      "（すべてのタスクを表示中。上のタブまたは検索窓から絞り込めます）"]]
    [:div {:class "flex items-center space-x-3 text-slate-400 text-[11px]"}
     [:span {:id "filterCountSummary"}
      "表示中: " [:strong {:class "text-white" :id "filterCountNum"} (str (count tasks))] (str " / 全" (count tasks) "件")]
     [:button {:type "button" :onclick "resetAllFilters()" :class "text-sky-400 hover:text-sky-300 underline font-medium"}
      "全解除"]]]

   ;; Table
   [:div {:class "overflow-x-auto"}
    [:table {:class "w-full text-left text-xs text-slate-300"}
     [:thead {:class "bg-slate-900 text-slate-400 font-semibold border-b border-slate-800 select-none whitespace-nowrap"}
      [:tr
       [:th {:class "px-3 py-2.5"} "ステータス"]
       [:th {:class "px-3 py-2.5"} "区間"]
       [:th {:class "px-3 py-2.5"} "日程"]
       [:th {:class "px-3 py-2.5"} "乗継 / 所要"]
       [:th {:class "px-3 py-2.5"} "最安航空会社"]
       [:th {:class "px-3 py-2.5 text-right"} "現在最安値"]
       [:th {:class "px-3 py-2.5 text-right"} "目標価格"]
       [:th {:class "px-3 py-2.5"} "メモ"]
       [:th {:class "px-3 py-2.5"} "最終取得"]
       [:th {:class "px-3 py-2.5 text-center"} "操作"]]]
     [:tbody {:class "divide-y divide-slate-800/80"}
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
              [:span {:class "inline-block px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-950 text-emerald-300 border border-emerald-800"} "🎯 目標達成"]
              (= status :active)
              [:span {:class "inline-block px-2 py-0.5 rounded text-[10px] font-medium bg-sky-950 text-sky-300 border border-sky-800"} "監視中"]
              (= status :paused)
              [:span {:class "inline-block px-2 py-0.5 rounded text-[10px] font-medium bg-slate-800 text-slate-300 border border-slate-700"} "一時停止"]
              :else
              [:span {:class "inline-block px-2 py-0.5 rounded text-[10px] font-medium bg-rose-950 text-rose-300 border border-rose-800"} "エラー"])]
           ;; Route
           [:td {:class "px-3 py-2.5 whitespace-nowrap"}
            [:div {:class "font-bold text-white flex items-center space-x-1"}
             [:span origin-str]
             [:i {:class "fa-solid fa-arrow-right text-[10px] text-sky-400"}]
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
           ;; Stops/Duration
           [:td {:class "px-3 py-2.5 whitespace-nowrap"}
            [:div {:class "text-slate-200 font-medium"}
             (if (= (:max-stops t) :direct-only) "直行便のみ" "制限なし")]
            [:div {:class "text-[10px] text-slate-400 mt-0.5"} (str (:check-interval-hours t) "h間隔")]]
           ;; Airlines
           [:td {:class "px-3 py-2.5 whitespace-nowrap"}
            [:span {:class "text-slate-200 font-medium"} (if (not (str/blank? airlines-val)) airlines-val "未取得")]]
           ;; Price & Provider
           [:td {:class "px-3 py-2.5 text-right whitespace-nowrap"}
            [:div {:class (if is-target-met "font-bold text-emerald-400 font-mono" "font-bold text-white font-mono")}
             (if price-val (str "¥" (.ToString (long price-val) "N0")) "---")]
            [:div {:class "mt-0.5"}
             [:span {:class "inline-block px-1.5 py-0.2 rounded bg-sky-950 border border-sky-800 text-sky-300 text-[9px] font-semibold"}
              (if-let [p (:last-lowest-provider t)] (domain/scraping-provider-to-string p) "---")]]]
           ;; Target Price
           [:td {:class "px-3 py-2.5 text-right text-slate-400 whitespace-nowrap font-mono"}
            (if-let [tp (:target-price-jpy t)] (str "¥" (.ToString (long tp) "N0")) "-")]
           ;; Notes (Click opens quick note modal)
           [:td {:class "px-3 py-2.5 max-w-[160px] whitespace-nowrap"}
            [:div {:class "truncate text-[11px] text-slate-300 flex items-center space-x-1 cursor-pointer hover:text-sky-300"
                   :onclick (str "openQuickNoteModal('" (:id t) "', '" (str/replace notes-val "'" "\\'") "', '" origin-str " ➔ " dest-str "')")}
             [:i {:class "fa-solid fa-file-lines text-sky-400 text-xs shrink-0"}]
             [:span {:class "truncate"} (if (str/blank? notes-val) "(メモ追加)" notes-val)]]]
           ;; Last Checked
           [:td {:class "px-3 py-2.5 text-slate-300 text-xs whitespace-nowrap"}
            [:div date-part]
            [:div {:class "text-[10px] text-slate-400 mt-0.5"} time-part]]
           ;; Actions
           [:td {:class "px-3 py-2.5 text-center whitespace-nowrap space-x-1"}
            [:button {:type "button" :onclick (str "toggleTaskStatus('" (:id t) "')") :title (if (= status :paused) "再開" "停止") :class (if (= status :paused) "text-amber-400 hover:text-amber-300 text-xs p-1" "text-slate-400 hover:text-amber-400 text-xs p-1")}
             [:i {:class (if (= status :paused) "fa-solid fa-play" "fa-solid fa-pause")}]]
            [:button {:type "button" :onclick (str "triggerImmediateRun('" (:id t) "')") :title "即時巡回 (ヘッドレス)" :class "text-slate-400 hover:text-sky-400 text-xs p-1"}
             [:i {:class "fa-solid fa-arrows-rotate"}]]
            [:button {:type "button" :onclick (str "triggerImmediateRunWithBrowser('" (:id t) "')") :title "ブラウザ巡回 (手動支援)" :class "text-sky-400 hover:text-sky-300 text-xs p-1"}
             [:i {:class "fa-solid fa-window-restore"}]]
            [:button {:type "button" :onclick (str "openDetailModal('" (:id t) "')") :class "text-sky-400 hover:underline text-xs ml-1"} "詳細"]
            [:button {:type "button" :onclick (str "openEditModal('" (:id t) "')") :class "text-slate-400 hover:underline text-xs ml-1"} "編集"]
            [:button {:type "button" :onclick (str "openDeleteModal('" (:id t) "', '" origin-str " ➔ " dest-str "')") :class "text-rose-400 hover:underline text-xs ml-1"} "削除"]]]))]]]])

(defn render-dashboard-content [tasks]
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
     let currentStatusFilter = 'all';

     function switchView(viewName) {
       const cardsView = document.getElementById('cardsView');
       const listView = document.getElementById('listView');
       const btnCards = document.getElementById('btnViewCards');
       const btnList = document.getElementById('btnViewList');
       if (!cardsView || !listView) return;

       if (viewName === 'list') {
         cardsView.classList.add('hidden');
         listView.classList.remove('hidden');
         btnList.classList.add('bg-slate-800', 'text-white');
         btnList.classList.remove('text-slate-400');
         btnCards.classList.remove('bg-slate-800', 'text-white');
         btnCards.classList.add('text-slate-400');
         localStorage.setItem('ft_view', 'list');
       } else {
         listView.classList.add('hidden');
         cardsView.classList.remove('hidden');
         btnCards.classList.add('bg-slate-800', 'text-white');
         btnCards.classList.remove('text-slate-400');
         btnList.classList.remove('bg-slate-800', 'text-white');
         btnList.classList.add('text-slate-400');
         localStorage.setItem('ft_view', 'cards');
       }
     }

     function setStatusFilter(status) {
       currentStatusFilter = status;
       const tabs = ['all', 'active', 'paused', 'error', 'empty'];
       tabs.forEach(t => {
         const btn = document.getElementById('tab' + t.charAt(0).toUpperCase() + t.slice(1));
         if (btn) {
           if (t === status) {
             btn.className = 'px-3 py-1.5 rounded-md text-xs font-medium bg-sky-600 text-white';
           } else {
             btn.className = 'px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800';
           }
         }
       });

       const chipStatus = document.getElementById('chipStatus');
       const chipVal = document.getElementById('chipStatusVal');
       const noFilter = document.getElementById('noFilterLabel');
       if (chipStatus && chipVal) {
         if (status === 'all') {
           chipStatus.classList.add('hidden');
           if (noFilter) noFilter.classList.remove('hidden');
         } else {
           chipStatus.classList.remove('hidden');
           chipVal.textContent = status;
           if (noFilter) noFilter.classList.add('hidden');
         }
       }

       applyAllFilters();
     }

     function applyAllFilters() {
       const searchInput = document.getElementById('quickSearchInput');
       const query = (searchInput ? searchInput.value : '').toLowerCase().trim();

       let visibleCount = 0;
       const cards = document.querySelectorAll('.taskCard');
       const rows = document.querySelectorAll('.tableRow');

       const emptyState = document.getElementById('emptyStateView');
       if (currentStatusFilter === 'empty') {
         cards.forEach(c => c.style.display = 'none');
         rows.forEach(r => r.style.display = 'none');
         if (emptyState) emptyState.style.display = 'block';
         updateCountSummary(0);
         return;
       }

       cards.forEach(card => {
         const s = card.getAttribute('data-status') || '';
         const searchCorpus = (card.getAttribute('data-search') || '').toLowerCase();
         const matchStatus = (currentStatusFilter === 'all') || (s === currentStatusFilter);
         const matchQuery = !query || searchCorpus.includes(query);
         if (matchStatus && matchQuery) {
           card.style.display = '';
           visibleCount++;
         } else {
           card.style.display = 'none';
         }
       });

       rows.forEach(row => {
         const s = row.getAttribute('data-status') || '';
         const searchCorpus = (row.getAttribute('data-search') || '').toLowerCase();
         const matchStatus = (currentStatusFilter === 'all') || (s === currentStatusFilter);
         const matchQuery = !query || searchCorpus.includes(query);
         if (matchStatus && matchQuery) {
           row.style.display = '';
         } else {
           row.style.display = 'none';
         }
       });

       updateCountSummary(visibleCount);
     }

     function updateCountSummary(count) {
       const numElem = document.getElementById('filterCountNum');
       if (numElem) numElem.textContent = count;
     }

     function resetAllFilters() {
       const searchInput = document.getElementById('quickSearchInput');
       if (searchInput) searchInput.value = '';
       setStatusFilter('all');
     }

     function insertTemplate(type) {
       const aiInput = document.getElementById('aiInput');
       if (!aiInput) return;
       if (type === 'markdown') {
         aiInput.value = '- 出発地: 東京 (羽田 / HND)\\n- 目的地: パリ (CDG)\\n- 往路日: 2026/05/01, 復路日: 2026/05/08\\n- 乗継: 直行便のみ\\n- 目標予算: 160,000 円以下\\n- 希望航空会社: ANA, JAL, エールフランス';
       } else if (type === 'yaml') {
         aiInput.value = 'trip: round_trip\\norigin: HND\\ndestination: CDG\\noutbound_date: 2026-05-01\\ninbound_date: 2026-05-08\\nmax_stops: direct_only\\ntarget_price: 160000\\npreferred_airlines:\\n  - ANA\\n  - JAL';
       } else {
         aiInput.value = '5月1日から8日まで東京からパリへの往復航空券を探しています。直行便希望で予算は16万円以内です。';
       }
       aiInput.focus();
     }

     function parseWithAI() {
       const aiInput = document.getElementById('aiInput');
       const prompt = aiInput ? aiInput.value.trim() : '';
       if (!prompt) {
         showToast('AI解析用の条件を入力してください', false);
         return;
       }
       showToast('AI解析を実行中...', true);
       fetch('/api/tasks/new-modal?prompt=' + encodeURIComponent(prompt))
         .then(r => r.text())
         .then(html => {
           const container = document.getElementById('modal-container');
           if (container) container.innerHTML = html;
         })
         .catch(err => {
           showToast('AI解析に失敗しました: ' + err.message, false);
         });
     }

     function focusAiInput() {
       const aiInput = document.getElementById('aiInput');
       if (aiInput) {
         aiInput.scrollIntoView({ behavior: 'smooth' });
         aiInput.focus();
       }
     }

     function openQuickNoteModal(taskId, currentNote, title) {
       fetch('/api/tasks/' + taskId + '/quick-note-modal')
         .then(r => r.text())
         .then(html => {
           const container = document.getElementById('modal-container');
           if (container) container.innerHTML = html;
         });
     }

     function openDetailModal(taskId) {
       fetch('/api/tasks/' + taskId + '/detail')
         .then(r => r.text())
         .then(html => {
           const container = document.getElementById('modal-container');
           if (container) container.innerHTML = html;
         });
     }

     function openEditModal(taskId) {
       fetch('/api/tasks/' + taskId + '/edit-modal')
         .then(r => r.text())
         .then(html => {
           const container = document.getElementById('modal-container');
           if (container) container.innerHTML = html;
         });
     }

     function openNewTaskModal() {
       fetch('/api/tasks/new-modal')
         .then(r => r.text())
         .then(html => {
           const container = document.getElementById('modal-container');
           if (container) container.innerHTML = html;
         });
     }

     function openDeleteModal(taskId, routeStr) {
       if (confirm(`監視タスク「${routeStr}」を削除しますか？`)) {
         fetch('/api/tasks/' + taskId, { method: 'DELETE' })
           .then(r => r.text())
           .then(html => {
             const dash = document.getElementById('dashboard-container');
             if (dash) dash.outerHTML = html;
             showToast('タスクを削除しました', true);
           });
       }
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
             showToast('巡回が完了しました', true);
           }
         })
         .catch(err => showToast('巡回エラー: ' + err.message, false));
     }

      function toggleTaskStatus(taskId) {
        showToast('ステータスを切り替えています...', true);
        fetch('/api/tasks/' + taskId + '/toggle-status', { method: 'POST' })
          .then(r => r.text())
          .then(html => {
            const dash = document.getElementById('dashboard-container');
            if (dash) dash.outerHTML = html;
            showToast('タスクステータスを更新しました', true);
          })
          .catch(err => showToast('更新失敗: ' + err.message, false));
      }

      function triggerImmediateRunWithBrowser(taskId) {
        showToast('ブラウザを表示して巡回を開始します (手動支援モード)...', true);
        fetch('/api/tasks/' + taskId + '/run?headless=false', { method: 'POST' })
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
              showToast('有頭ブラウザ巡回が完了しました', true);
            }
          })
          .catch(err => showToast('巡回エラー: ' + err.message, false));
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

     // Initial view restoration
     if (localStorage.getItem('ft_view') === 'list') {
       switchView('list');
     }
  ")]])
