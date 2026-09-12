(ns flight-tracker-ai.web.views.layout
  (:require [flight-tracker-ai.web.views.html-dsl :as h]
            [clojure.string :as str]))

(defn base-layout [title-str body-content]
  (h/render-html
    [:html {:lang "ja"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:title (str title-str " - FlightTrackerAI")]
      [:link {:rel "icon" :type "image/svg+xml" :href "/favicon.svg"}]
      [:script {:src "https://cdn.tailwindcss.com"}]
      [:script {:src "https://unpkg.com/htmx.org@1.9.12"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/chart.js"}]
      [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css"}]
      [:style (h/raw "
        @keyframes pulse-subtle { 0%, 100% { opacity: 1; } 50% { opacity: 0.85; } }
        .animate-pulse-subtle { animation: pulse-subtle 3s infinite ease-in-out; }
        .badge-google { background-color: #064e3b; color: #34d399; border: 1px solid #059669; }
        .badge-skyscanner { background-color: #0c4a6e; color: #38bdf8; border: 1px solid #0284c7; }
      ")]]
     [:body {:class "bg-slate-900 text-slate-100 min-h-screen flex flex-col font-sans antialiased"}
      ;; トースト通知用コンテナ (右上固定)
      [:div {:id "toastContainer" :class "fixed top-4 right-4 z-50 flex flex-col space-y-2 pointer-events-none"}]

      ;; ナビゲーションヘッダー
      [:header {:class "border-b border-slate-800 bg-slate-950/90 backdrop-blur sticky top-0 z-40"}
       [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between"}
        ;; ロゴ
        [:div {:class "flex items-center space-x-3"}
         [:div {:class "bg-sky-600 p-2 rounded-lg text-white shadow-md flex items-center justify-center w-9 h-9"}
          [:i {:class "fa-solid fa-plane text-base"}]]
         [:div
          [:div {:class "flex items-center space-x-1"}
           [:span {:class "text-lg font-bold tracking-tight text-white"} "FlightTracker"
            [:span {:class "text-sky-400"} "AI"]]]
          [:p {:class "text-[10px] text-slate-400 hidden sm:block leading-none mt-0.5"}
           "Google Flights & Skyscanner 自動巡回・価格監視"]]]

        ;; 右側アクション
        [:div {:class "flex items-center space-x-2 sm:space-x-3"}
         [:div {:class "hidden md:flex items-center space-x-2 text-xs bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800"}
          [:span {:class "relative flex h-2 w-2"}
           [:span {:class "animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"}]
           [:span {:class "relative inline-flex rounded-full h-2 w-2 bg-emerald-500"}]]
          [:span {:class "text-slate-300"} "巡回ワーカー: "
           [:span {:class "text-emerald-400 font-medium"} "稼働中"]]]

         ;; ログ確認ボタン
         [:button {:class "p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-lg transition border border-slate-700 flex items-center space-x-1.5 text-xs font-medium"
                   :title "システム実行ログ確認 (AI共有用)"
                   :hx-get "/api/logs/modal"
                   :hx-target "#modal-container"}
          [:i {:class "fa-solid fa-terminal text-sm text-sky-400"}]
          [:span {:class "hidden sm:inline"} "ログ確認"]]

         ;; 全体設定ボタン
         [:button {:class "p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-lg transition border border-slate-700 flex items-center space-x-1.5 text-xs font-medium"
                   :title "システム全体設定"
                   :hx-get "/api/settings/modal"
                   :hx-target "#modal-container"}
          [:i {:class "fa-solid fa-sliders text-sm text-slate-400"}]
          [:span {:class "hidden sm:inline"} "全体設定"]]

         ;; 新規タスク登録ボタン
         [:button {:class "flex items-center space-x-1.5 bg-sky-600 hover:bg-sky-500 active:scale-95 transition text-white px-3.5 py-2 rounded-lg font-medium text-xs shadow-sm"
                   :hx-get "/api/tasks/new-modal"
                   :hx-target "#modal-container"}
          [:i {:class "fa-solid fa-plus text-xs"}]
          [:span {:class "hidden sm:inline"} "新規タスク登録"]
          [:span {:class "sm:hidden"} "新規登録"]]]]]

      ;; メインコンテンツ
      [:main {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 flex-1 w-full space-y-6"}
       body-content]

      ;; フッター
      [:footer {:class "border-t border-slate-800/80 bg-slate-950 py-4 text-center text-xs text-slate-500 mt-auto"}
       "© 2026 FlightTrackerAI • Specification-Driven Architecture on ClojureCLR & .NET 10"]

      ;; モーダル用コンテナ
      [:div {:id "modal-container"}]

      ;; 共通スクリプト (Layout.fs 完全準拠)
      [:script {} (h/raw "
        function closeCurrentModal() {
          const modal = document.getElementById('active-modal');
          if (modal) modal.remove();
          const container = document.getElementById('modal-container');
          if (container) container.innerHTML = '';
          document.body.classList.remove('overflow-hidden');
        }
        window.closeCurrentModal = closeCurrentModal;

        function showToast(message, isSuccess = true) {
          const container = document.getElementById('toastContainer');
          if (!container) return;
          const toast = document.createElement('div');
          toast.className = `px-4 py-3 rounded-xl shadow-2xl text-xs font-medium flex items-center gap-2 pointer-events-auto transition duration-300 border ${isSuccess ? 'bg-slate-900/95 text-emerald-300 border-emerald-500/30' : 'bg-slate-900/95 text-rose-300 border-rose-500/30'}`;
          toast.innerHTML = `<i class=\"fa-solid ${isSuccess ? 'fa-circle-check text-emerald-400' : 'fa-circle-exclamation text-rose-400'}\"></i><span>${message}</span>`;
          container.appendChild(toast);
          setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 5000);
        }
        window.showToast = showToast;

        function insertTemplate(type) {
          const textarea = document.getElementById('aiInput');
          if (!textarea) return;
          if (type === 'markdown') {
            textarea.value = \"- 出発地: 東京 (羽田 / HND)\\n- 目的地: パリ (CDG)\\n- 往路日: 2026/05/01, 復路日: 2026/05/08\\n- 乗継: 直行便のみ\\n- 目標予算: 160,000 円以下\\n- 希望航空会社: ANA, エールフランス\";
          } else if (type === 'yaml') {
            textarea.value = \"origin: HND\\ndestination: SIN\\ntrip_type: RoundTrip\\noutbound_date: 2026-08-10\\ninbound_date: 2026-08-17\\nmax_stops: 0\\ntarget_price: 90000\\nnotes: お盆休みシンガポール\";
          } else if (type === 'natural') {
            textarea.value = \"8月のお盆休みに羽田からシンガポールに往復で行きたいです。予算は9万円以内で直行便を希望します。\";
          }
          textarea.focus();
        }
        window.insertTemplate = insertTemplate;

        function parseWithAI() {
          const textarea = document.getElementById('aiInput');
          const prompt = textarea ? textarea.value.trim() : '';
          if (!prompt) {
            showToast('AI解析するテキストを入力してください。', false);
            return;
          }
          const newTab = window.open('about:blank', '_blank');
          if (newTab) {
            newTab.document.write(`
              <!DOCTYPE html>
              <html lang=\"ja\" class=\"dark\">
              <head>
                <meta charset=\"UTF-8\">
                <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
                <title>AI解析中... • FlightTrackerAI</title>
                <link rel=\"icon\" type=\"image/svg+xml\" href=\"/favicon.svg\" />
                <script src=\"https://cdn.tailwindcss.com\"><\\/script>
                <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css\">
              </head>
              <body class=\"bg-slate-950 text-slate-100 flex items-center justify-center min-h-screen font-sans p-4\">
                <div class=\"text-center space-y-5 max-w-md p-8 bg-slate-900/90 border border-slate-800 rounded-2xl shadow-2xl backdrop-blur-sm\">
                  <div class=\"relative w-20 h-20 mx-auto flex items-center justify-center\">
                    <div class=\"absolute inset-0 rounded-full border-4 border-sky-500/20 border-t-sky-400 animate-spin\"></div>
                    <i class=\"fa-solid fa-plane text-xl text-sky-400 animate-pulse\"></i>
                  </div>
                  <div class=\"space-y-2\">
                    <h3 class=\"font-bold text-white text-base\">AI 解析を実行中...</h3>
                    <p class=\"text-xs text-slate-400 leading-relaxed\">自然言語プロンプトからフライト条件（出発地・目的地・日程・予算等）を抽出しています。<br>解析完了後、自動的に登録画面へ遷移します。</p>
                  </div>
                  <div class=\"inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-800/80 border border-slate-700 text-[11px] text-slate-400 font-mono\">
                    <span class=\"w-2 h-2 rounded-full bg-sky-400 animate-ping\"></span>
                    <span>OpenRouter LLM 解析処理中</span>
                  </div>
                </div>
              </body>
              </html>
            `);
            newTab.document.close();
          }
          showToast('AI解析を実行中...（新規タブで準備中）', true);
          fetch('/api/ai/parse', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ prompt: prompt })
          })
          .then(async r => {
            if (!r.ok) {
              let errText = await r.text();
              try {
                const errObj = JSON.parse(errText);
                if (errObj && errObj.error) errText = errObj.error;
              } catch(e) {}
              throw new Error(errText || `HTTP ${r.status}`);
            }
            return r.json();
          })
          .then(data => {
            const origin = data.origin || data.Origin || '';
            const destination = data.destination || data.Destination || '';
            const outboundDate = data.outboundDate || data.OutboundDate || '';
            const inboundDate = data.inboundDate || data.InboundDate || '';
            const tripType = data.tripType || data.TripType || 'RoundTrip';
            const maxStops = data.maxStops || data.MaxStops || 'Any';
            const maxPrice = data.maxPriceJpy || data.MaxPriceJpy || '';
            const notes = data.notes || data.Notes || '';

            const params = new URLSearchParams();
            if (origin) params.append('origin', origin);
            if (destination) params.append('destination', destination);
            if (outboundDate) params.append('outboundDate', outboundDate);
            if (inboundDate) params.append('inboundDate', inboundDate);
            if (tripType) params.append('tripType', tripType);
            if (maxStops) params.append('maxStops', maxStops);
            if (maxPrice) params.append('maxPriceJpy', maxPrice);
            if (notes) params.append('notes', notes);

            const newTabUrl = '/tasks/new?' + params.toString();
            if (newTab && !newTab.closed) {
              newTab.location.href = newTabUrl;
            } else {
              window.open(newTabUrl, '_blank');
            }
            showToast('AI解析完了: 別画面（新規タブ）に登録画面を開きました！', true);
          })
          .catch(err => {
            if (newTab && !newTab.closed) {
              const sanitizedMsg = (err.message || 'エラーが発生しました').replace(/</g, '&lt;').replace(/>/g, '&gt;');
              newTab.document.body.innerHTML = `
                <div class=\"text-center space-y-4 max-w-md p-8 bg-slate-900/90 border border-rose-500/30 rounded-2xl shadow-2xl\">
                  <div class=\"w-16 h-16 mx-auto rounded-full bg-rose-500/10 border border-rose-500/30 flex items-center justify-center text-rose-400 text-2xl\">
                    <i class=\"fa-solid fa-triangle-exclamation\"></i>
                  </div>
                  <div class=\"space-y-1\">
                    <h3 class=\"font-bold text-white text-base\">AI解析に失敗しました</h3>
                    <p class=\"text-xs text-rose-300 leading-relaxed\">${sanitizedMsg}</p>
                  </div>
                  <p class=\"text-[11px] text-slate-400\">元の画面でプロンプト内容をご確認・修正の上、再試行してください。</p>
                  <button onclick=\"window.close()\" class=\"px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs rounded-lg font-medium transition\">このタブを閉じる</button>
                </div>
              `;
            }
            showToast(err.message, false);
          });
        }
        window.parseWithAI = parseWithAI;

        // モーダルイベントリスナー
        document.body.addEventListener('closeModal', function() {
          closeCurrentModal();
        });

        window.addEventListener('keydown', function(e) {
          if ((e.key === 'Escape' || e.key === 'Esc') && !e.isComposing && e.keyCode !== 229) {
            closeCurrentModal();
          }
        });
      ")]]]))
