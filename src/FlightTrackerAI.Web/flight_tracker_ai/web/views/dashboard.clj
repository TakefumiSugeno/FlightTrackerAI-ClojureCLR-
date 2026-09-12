(ns flight-tracker-ai.web.views.dashboard
  (:require [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [clojure.string :as str])
  (:import [System DateTimeOffset TimeSpan DateOnly]))

(defn- format-jst [^DateTimeOffset dto-opt]
  (if dto-opt
    (let [jst (.ToOffset dto-opt (TimeSpan/FromHours 9.0))]
      [(.ToString jst "yyyy/MM/dd") (str (.ToString jst "HH:mm") " JST")])
    ["-" "未巡回"]))

(defn render-empty-state []
  [:div {:class "text-center py-16 bg-slate-950 rounded-2xl border border-dashed border-slate-800 my-6"}
   [:div {:class "w-14 h-14 bg-sky-950/60 border border-sky-800/40 rounded-2xl flex items-center justify-center mx-auto text-sky-400 mb-4"}
    [:i {:class "fa-solid fa-plane-slash text-2xl"}]]
   [:h3 {:class "text-base font-bold text-slate-100"} "監視中のタスクはありません"]
   [:p {:class "text-xs text-slate-400 mt-1 max-w-sm mx-auto leading-relaxed"}
    "上部のAI自然言語入力、または「新規タスク登録」ボタンから監視したい航空券ルートを登録してください。"]
   [:button {:class "mt-5 bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold px-4 py-2 rounded-lg shadow-sm transition inline-flex items-center gap-1.5"
             :hx-get "/api/tasks/new-modal"
             :hx-target "#modal-container"}
    [:i {:class "fa-solid fa-plus text-xs"}]
    "タスクを登録する"]])

(defn render-task-card [task-item]
  (let [id-str (str (:id task-item))
        title (or (:title task-item) "名称未設定")
        status (:status task-item)
        target-price (:target-price-jpy task-item)
        lowest-price (:last-lowest-price-jpy task-item)
        is-price-met (and target-price lowest-price (<= lowest-price target-price))
        trip (:trip-type task-item)
        dates-str (if (= (:kind trip) :round-trip)
                    (str (when-let [d (:outbound-date trip)] (.ToString ^DateOnly d "yyyy/MM/dd"))
                         " - "
                         (when-let [d (:inbound-date trip)] (.ToString ^DateOnly d "yyyy/MM/dd"))
                         " (往復)")
                    (str (when-let [d (:outbound-date trip)] (.ToString ^DateOnly d "yyyy/MM/dd"))
                         " (片道)"))
        [date-part time-part] (format-jst (:last-checked-at task-item))
        origin-str (domain/iata-code-value (:origin task-item))
        dest-str (domain/iata-code-value (:destination task-item))
        stops-label (case (:max-stops task-item)
                      :direct-only "直行便のみ"
                      :one-stop "経由1回まで"
                      "経由無制限")
        provider (:last-lowest-provider task-item)
        notes (or (:user-notes task-item) "")]
    [:div {:class "bg-slate-950 border border-slate-800 rounded-xl p-4 shadow-sm hover:border-slate-700 transition flex flex-col justify-between relative overflow-hidden"}
     ;; ステータス上部バー
     [:div {:class "flex items-start justify-between gap-2 mb-3"}
      [:div
       [:div {:class "flex items-center gap-2"}
        [:span {:class (case status
                         :active "w-2.5 h-2.5 rounded-full bg-emerald-500 inline-block animate-pulse"
                         :paused "w-2.5 h-2.5 rounded-full bg-amber-400 inline-block"
                         :completed "w-2.5 h-2.5 rounded-full bg-slate-500 inline-block"
                         "w-2.5 h-2.5 rounded-full bg-rose-500 inline-block")}]
        [:h3 {:class "font-bold text-white text-sm leading-tight"} title]
        [:span {:class (case status
                         :active "text-[10px] font-semibold px-1.5 py-0.5 rounded bg-emerald-950/80 text-emerald-400 border border-emerald-800/50"
                         :paused "text-[10px] font-semibold px-1.5 py-0.5 rounded bg-amber-950/80 text-amber-400 border border-amber-800/50"
                         :completed "text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-900 text-slate-400 border border-slate-700"
                         "text-[10px] font-semibold px-1.5 py-0.5 rounded bg-rose-950/80 text-rose-400 border border-rose-800/50")}
         (case status
           :active "監視中"
           :paused "一時停止"
           :completed "完了"
           "エラー")]
        (when is-price-met
          [:span {:class "text-[10px] font-bold px-1.5 py-0.5 rounded bg-emerald-900/90 text-emerald-300 border border-emerald-500/60 flex items-center gap-1"}
           [:i {:class "fa-solid fa-circle-check text-[9px]"}]
           "目標達成"])]
       [:p {:class "text-[11px] text-slate-400 mt-1 flex items-center gap-1.5"}
        [:i {:class "fa-regular fa-calendar text-[10px]"}]
        dates-str]]

      ;; 操作ボタン群 (5大操作)
      [:div {:class "flex items-center gap-1"}
       ;; 1. 一時停止 / 再開
       [:button {:class (if (= status :paused)
                          "p-1.5 text-amber-400 hover:text-amber-300 hover:bg-slate-900 rounded-lg transition text-xs"
                          "p-1.5 text-slate-400 hover:text-amber-400 hover:bg-slate-900 rounded-lg transition text-xs")
                 :title (if (= status :paused) "巡回再開" "一時停止")
                 :hx-post (str "/api/tasks/" id-str "/toggle-status")
                 :hx-target "#dashboard-container"}
        [:i {:class (if (= status :paused) "fa-solid fa-play" "fa-solid fa-pause")}]]
       ;; 2. 即時巡回 (ヘッドレス)
       [:button {:class "p-1.5 text-slate-400 hover:text-sky-400 hover:bg-slate-900 rounded-lg transition text-xs"
                 :title "即時巡回 (ヘッドレス)"
                 :hx-post (str "/api/tasks/" id-str "/run?headless=true")
                 :hx-target "#dashboard-container"}
        [:i {:class "fa-solid fa-arrows-rotate"}]]
       ;; 3. ブラウザを開いて巡回 (手動支援)
       [:button {:class "p-1.5 text-slate-400 hover:text-sky-300 hover:bg-slate-900 rounded-lg transition text-xs"
                 :title "ブラウザを開いて巡回 (ユーザー手動支援)"
                 :hx-post (str "/api/tasks/" id-str "/run?headless=false")
                 :hx-target "#dashboard-container"}
        [:i {:class "fa-solid fa-window-restore text-sky-400"}]]
       ;; 4. 編集モーダル
       [:button {:class "p-1.5 text-slate-400 hover:text-white hover:bg-slate-900 rounded-lg transition text-xs"
                 :title "編集"
                 :hx-get (str "/api/tasks/" id-str "/modal")
                 :hx-target "#modal-container"}
        [:i {:class "fa-solid fa-pen-to-square"}]]
       ;; 5. 削除
       [:button {:class "p-1.5 text-slate-400 hover:text-rose-400 hover:bg-slate-900 rounded-lg transition text-xs"
                 :title "削除"
                 :hx-delete (str "/api/tasks/" id-str)
                 :hx-confirm (str "タスク '" title "' を削除しますか？")
                 :hx-target "#dashboard-container"}
        [:i {:class "fa-regular fa-trash-can"}]]]]

     ;; ルート表示
     [:div {:class "my-2.5 flex items-center justify-between bg-slate-900/80 p-3 rounded-lg border border-slate-800"}
      [:div {:class "text-center"}
       [:span {:class "text-[10px] font-semibold text-slate-500 block"} "出発"]
       [:p {:class "text-base font-black text-white font-mono tracking-wider"} origin-str]]
      [:div {:class "flex-1 flex flex-col items-center px-3"}
       [:span {:class "text-[10px] text-slate-400 font-medium"} stops-label]
       [:div {:class "w-full h-0.5 bg-slate-700 relative my-1"}
        [:i {:class "fa-solid fa-plane text-slate-400 absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 text-[10px] bg-slate-900 px-1"}]]]
      [:div {:class "text-center"}
       [:span {:class "text-[10px] font-semibold text-slate-500 block"} "到着"]
       [:p {:class "text-base font-black text-white font-mono tracking-wider"} dest-str]]]

     ;; 価格情報
     [:div {:class "my-2"}
      [:div {:class "flex items-baseline justify-between"}
       [:span {:class "text-xs text-slate-400 font-medium"} "現在最安値"]
       [:div {:class "flex items-center gap-2"}
        (cond
          (= provider :google-flights)
          [:span {:class "badge-google text-[10px] font-bold px-1.5 py-0.5 rounded"} "Google"]
          (= provider :skyscanner)
          [:span {:class "badge-skyscanner text-[10px] font-bold px-1.5 py-0.5 rounded"} "Skyscanner"])]]
      [:div {:class "flex items-baseline gap-2 mt-0.5"}
       (if lowest-price
         [:p {:class (if is-price-met "text-2xl font-black text-emerald-400" "text-2xl font-black text-white")}
          (str "¥" (.ToString ^long lowest-price "N0"))]
         [:p {:class "text-xl font-bold text-slate-500"} "未取得"])
       (when target-price
         [:span {:class "text-xs text-slate-400"}
          (str "/ 目標: ¥" (.ToString ^long target-price "N0"))])]]

     ;; 航空会社・メモ
     [:div {:class "mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400"}
      [:div {:class "flex items-center gap-1.5 truncate max-w-[170px]"}
       [:i {:class "fa-regular fa-note-sticky text-slate-500"}]
       [:span {:class "truncate cursor-pointer hover:text-sky-400 transition"
               :title (if (str/blank? notes) "メモなし" notes)
               :hx-get (str "/api/tasks/" id-str "/notes-modal")
               :hx-target "#modal-container"}
        (if (str/blank? notes) "メモを追加..." notes)]]
      [:div {:class "text-right text-[11px] text-slate-500"}
       (str date-part " " time-part)]]

     ;; 詳細モーダルボタン
     [:button {:class "w-full mt-3 py-1.5 bg-slate-900 hover:bg-slate-850 hover:border-slate-600 text-slate-300 hover:text-white text-xs font-semibold rounded-lg border border-slate-700 transition flex items-center justify-center gap-1.5 shadow-sm"
               :hx-get (str "/api/tasks/" id-str "/detail-modal")
               :hx-target "#modal-container"}
      [:i {:class "fa-solid fa-chart-line text-sky-400"}]
      "価格推移・旅程詳細"]]))

(defn render-task-table [tasks]
  [:div {:class "bg-slate-950 rounded-xl border border-slate-800 overflow-hidden shadow-sm"}
   [:div {:class "overflow-x-auto"}
    [:table {:class "w-full text-left border-collapse text-xs text-slate-200"}
     [:thead {:class "bg-slate-900 border-b border-slate-800 text-slate-400 font-semibold uppercase tracking-wider text-[11px]"}
      [:tr
       [:th {:class "px-4 py-3"} "タスク名 / 状態"]
       [:th {:class "px-3 py-3"} "区間 / 種別"]
       [:th {:class "px-3 py-3"} "日程"]
       [:th {:class "px-3 py-3"} "乗継 / 所要時間"]
       [:th {:class "px-3 py-3"} "現在最安値"]
       [:th {:class "px-3 py-3"} "目標価格"]
       [:th {:class "px-3 py-3"} "最安航空会社"]
       [:th {:class "px-3 py-3"} "メモ"]
       [:th {:class "px-3 py-3"} "取得日時 (JST)"]
       [:th {:class "px-4 py-3 text-right"} "操作"]]]
     [:tbody {:class "divide-y divide-slate-800/80"}
      (for [t tasks]
        (let [id-str (str (:id t))
              title (or (:title t) "名称未設定")
              status (:status t)
              trip (:trip-type t)
              origin-str (domain/iata-code-value (:origin t))
              dest-str (domain/iata-code-value (:destination t))
              [date-part time-part] (format-jst (:last-checked-at t))
              lowest-price (:last-lowest-price-jpy t)
              target-price (:target-price-jpy t)
              provider (:last-lowest-provider t)
              notes (or (:user-notes t) "")
              airlines (or (:last-lowest-airlines t) "-")]
          [:tr {:class "hover:bg-slate-900/60 transition"}
           ;; 1. タイトル & 状態
           [:td {:class "px-4 py-2.5 font-medium text-white"}
            [:div {:class "flex items-center gap-1.5"}
             [:span {:class (case status
                              :active "w-2 h-2 rounded-full bg-emerald-500 shrink-0"
                              :paused "w-2 h-2 rounded-full bg-amber-400 shrink-0"
                              :completed "w-2 h-2 rounded-full bg-slate-500 shrink-0"
                              "w-2 h-2 rounded-full bg-rose-500 shrink-0")}]
             [:span {:class "font-bold"} title]]]
           ;; 2. 区間 & 種別
           [:td {:class "px-3 py-2.5"}
            [:div {:class "font-bold font-mono text-sky-400"} (str origin-str " ➔ " dest-str)]
            [:div {:class "text-[10px] text-slate-400"} (if (= (:kind trip) :round-trip) "往復" "片道")]]
           ;; 3. 日程
           [:td {:class "px-3 py-2.5 text-slate-300 whitespace-nowrap"}
            (if (= (:kind trip) :round-trip)
              [:div
               [:div (when-let [d (:outbound-date trip)] (.ToString ^DateOnly d "MM/dd"))]
               [:div {:class "text-[10px] text-slate-400"} (str "~ " (when-let [d (:inbound-date trip)] (.ToString ^DateOnly d "MM/dd")))]]
              [:div (when-let [d (:outbound-date trip)] (.ToString ^DateOnly d "yyyy/MM/dd"))])]
           ;; 4. 乗継 & 所要時間
           [:td {:class "px-3 py-2.5 text-slate-300"}
            [:div {:class "font-medium"} (case (:max-stops t)
                                           :direct-only "直行便のみ"
                                           :one-stop "経由1回"
                                           "制限なし")]
            [:div {:class "text-[10px] text-slate-500"} (str "間隔: " (or (:check-interval-hours t) 12) "h")]]
           ;; 5. 現在最安値
           [:td {:class "px-3 py-2.5 whitespace-nowrap"}
            (if lowest-price
              [:div
               [:div {:class "font-black text-white text-sm"} (str "¥" (.ToString ^long lowest-price "N0"))]
               (cond
                 (= provider :google-flights) [:span {:class "badge-google text-[9px] font-bold px-1 rounded"} "Google"]
                 (= provider :skyscanner) [:span {:class "badge-skyscanner text-[9px] font-bold px-1 rounded"} "Skyscanner"])]
              [:span {:class "text-slate-500"} "-"])]
           ;; 6. 目標価格
           [:td {:class "px-3 py-2.5 text-slate-300 whitespace-nowrap"}
            (if target-price
              (str "¥" (.ToString ^long target-price "N0"))
              [:span {:class "text-slate-500"} "-"])]
           ;; 7. 最安航空会社
           [:td {:class "px-3 py-2.5 text-slate-300 max-w-[120px] truncate"} airlines]
           ;; 8. メモ
           [:td {:class "px-3 py-2.5 text-slate-400 max-w-[130px] truncate"}
            [:span {:class "cursor-pointer hover:text-sky-400 transition"
                    :title (if (str/blank? notes) "メモなし" notes)
                    :hx-get (str "/api/tasks/" id-str "/notes-modal")
                    :hx-target "#modal-container"}
             (if (str/blank? notes) "-" notes)]]
           ;; 9. 取得日時
           [:td {:class "px-3 py-2.5 text-slate-400 whitespace-nowrap"}
            [:div date-part]
            [:div {:class "text-[10px] text-slate-500"} time-part]]
           ;; 10. 操作ボタン
           [:td {:class "px-4 py-2.5 text-right whitespace-nowrap"}
            [:div {:class "flex items-center justify-end gap-1.5"}
             [:button {:class (if (= status :paused) "p-1 text-amber-400 hover:text-amber-300 rounded transition" "p-1 text-slate-400 hover:text-amber-400 rounded transition")
                       :title (if (= status :paused) "巡回再開" "一時停止")
                       :hx-post (str "/api/tasks/" id-str "/toggle-status")
                       :hx-target "#dashboard-container"}
              [:i {:class (if (= status :paused) "fa-solid fa-play" "fa-solid fa-pause")}]]
             [:button {:class "p-1 text-slate-400 hover:text-sky-400 rounded transition"
                       :title "即時巡回 (ヘッドレス)"
                       :hx-post (str "/api/tasks/" id-str "/run?headless=true")
                       :hx-target "#dashboard-container"}
              [:i {:class "fa-solid fa-arrows-rotate"}]]
             [:button {:class "p-1 text-slate-400 hover:text-sky-300 rounded transition"
                       :title "ブラウザを開いて巡回 (ユーザー手動支援)"
                       :hx-post (str "/api/tasks/" id-str "/run?headless=false")
                       :hx-target "#dashboard-container"}
              [:i {:class "fa-solid fa-window-restore text-sky-400"}]]
             [:button {:class "p-1 text-slate-400 hover:text-sky-400 rounded transition"
                       :title "グラフ・詳細"
                       :hx-get (str "/api/tasks/" id-str "/detail-modal")
                       :hx-target "#modal-container"}
              [:i {:class "fa-solid fa-chart-line"}]]
             [:button {:class "p-1 text-slate-400 hover:text-white rounded transition"
                       :title "編集"
                       :hx-get (str "/api/tasks/" id-str "/modal")
                       :hx-target "#modal-container"}
              [:i {:class "fa-solid fa-pen"}]]
             [:button {:class "p-1 text-slate-400 hover:text-rose-400 rounded transition"
                       :title "削除"
                       :hx-delete (str "/api/tasks/" id-str)
                       :hx-confirm (str "タスク '" title "' を削除しますか？")
                       :hx-target "#dashboard-container"}
              [:i {:class "fa-regular fa-trash-can"}]]]]]))]]]]
)




(defn render-dashboard
  ([tasks] (render-dashboard tasks "card" "all" ""))
  ([tasks view-mode status-filter query-str]
   (let [v-mode (or view-mode "card")
         s-filter (or status-filter "all")
         q-str (or query-str "")
         all-count (count tasks)
         active-count (count (filter #(= (:status %) :active) tasks))
         paused-count (count (filter #(= (:status %) :paused) tasks))
         other-count (count (filter #(contains? #{:completed :failed :error} (:status %)) tasks))
         filtered-by-status (case s-filter
                              "active" (filter #(= (:status %) :active) tasks)
                              "paused" (filter #(= (:status %) :paused) tasks)
                              "other" (filter #(contains? #{:completed :failed :error} (:status %)) tasks)
                              "empty" []
                              tasks)
         filtered (if (str/blank? q-str)
                    filtered-by-status
                    (let [q (.ToLowerInvariant (.Trim q-str))]
                      (filter (fn [t]
                                (or (and (:title t) (.Contains (.ToLowerInvariant (:title t)) q))
                                    (and (:origin t) (.Contains (.ToLowerInvariant (domain/iata-code-value (:origin t))) q))
                                    (and (:destination t) (.Contains (.ToLowerInvariant (domain/iata-code-value (:destination t))) q))))
                              filtered-by-status)))]
     [:div {:id "dashboard-container" :class "space-y-6"}
      ;; 1. AI 構造化文書・自然言語解析アシスタント Box
      [:div {:class "bg-slate-950 border border-slate-800 rounded-xl p-4 shadow-sm space-y-3"}
       [:div {:class "flex flex-col sm:flex-row sm:items-center justify-between gap-2"}
        [:div {:class "flex items-center space-x-2 text-xs font-semibold text-sky-400"}
         [:i {:class "fa-solid fa-wand-magic-sparkles text-sm"}]
         [:span "AI 構造化文書・自然言語解析アシスタント"]]
        [:div {:class "flex items-center space-x-2 text-[11px]"}
         [:span {:class "text-slate-500 hidden sm:inline"} "テンプレート入力:"]
         [:button {:type "button"
                   :onclick "insertTemplate('markdown')"
                   :class "px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 hover:text-white transition"}
          "箇条書き"]
         [:button {:type "button"
                   :onclick "insertTemplate('yaml')"
                   :class "px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 hover:text-white transition"}
          "YAML形式"]
         [:button {:type "button"
                   :onclick "insertTemplate('natural')"
                   :class "px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 hover:text-white transition"}
          "自然文"]]]

       [:div {:class "relative"}
        [:textarea {:id "aiInput"
                    :rows "4"
                    :placeholder "【自由な構造化テキストで条件を記述できます】\n例:\n- 出発地: 東京 (羽田 / HND)\n- 目的地: パリ (CDG)\n- 往路日: 2026/05/01, 復路日: 2026/05/08\n- 乗継: 直行便のみ\n- 目標予算: 160,000 円以下\n- 希望航空会社: ANA, エールフランス"
                    :class "w-full bg-slate-900 border border-slate-700 rounded-lg p-3 text-xs text-white placeholder-slate-500 font-mono focus:outline-none focus:ring-1 focus:ring-sky-500 leading-relaxed resize-y min-h-[96px]"}]]

       [:div {:class "flex flex-col sm:flex-row sm:items-center justify-between gap-2 pt-1 border-t border-slate-800/80"}
        [:span {:class "text-[11px] text-slate-400"}
         "※ 箇条書きやMarkdown、長文からOpenRouter AIが自動で各パラメータを抽出して登録フォームへ展開します。"]
        [:button {:type "button"
                  :onclick "parseWithAI()"
                  :class "bg-sky-600 hover:bg-sky-500 active:scale-95 transition text-white px-4 py-2 rounded-lg text-xs font-semibold flex items-center justify-center space-x-1.5 whitespace-nowrap shadow-sm"}
         [:i {:class "fa-solid fa-wand-magic text-xs"}]
         [:span "AIで解析して新規登録フォームに反映"]]]]

      ;; 2. コントロールバー: ステータスタブ & 表示切替
      [:div {:class "flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-950/80 p-3 rounded-xl border border-slate-800"}
       ;; ステータスタブ
       [:div {:class "flex flex-wrap gap-1"}
        [:button {:class (if (or (= s-filter "all") (str/blank? s-filter))
                           "px-3 py-1.5 rounded-md text-xs font-medium bg-sky-600 text-white"
                           "px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800")
                  :hx-get (str "/api/tasks/view?mode=" v-mode "&status=all&query=" q-str)
                  :hx-target "#dashboard-container"}
         (str "すべて (" all-count ")")]
        [:button {:class (if (= s-filter "active")
                           "px-3 py-1.5 rounded-md text-xs font-medium bg-sky-600 text-white"
                           "px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800")
                  :hx-get (str "/api/tasks/view?mode=" v-mode "&status=active&query=" q-str)
                  :hx-target "#dashboard-container"}
         (str "監視中 (" active-count ")")]
        [:button {:class (if (= s-filter "paused")
                           "px-3 py-1.5 rounded-md text-xs font-medium bg-sky-600 text-white"
                           "px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800")
                  :hx-get (str "/api/tasks/view?mode=" v-mode "&status=paused&query=" q-str)
                  :hx-target "#dashboard-container"}
         (str "一時停止 (" paused-count ")")]
        [:button {:class (if (= s-filter "other")
                           "px-3 py-1.5 rounded-md text-xs font-medium bg-sky-600 text-white"
                           "px-3 py-1.5 rounded-md text-xs font-medium text-slate-400 hover:text-white hover:bg-slate-800")
                  :hx-get (str "/api/tasks/view?mode=" v-mode "&status=other&query=" q-str)
                  :hx-target "#dashboard-container"}
         (str "完了/エラー (" other-count ")")]]

       ;; 表示切替 & クイック検索
       [:div {:class "flex items-center space-x-2 sm:space-x-3"}
        [:div {:class "relative flex-1 sm:flex-none"}
         [:i {:class "fa-solid fa-magnifying-glass text-xs absolute left-3 top-2.5 text-slate-400"}]
         [:input {:type "text"
                  :placeholder "都市・空港・航空会社で検索..."
                  :value q-str
                  :class "bg-slate-900 border border-slate-700 rounded-lg pl-8 pr-3 py-1.5 text-xs text-white placeholder-slate-500 w-full sm:w-56 focus:outline-none focus:ring-1 focus:ring-sky-500"
                  :hx-get (str "/api/tasks/view?mode=" v-mode "&status=" s-filter)
                  :hx-trigger "keyup changed delay:300ms"
                  :hx-target "#dashboard-container"
                  :name "query"}]]

        [:div {:class "flex bg-slate-900 border border-slate-700 rounded-lg p-0.5"}
         [:button {:class (if (= v-mode "card")
                            "px-2.5 py-1 rounded text-xs font-medium bg-slate-800 text-white flex items-center space-x-1"
                            "px-2.5 py-1 rounded text-xs font-medium text-slate-400 hover:text-white flex items-center space-x-1")
                   :hx-get (str "/api/tasks/view?mode=card&status=" s-filter "&query=" q-str)
                   :hx-target "#dashboard-container"}
          [:i {:class "fa-solid fa-table-cells-large text-xs"}]
          [:span {:class "hidden sm:inline"} "カード"]]
         [:button {:class (if (= v-mode "table")
                            "px-2.5 py-1 rounded text-xs font-medium bg-slate-800 text-white flex items-center space-x-1"
                            "px-2.5 py-1 rounded text-xs font-medium text-slate-400 hover:text-white flex items-center space-x-1")
                   :hx-get (str "/api/tasks/view?mode=table&status=" s-filter "&query=" q-str)
                   :hx-target "#dashboard-container"}
          [:i {:class "fa-solid fa-table text-xs"}]
          [:span {:class "hidden sm:inline"} "一覧リスト (Excel風)"]]]]]

      ;; 3. 一覧本体
      (cond
        (empty? filtered) (render-empty-state)
        (= v-mode "table") (render-task-table filtered)
        :else [:div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5"}
               (for [t filtered]
                 (render-task-card t))])])))

(defn render-dashboard-content [tasks]
  (render-dashboard tasks "card" "all" ""))
