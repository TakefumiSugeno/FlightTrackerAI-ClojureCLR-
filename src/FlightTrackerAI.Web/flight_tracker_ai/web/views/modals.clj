(ns flight-tracker-ai.web.views.modals
  (:require [flight-tracker-ai.web.views.html-dsl :as h]
            [flight-tracker-ai.core.domain :as domain]
            [flight-tracker-ai.core.dto :as dto]
            [clojure.string :as str])
  (:import [System DateTime DateTimeOffset TimeSpan DateOnly]))

(defn- modal-backdrop [title-str content]
  [:div {:id "active-modal"
         :class "fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm"
         :onclick "if (event.target === this) closeCurrentModal();"}
   [:div {:class "bg-slate-950 rounded-2xl shadow-2xl max-w-lg w-full max-h-[90vh] flex flex-col overflow-hidden border border-slate-800 text-slate-100"}
    ;; ヘッダー
    [:div {:class "px-6 py-4 border-b border-slate-800 bg-slate-900 flex items-center justify-between"}
     [:h3 {:class "text-sm font-bold text-white flex items-center gap-2"}
      [:i {:class "fa-solid fa-circle-plus text-sky-400"}]
      [:span title-str]]
     [:button {:type "button"
               :class "text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
               :onclick "closeCurrentModal()"}
      [:i {:class "fa-solid fa-xmark text-base"}]]]
    ;; コンテンツ本体 (スクロール可能)
    [:div {:class "p-6 overflow-y-auto space-y-4 flex-1"}
     content]]])

(def airports-datalist
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
   [:option {:value "TPE - 台北(桃園)"}]])

;; 1. タスク登録・編集モーダル (Modals.fs 完全準拠)
(defn render-task-modal
  ([task-opt] (render-task-modal task-opt nil nil))
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
         trip-type-val (cond
                         task-opt (if (= (:kind (:trip-type task-opt)) :one-way) "OneWay" "RoundTrip")
                         (:tripType initial-params) (:tripType initial-params)
                         :else "RoundTrip")
         outbound-val (or (when task-opt
                            (when-let [ob (:outbound-date (:trip-type task-opt))]
                              (.ToString ^DateOnly ob "yyyy-MM-dd")))
                          (:outboundDate initial-params)
                          (.ToString (.AddMonths DateTime/UtcNow 1) "yyyy-MM-dd"))
         inbound-val (or (when task-opt
                           (when-let [ib (:inbound-date (:trip-type task-opt))]
                             (.ToString ^DateOnly ib "yyyy-MM-dd")))
                         (:inboundDate initial-params)
                         (.ToString (.AddDays (.AddMonths DateTime/UtcNow 1) 7.0) "yyyy-MM-dd"))
         max-stops-val (cond
                         task-opt (case (:max-stops task-opt)
                                    :direct-only "DirectOnly"
                                    :one-stop "OneStop"
                                    "Any")
                         (:maxStops initial-params) (:maxStops initial-params)
                         :else "Any")
         target-price-val (or (when task-opt
                                (when-let [tp (:target-price-jpy task-opt)] (str tp)))
                              (:targetPriceJpy initial-params)
                              (:maxPriceJpy initial-params)
                              "")
         interval-val (or (when task-opt (str (or (:check-interval-hours task-opt) 12)))
                          (:checkIntervalHours initial-params)
                          "12")
         webhook-val (or (when task-opt (:notification-webhook-url task-opt))
                         (:webhookUrl initial-params)
                         "")
         notes-val (or (when task-opt (:user-notes task-opt))
                       (:notes initial-params)
                       "")
         post-url (if task-opt
                    (str "/api/tasks/" (:id task-opt))
                    "/api/tasks")]
     (modal-backdrop title
       [:form {:hx-post post-url
               :hx-target "#dashboard-container"
               :hx-swap "outerHTML"
               :class "space-y-4 text-xs"}
        (when error-msg
          [:div {:class "p-3 bg-rose-950/80 border border-rose-800 rounded-lg text-rose-300 text-xs"}
           error-msg])

        ;; 旅行タイプ切り替えボタン
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "旅行タイプ"]
         [:div {:class "grid grid-cols-2 gap-2 bg-slate-900 p-1 rounded-lg border border-slate-800"}
          [:button {:type "button"
                    :id "btnRoundTrip"
                    :class (if (= trip-type-val "RoundTrip")
                             "py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition"
                             "py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                    :onclick "document.getElementById('formTripType').value='RoundTrip'; document.getElementById('btnRoundTrip').className='py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnOneWay').className='py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='block';"}
           "往復"]
          [:button {:type "button"
                    :id "btnOneWay"
                    :class (if (= trip-type-val "OneWay")
                             "py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition"
                             "py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                    :onclick "document.getElementById('formTripType').value='OneWay'; document.getElementById('btnOneWay').className='py-1.5 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnRoundTrip').className='py-1.5 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='none';"}
           "片道"]]
         [:input {:type "hidden" :id "formTripType" :name "tripType" :value trip-type-val}]]

        ;; 空港選択 (Datalist サポート)
        [:div {:class "grid grid-cols-2 gap-3"}
         [:div
          [:label {:class "block text-slate-400 font-medium mb-1"} "出発地 (都市名またはIATA) *"]
          [:input {:list "airportsList"
                   :type "text"
                   :id "form-origin"
                   :name "origin"
                   :required true
                   :value origin-val
                   :placeholder "HND - 東京(羽田)"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]
         [:div
          [:label {:class "block text-slate-400 font-medium mb-1"} "目的地 (都市名またはIATA) *"]
          [:input {:list "airportsList"
                   :type "text"
                   :id "form-destination"
                   :name "destination"
                   :required true
                   :value dest-val
                   :placeholder "CDG - パリ(シャルル・ド・ゴール)"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]

        airports-datalist

        ;; 日程入力
        [:div {:class "grid grid-cols-2 gap-3"}
         [:div
          [:label {:class "block text-slate-400 font-medium mb-1 flex items-center gap-1.5"}
           [:i {:class "fa-regular fa-calendar text-sky-400 text-xs"}]
           "往路出発日 *"]
          [:div {:class "relative"}
           [:input {:id "form-outbound"
                    :type "date"
                    :name "outboundDate"
                    :required true
                    :value outbound-val
                    :class "w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3 py-2 text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]
         [:div {:id "inboundDateContainer"
                :style (if (= trip-type-val "OneWay") "display: none;" "")}
          [:label {:class "block text-slate-400 font-medium mb-1 flex items-center gap-1.5"}
           [:i {:class "fa-regular fa-calendar text-indigo-400 text-xs"}]
           "復路出発日"]
          [:div {:class "relative"}
           [:input {:id "form-inbound"
                    :type "date"
                    :name "inboundDate"
                    :value inbound-val
                    :class "w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3 py-2 text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]]

        ;; 乗継・巡回間隔
        [:div {:class "grid grid-cols-2 gap-3"}
         [:div
          [:label {:class "block text-slate-400 font-medium mb-1"} "許容乗継回数 (Max Stops)"]
          [:select {:id "form-max-stops"
                    :name "maxStops"
                    :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
           [:option (merge {:value "Any"} (when (= max-stops-val "Any") {:selected true})) "乗継制限なし (最安重視・推奨)"]
           [:option (merge {:value "OneStop"} (when (= max-stops-val "OneStop") {:selected true})) "1回乗継まで"]
           [:option (merge {:value "DirectOnly"} (when (= max-stops-val "DirectOnly") {:selected true})) "直行便のみ (0回乗継)"]]]
         [:div
          [:label {:class "block text-slate-400 font-medium mb-1"} "巡回間隔"]
          [:select {:name "checkIntervalHours"
                    :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
           [:option (merge {:value "12"} (when (= interval-val "12") {:selected true})) "全体設定に従う (現在 12h)"]
           [:option (merge {:value "3"} (when (= interval-val "3") {:selected true})) "3時間ごと"]
           [:option (merge {:value "6"} (when (= interval-val "6") {:selected true})) "6時間ごと"]
           [:option (merge {:value "12"} (when (= interval-val "12") {:selected true})) "12時間ごと"]
           [:option (merge {:value "24"} (when (= interval-val "24") {:selected true})) "24時間ごと"]]]]

        ;; 目標価格 & タスク名
        [:div {:class "grid grid-cols-2 gap-3"}
         [:div
          [:label {:class "block text-slate-400 font-medium mb-1"} "目標アラート価格 (JPY)"]
          [:input {:id "form-target-price"
                   :type "number"
                   :name "targetPriceJpy"
                   :value target-price-val
                   :placeholder "例: 160000"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]
         [:div
          [:label {:class "block text-slate-400 font-medium mb-1"} "タスク名 (任意)"]
          [:input {:id "form-title"
                   :type "text"
                   :name "title"
                   :value task-title
                   :placeholder "例: ゴールデンウィーク パリ旅行"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]

        ;; メモ
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "構造化メモ / 要望・制約（任意）"]
         [:textarea {:id "form-notes"
                     :name "userNotes"
                     :rows "2"
                     :placeholder "例: - 荷物制限なし希望\n- ホテル最寄り空港優先"
                     :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white font-mono text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none resize-y"}
          notes-val]]

        ;; Webhook 設定 & ブラウザ表示
        [:div {:class "p-3 bg-slate-900 rounded-lg border border-slate-800 space-y-2"}
         [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
          [:input {:type "checkbox"
                   :name "useDefaultWebhook"
                   :value "true"
                   :checked true
                   :class "rounded border-slate-700 text-sky-600 focus:ring-sky-500 bg-slate-950"}]
          [:span "デフォルトの Discord / Slack Webhook に通知する"]]
         [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
          [:input (merge {:type "checkbox"
                          :name "showBrowser"
                          :value "true"
                          :class "rounded border-slate-700 text-sky-600 focus:ring-sky-500 bg-slate-950"}
                         (when (and task-opt (not (:is-headless task-opt))) {:checked true}))]
          [:span {:class "text-sky-300 font-medium"} "定期巡回時もブラウザを表示する (手動支援モード)"]]]

        ;; アクションボタン
        [:div {:class "pt-3 border-t border-slate-800 flex justify-end space-x-2"}
         [:button {:type "button"
                   :class "px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"
                   :onclick "closeCurrentModal()"}
          "キャンセル"]
         [:button {:type "submit"
                   :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold shadow-sm transition"}
          (if is-edit "変更を保存" "登録して巡回開始")]]]))))

;; 2. クイックメモ編集モーダル
(defn render-notes-modal [task-item]
  (let [notes-val (or (:user-notes task-item) "")
        task-id (str (:id task-item))]
    (modal-backdrop (str "タスクのメモ・要望編集: " (or (:title task-item) task-id))
      [:form {:hx-post (str "/api/tasks/" task-id "/notes")
              :hx-target "#dashboard-container"
              :hx-swap "outerHTML"
              :class "space-y-4 text-xs"}
       [:div
        [:label {:class "block text-slate-300 font-semibold mb-1"}
         "ユーザーメモ / 要望・制約（Markdown・箇条書き可）"]
        [:textarea {:name "userNotes"
                    :rows "4"
                    :placeholder "例: - 家族旅行のため荷物預けあり必須\n- 現地午前着希望"
                    :class "w-full bg-slate-900 border border-slate-700 rounded-lg p-3 text-white font-mono text-xs focus:ring-1 focus:ring-sky-500 leading-relaxed focus:outline-none resize-y"}
         notes-val]
        [:p {:class "text-[11px] text-slate-400 mt-1"}
         "※ このメモはタスクカードおよび一覧リストに表示され、AI買い時分析の参照情報としても活用されます。"]]
       [:div {:class "pt-3 border-t border-slate-800 grid grid-cols-2 gap-2"}
        [:button {:type "button"
                  :class "px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-medium transition text-center"
                  :onclick "closeCurrentModal()"}
         "キャンセル"]
        [:button {:type "submit"
                  :class "px-3 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-xs font-semibold shadow transition text-center"}
         "メモを保存"]]])))

;; 3. システム全体設定モーダル
(defn render-settings-modal [settings]
  (modal-backdrop "システム全体設定 (Global Settings)"
    [:form {:hx-post "/api/settings"
            :hx-target "#dashboard-container"
            :hx-swap "outerHTML"
            :class "space-y-4 text-xs"}
     [:div
      [:label {:class "block text-slate-300 font-semibold mb-1"}
       "全体デフォルト巡回間隔 (Default Check Interval)"]
      [:select {:name "defaultCheckIntervalHours"
                :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white focus:ring-1 focus:ring-sky-500 focus:outline-none"}
       [:option (merge {:value "3"} (when (= (:default-check-interval-hours settings) 3) {:selected true})) "3時間ごと"]
       [:option (merge {:value "6"} (when (= (:default-check-interval-hours settings) 6) {:selected true})) "6時間ごと"]
       [:option (merge {:value "12"} (when (= (:default-check-interval-hours settings) 12) {:selected true})) "12時間ごと (推奨・初期値)"]
       [:option (merge {:value "24"} (when (= (:default-check-interval-hours settings) 24) {:selected true})) "24時間ごと"]]
      [:p {:class "text-[11px] text-slate-400 mt-1"}
       "※ タスク登録時に個別の巡回間隔を指定しなかった場合、この間隔で定期巡回されます。"]]

     [:div
      [:label {:class "block text-slate-300 font-semibold mb-1"}
       "デフォルト Discord / Slack Webhook URL"]
      [:input {:type "url"
               :name "defaultWebhookUrl"
               :value (or (:default-webhook-url settings) "")
               :placeholder "https://discord.com/api/webhooks/..."
               :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]

     [:div
      [:label {:class "block text-slate-300 font-semibold mb-1"}
       "OpenRouter API Key (AI支援機能用)"]
      [:input {:type "password"
               :name "openRouterApiKey"
               :value (or (:openrouter-api-key settings) "")
               :placeholder "sk-or-v1-..."
               :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white font-mono focus:ring-1 focus:ring-sky-500 focus:outline-none"}]
      [:p {:class "text-[11px] text-slate-400 mt-1"}
       "※ .env ファイルの OPENROUTER_API_KEY または本設定値が使用されます。"]]

     [:div
      [:label {:class "block text-slate-300 font-semibold mb-1"}
       "スクレイピングプロバイダー設定"]
      [:div {:class "p-3 bg-slate-900 rounded-lg border border-slate-800 space-y-2"}
       [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
        [:input (merge {:type "checkbox" :name "enableGoogleFlights" :value "true"
                        :class "rounded border-slate-700 bg-slate-950 text-sky-600"}
                       (when (:enable-google-flights settings) {:checked true}))]
        [:span "Google Flights 巡回を有効化"]]
       [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
        [:input (merge {:type "checkbox" :name "enableSkyscanner" :value "true"
                        :class "rounded border-slate-700 bg-slate-950 text-sky-600"}
                       (when (:enable-skyscanner settings) {:checked true}))]
        [:span "Skyscanner 巡回を有効化"]]]]

     [:div
      [:label {:class "block text-slate-300 font-semibold mb-1"}
       "ブラウザ実行モード (ユーザー支援・Bot認証解除)"]
      [:div {:class "p-3 bg-slate-900 rounded-lg border border-slate-800 space-y-1.5"}
       [:label {:class "flex items-center space-x-2 text-slate-300 cursor-pointer"}
        [:input (merge {:type "checkbox" :name "showBrowser" :value "true"
                        :class "rounded border-slate-700 bg-slate-950 text-sky-600"}
                       (when (not (:headless-mode settings)) {:checked true}))]
        [:span {:class "font-medium text-sky-300"} "ブラウザ画面を表示して巡回する (有頭・ユーザー支援モード)"]]
       [:p {:class "text-[11px] text-slate-400 pl-5 leading-relaxed"}
        "※ チェックを入れると巡回時に実際のブラウザ画面（有頭Chromium）がポップアップします。Skyscannerの「PRESS & HOLD」などのBot判定を手動で解除して巡回を支援できます。"]]]

     [:div {:class "pt-3 border-t border-slate-800 flex justify-end space-x-2"}
      [:button {:type "button"
                :class "px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg font-medium transition"
                :onclick "closeCurrentModal()"}
       "閉じる"]
      [:button {:type "submit"
                :class "px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold shadow transition"}
       "設定を保存"]]]))

;; 4. 旅程詳細・価格推移モーダル (Modals.fs 完全準拠)
(defn render-detail-modal [task-item history-snapshots]
  (let [id-str (str (:id task-item))
        last-checked (:last-checked-at task-item)
        last-checked-str (if last-checked
                           (.ToString (.ToOffset ^DateTimeOffset last-checked (TimeSpan/FromHours 9.0)) "yyyy/MM/dd HH:mm:ss JST")
                           "-")]
    (modal-backdrop (str "価格推移 & 旅程詳細: " (:title task-item))
      [:div {:class "space-y-5 text-xs"}
       ;; チャートエリア
       [:div {:class "bg-slate-900 p-4 rounded-xl border border-slate-800"}
        [:div {:class "flex items-center justify-between mb-2"}
         [:span {:class "font-bold text-slate-300 flex items-center gap-1.5"}
          [:i {:class "fa-solid fa-chart-line text-sky-400"}]
          "最安値 推移チャート (JPY)"]
         [:div {:class "flex items-center space-x-3 text-[11px] font-medium"}
          [:span {:class "text-emerald-400 flex items-center gap-1"}
           [:span {:class "w-2 h-2 rounded-full bg-emerald-400"}]
           "Google Flights"]
          [:span {:class "text-sky-400 flex items-center gap-1"}
           [:span {:class "w-2 h-2 rounded-full bg-sky-400"}]
           "Skyscanner"]]]
        [:div {:class "h-52 relative w-full"}
         [:canvas {:id "priceHistoryChart"}]]]

       ;; 同日・同区間の候補便一覧
       [:div {:class "space-y-2"}
        [:div {:class "flex items-center justify-between"}
         [:h4 {:class "font-bold text-white flex items-center gap-1.5"}
          [:i {:class "fa-solid fa-list text-sky-400"}]
          "同日・同区間の候補便一覧"]
         [:span {:class "text-[11px] text-slate-400"}
          (str "最新データ: " last-checked-str)]]

        (if (empty? history-snapshots)
          [:div {:class "bg-slate-900/60 p-4 rounded-lg border border-slate-800 text-center text-slate-500 italic"}
           "フライトスナップショットはまだ記録されていません。「即時巡回」を実行するとフライト候補が保存されます。"]
          [:div {:class "overflow-x-auto border border-slate-800 rounded-lg"}
           [:table {:class "w-full text-left border-collapse min-w-max text-xs"}
            [:thead {:class "bg-slate-900 text-slate-400 font-semibold border-b border-slate-800 whitespace-nowrap"}
             [:tr
              [:th {:class "px-3 py-2"} "ソース"]
              [:th {:class "px-3 py-2"} "航空会社"]
              [:th {:class "px-3 py-2"} "発着時刻 (現地時間)"]
              [:th {:class "px-3 py-2"} "乗継"]
              [:th {:class "px-3 py-2"} "所要時間"]
              [:th {:class "px-3 py-2 text-right"} "価格 (総額)"]
              [:th {:class "px-3 py-2 text-center"} "予約"]]]
            [:tbody {:class "divide-y divide-slate-800"}
             (for [s history-snapshots]
               (let [p (:provider s)
                     dep (:departure-time s)
                     arr (:arrival-time s)
                     dep-str (if (instance? DateTimeOffset dep) (.ToString ^DateTimeOffset dep "HH:mm") (str dep))
                     arr-str (if (instance? DateTimeOffset arr) (.ToString ^DateTimeOffset arr "HH:mm") (str arr))
                     dur (or (:total-duration-minutes s) 0)
                     price (or (:price-jpy s) 0)
                     book-url (:booking-url s)
                     stops (or (:stops-count s) 0)]
                 [:tr {:class "hover:bg-slate-900/50 transition"}
                  [:td {:class "px-3 py-2"}
                   [:span {:class (if (= p :google-flights)
                                    "px-2 py-0.5 rounded bg-emerald-950 border border-emerald-800 text-emerald-300 font-semibold"
                                    "px-2 py-0.5 rounded bg-sky-950 border border-sky-800 text-sky-300 font-semibold")}
                    (if (= p :google-flights) "GoogleFlights" "Skyscanner")]]
                  [:td {:class "px-3 py-2 font-semibold text-white"}
                   (or (:airlines-summary s) "-")]
                  [:td {:class "px-3 py-2"}
                   (str dep-str " ➔ " arr-str)]
                  [:td {:class "px-3 py-2"}
                   (if (zero? stops) "直行便" (str "経由" stops "回"))]
                  [:td {:class "px-3 py-2"}
                   (str (quot dur 60) "h " (format "%02d" (rem dur 60)) "m")]
                  [:td {:class "px-3 py-2 text-right font-bold text-emerald-400"}
                   (str "¥" (.ToString ^long price "N0"))]
                  [:td {:class "px-3 py-2 text-center"}
                   (if (not (str/blank? book-url))
                     [:a {:href book-url :target "_blank" :class "text-sky-400 hover:text-sky-300 underline font-medium"}
                      "予約"]
                      [:span {:class "text-slate-600"} "-"])]]))]]])]



       ;; フッター閉じるボタン
       [:div {:class "pt-3 border-t border-slate-800 flex justify-end"}
        [:button {:type "button"
                  :class "px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-semibold transition"
                  :onclick "closeCurrentModal()"}
         "閉じる"]]

       ;; Chart.js スクリプト
       [:script {} (h/raw (str "
         (function() {
           fetch('/api/tasks/" id-str "/history')
           .then(r => r.json())
           .then(data => {
             const ctx = document.getElementById('priceHistoryChart');
             if (!ctx) return;
             new Chart(ctx, {
               type: 'line',
               data: {
                 labels: data.labels || [],
                 datasets: [{
                   label: '最安価格 (¥)',
                   data: data.prices || [],
                   borderColor: '#38bdf8',
                   backgroundColor: 'rgba(56, 189, 248, 0.15)',
                   tension: 0.25,
                   fill: true,
                   pointRadius: 4,
                   pointBackgroundColor: '#0284c7'
                 }]
               },
               options: {
                 responsive: true,
                 maintainAspectRatio: false,
                 plugins: {
                   legend: { labels: { color: '#94a3b8', font: { size: 11 } } }
                 },
                 scales: {
                   x: { grid: { color: '#1e293b' }, ticks: { color: '#94a3b8', font: { size: 10 } } },
                   y: { grid: { color: '#1e293b' }, ticks: { color: '#94a3b8', font: { size: 10 } } }
                 }
               }
             });
           });
         })();
       "))]])))

;; 5. システム実行ログモーダル
(defn render-logs-modal [logs log-file-path]
  (let [log-content (str/join "\n" logs)
        log-count (count logs)]
    (modal-backdrop "システム実行ログ (Debug & Activity Logs)"
      [:div {:class "space-y-4 text-xs"}
       [:div {:class "flex items-center justify-between text-slate-400"}
        [:span {:class "font-mono text-[11px] truncate max-w-xs"}
         (str "ログ: " log-file-path)]
        [:span {:class "text-[11px] font-medium text-sky-400"}
         (str "最新 " log-count " 行を表示中")]]

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
         "閉じる"]]])))

;; 6. スタンドアロン新規タスク登録画面 (/tasks/new)
(defn render-standalone-new-task-page [query-params]
  (let [origin (or (:origin query-params) "")
        destination (or (:destination query-params) "")
        outbound-date (or (:outboundDate query-params) "")
        inbound-date (or (:inboundDate query-params) "")
        trip-type (or (:tripType query-params) "RoundTrip")
        max-stops (or (:maxStops query-params) "Any")
        max-price (or (:maxPriceJpy query-params) "")
        notes (or (:notes query-params) "")]
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
       ;; 旅行タイプ切り替え
       [:div
        [:label {:class "block text-slate-400 font-medium mb-1"} "旅行タイプ"]
        [:div {:class "grid grid-cols-2 gap-2 bg-slate-900 p-1 rounded-lg border border-slate-800"}
         [:button {:type "button"
                   :id "btnRoundTrip"
                   :class (if (not= trip-type "OneWay") "py-2 text-center rounded-md font-medium bg-sky-600 text-white transition" "py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                   :onclick "document.getElementById('formTripType').value='RoundTrip'; document.getElementById('btnRoundTrip').className='py-2 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnOneWay').className='py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='block';"}
          "往復"]
         [:button {:type "button"
                   :id "btnOneWay"
                   :class (if (= trip-type "OneWay") "py-2 text-center rounded-md font-medium bg-sky-600 text-white transition" "py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition")
                   :onclick "document.getElementById('formTripType').value='OneWay'; document.getElementById('btnOneWay').className='py-2 text-center rounded-md font-medium bg-sky-600 text-white transition'; document.getElementById('btnRoundTrip').className='py-2 text-center rounded-md font-medium text-slate-400 hover:text-white transition'; document.getElementById('inboundDateContainer').style.display='none';"}
          "片道"]]
        [:input {:type "hidden" :id "formTripType" :name "tripType" :value (if (= trip-type "OneWay") "OneWay" "RoundTrip")}]]

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
                  :placeholder "MNL - マニラ"
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]

       airports-datalist

       ;; 日程入力
       [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "往路出発日 *"]
         [:input {:type "date"
                  :name "outboundDate"
                  :required true
                  :value outbound-date
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]
        [:div {:id "inboundDateContainer"
               :style (if (= trip-type "OneWay") "display: none;" "")}
         [:label {:class "block text-slate-400 font-medium mb-1"} "復路出発日"]
         [:input {:type "date"
                  :name "inboundDate"
                  :value inbound-date
                  :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]]

       ;; 乗継・巡回間隔
       [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "許容乗継回数"]
         [:select {:name "maxStops"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
          [:option (merge {:value "Any"} (when (= max-stops "Any") {:selected true})) "乗継制限なし (最安重視)"]
          [:option (merge {:value "OneStop"} (when (= max-stops "OneStop") {:selected true})) "1回乗継まで"]
          [:option (merge {:value "DirectOnly"} (when (= max-stops "DirectOnly") {:selected true})) "直行便のみ"]]]
        [:div
         [:label {:class "block text-slate-400 font-medium mb-1"} "巡回間隔"]
         [:select {:name "checkIntervalHours"
                   :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
          [:option {:value "12" :selected true} "全体設定に従う (現在 12h)"]
          [:option {:value "3"} "3時間ごと"]
          [:option {:value "6"} "6時間ごと"]
          [:option {:value "12"} "12時間ごと"]
          [:option {:value "24"} "24時間ごと"]]]]

       ;; 目標価格
       [:div
        [:label {:class "block text-slate-400 font-medium mb-1"} "目標アラート価格 (JPY)"]
        [:input {:type "number"
                 :name "targetPriceJpy"
                 :value max-price
                 :placeholder "例: 90000"
                 :class "w-full bg-slate-900 border border-slate-700 rounded-lg px-3.5 py-2.5 text-white text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}]]

       ;; メモ
       [:div
        [:label {:class "block text-slate-400 font-medium mb-1"} "メモ・要望"]
        [:textarea {:name "userNotes"
                    :rows "3"
                    :placeholder "メモや要望を自由に入力"
                    :class "w-full bg-slate-900 border border-slate-700 rounded-lg p-3 text-white font-mono text-xs focus:ring-1 focus:ring-sky-500 focus:outline-none"}
         notes]]

       [:div {:class "pt-4 border-t border-slate-800 flex justify-end space-x-3"}
        [:a {:href "/"
             :class "px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-medium transition"}
         "キャンセル"]
        [:button {:type "submit"
                  :class "px-5 py-2.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-xs font-semibold shadow transition"}
         "タスクを登録して監視開始"]]]]]))

;; 互換性エイリアス
(def render-timeline-modal render-detail-modal)
(def render-quick-note-modal render-notes-modal)
(defn render-delete-modal [task-id route-str]
  [:div {:id "deleteModal"
         :class "fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm"
         :onclick "if (event.target === this) closeCurrentModal();"}
   [:div {:class "bg-slate-950 border border-slate-800 w-full max-w-sm rounded-2xl shadow-2xl p-6 space-y-4 text-slate-100"}
    [:div {:class "w-12 h-12 rounded-full bg-rose-950 text-rose-400 flex items-center justify-center mx-auto border border-rose-800"}
     [:i {:class "fa-solid fa-triangle-exclamation text-xl"}]]
    [:div {:class "text-center"}
     [:h3 {:class "text-sm font-bold text-white"} "監視タスクを削除しますか？"]
     [:p {:class "text-xs text-rose-300 font-semibold mt-1"} route-str]
     [:p {:class "text-xs text-slate-400 mt-2"} "このタスクおよび蓄積された価格履歴データがすべて完全に削除されます。"]]
    [:div {:class "grid grid-cols-2 gap-2 pt-2 border-t border-slate-800"}
     [:button {:type "button"
               :onclick "closeCurrentModal()"
               :class "px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-medium transition"}
      "キャンセル"]
     [:button {:type "button"
               :hx-delete (str "/api/tasks/" task-id)
               :hx-target "#dashboard-container"
               :class "px-3 py-2 bg-rose-600 hover:bg-rose-500 text-white rounded-lg text-xs font-semibold transition"}
      "削除する"]]]])
