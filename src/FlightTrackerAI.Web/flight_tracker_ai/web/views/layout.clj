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
      [:script (h/raw "
        tailwind.config = {
          darkMode: 'class',
          theme: {
            extend: {
              colors: {
                skyline: {
                  50: '#f0f7ff',
                  100: '#e0effe',
                  500: '#0284c7',
                  600: '#0369a1',
                  700: '#075985',
                  800: '#0c4a6e',
                  900: '#082f49',
                  950: '#041d30',
                },
              },
            },
          },
        };
      ")]
      [:script {:src "https://unpkg.com/htmx.org@1.9.12"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/chart.js"}]
      [:script {:src "https://unpkg.com/lucide@latest"}]
      [:style (h/raw "
        @keyframes pulse-subtle { 0%, 100% { opacity: 1; } 50% { opacity: 0.85; } }
        .animate-pulse-subtle { animation: pulse-subtle 3s infinite ease-in-out; }
        .badge-google { background-color: #064e3b; color: #34d399; border: 1px solid #059669; }
        .badge-skyscanner { background-color: #0c4a6e; color: #38bdf8; border: 1px solid #0284c7; }
      ")]]
     [:body {:class "bg-slate-900 text-slate-100 min-h-screen flex flex-col font-sans antialiased"}
      ;; Toast Container
      [:div {:id "toastContainer" :class "fixed top-4 right-4 z-50 flex flex-col space-y-2 pointer-events-none"}]

      ;; Header
      [:header {:class "border-b border-slate-800 bg-slate-950/90 backdrop-blur sticky top-0 z-40"}
       [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between"}
        ;; Left: Logo & Environment
        [:div {:class "flex items-center space-x-3"}
         [:div {:class "bg-sky-600 p-2 rounded-lg text-white shadow-md flex items-center justify-center"}
          [:i {:data-lucide "plane" :class "w-5 h-5"}]]
         [:div
          [:div {:class "flex items-center space-x-2"}
           [:span {:class "text-lg font-bold tracking-tight text-white"} "FlightTracker"
            [:span {:class "text-sky-400"} "AI"]]]
          [:p {:class "text-[10px] text-slate-400 hidden sm:block leading-none mt-0.5"}
           "Google Flights & Skyscanner 自動巡回・価格監視"]]]

        ;; Right: Worker Status & Actions
        [:div {:class "flex items-center space-x-2 sm:space-x-3"}
         [:div {:class "hidden md:flex items-center space-x-2 text-xs bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800"}
          [:span {:class "relative flex h-2 w-2"}
           [:span {:class "animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"}]
           [:span {:class "relative inline-flex rounded-full h-2 w-2 bg-emerald-500"}]]
          [:span {:class "text-slate-300"} "巡回ワーカー: "
           [:span {:class "text-emerald-400 font-medium"} "稼働中 (次巡回: 18:00)"]]]

         [:button {:class "p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-lg transition border border-slate-700 flex items-center space-x-1.5 text-xs font-medium"
                   :title "システム全体設定 (デフォルト巡回間隔 / Webhook / APIキー)"
                   :hx-get "/api/settings/modal"
                   :hx-target "#modal-container"}
          [:i {:data-lucide "sliders" :class "w-4 h-4"}]
          [:span {:class "hidden sm:inline"} "全体設定"]]

         [:button {:class "flex items-center space-x-1.5 bg-sky-600 hover:bg-sky-500 active:scale-95 transition text-white px-3.5 py-2 rounded-lg font-medium text-xs shadow-sm"
                   :hx-get "/api/tasks/new-modal"
                   :hx-target "#modal-container"}
          [:i {:data-lucide "plus" :class "w-4 h-4"}]
          [:span {:class "hidden sm:inline"} "新規タスク登録"]
          [:span {:class "sm:hidden"} "新規登録"]]]]]

      ;; Main Container
      [:main {:class "flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full space-y-6"}
       body-content]

      ;; Footer
      [:footer {:class "border-t border-slate-800 bg-slate-950 py-4 text-center text-xs text-slate-500 mt-auto flex items-center justify-between max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 w-full"}
       [:p "FlightTrackerAI • Specification-Driven Architecture on ClojureCLR (.NET 10)"]
       [:button {:class "text-slate-400 hover:text-slate-200 text-xs flex items-center gap-1 transition"
                 :title "システム実行ログ確認 (AI共有用)"
                 :hx-get "/api/logs/modal"
                 :hx-target "#modal-container"}
        [:i {:data-lucide "terminal" :class "w-3.5 h-3.5 text-sky-400"}]
        [:span "ログ確認"]]]

      ;; Modal Container
      [:div {:id "modal-container"}]

      ;; Client-Side Scripts
      [:script {} (h/raw "
        // --- Modal & Navigation State Machine ---
        window.__modalState = {
          isOpen: false,
          isNavigatingBack: false
        };

        // Initialize / Sanitize zombie modal hash on load
        if (window.location.hash === '#modal') {
          history.replaceState(null, '', window.location.pathname + window.location.search);
        }

        function showToast(message, isSuccess = true) {
          const container = document.getElementById('toastContainer');
          if (!container) return;
          const toast = document.createElement('div');
          toast.className = `px-4 py-3 rounded-xl shadow-2xl text-xs font-medium flex items-center gap-2 pointer-events-auto transition duration-300 border ${isSuccess ? 'bg-slate-900/95 text-emerald-300 border-emerald-500/30' : 'bg-slate-900/95 text-rose-300 border-rose-500/30'}`;
          toast.innerHTML = `<i data-lucide=\"${isSuccess ? 'check-circle' : 'alert-circle'}\" class=\"w-4 h-4 ${isSuccess ? 'text-emerald-400' : 'text-rose-400'}\"></i><span>${message}</span>`;
          container.appendChild(toast);
          if (window.lucide) lucide.createIcons();
          setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 5000);
        }
        window.showToast = showToast;

        function isFormDirty(form) {
          if (!form) return false;
          for (const el of form.elements) {
            if (el.type === 'submit' || el.type === 'button' || el.type === 'hidden') continue;
            if (el.type === 'checkbox' || el.type === 'radio') {
              if (el.checked !== el.defaultChecked) return true;
            } else {
              if (el.value !== el.defaultValue) return true;
            }
          }
          return false;
        }
        window.isFormDirty = isFormDirty;

        function openModalSync(pushHistory = true) {
          document.body.classList.add('overflow-hidden');
          if (!window.__modalState.isOpen) {
            window.__modalState.isOpen = true;
            if (pushHistory) {
              history.pushState({ modalOpen: true }, '', '#modal');
            }
          } else if (pushHistory) {
            history.replaceState({ modalOpen: true }, '', '#modal');
          }
          setTimeout(function() { if (window.lucide) lucide.createIcons(); }, 10);
        }
        window.openModalSync = openModalSync;

        function closeCurrentModal(syncHistory = true) {
          // Remove scroll lock
          document.body.classList.remove('overflow-hidden');

          // Clean DOM
          const container = document.getElementById('modal-container');
          if (container) container.innerHTML = '';
          document.querySelectorAll('#active-modal, #quickNoteModal, #timelineModal, #deleteModal, .modal-backdrop-clickable').forEach(function(el) {
            el.remove();
          });

          // Sync History API if opened via UI
          if (window.__modalState.isOpen) {
            window.__modalState.isOpen = false;
            if (syncHistory && !window.__modalState.isNavigatingBack) {
              if (window.location.hash === '#modal' || (history.state && history.state.modalOpen)) {
                history.back();
              }
            }
          }
        }
        window.closeCurrentModal = closeCurrentModal;

        // Popstate handler for browser back/forward buttons
        window.addEventListener('popstate', function(e) {
          if (window.__modalState.isOpen) {
            window.__modalState.isNavigatingBack = true;
            closeCurrentModal(false);
            window.__modalState.isNavigatingBack = false;
          }
        });

        // ESC key handler with IME composition guard and dirty check
        window.addEventListener('keydown', function(e) {
          if ((e.key === 'Escape' || e.key === 'Esc') && !e.isComposing && e.keyCode !== 229) {
            const activeModal = document.querySelector('#active-modal, #quickNoteModal, #timelineModal, #deleteModal, .modal-backdrop-clickable');
            if (activeModal) {
              const form = activeModal.querySelector('form');
              if (window.isFormDirty && form && window.isFormDirty(form)) {
                if (!confirm('入力内容が変更されています。破棄して閉じますか？')) {
                  return;
                }
              }
              closeCurrentModal(true);
            }
          }
        });

        // HTMX trigger handler for closeModal
        document.body.addEventListener('closeModal', function() {
          closeCurrentModal(true);
        });

        // HTMX afterSwap handler to detect modal injection and re-render Lucide icons
        document.body.addEventListener('htmx:afterSwap', function(e) {
          const target = e.detail.target;
          if (target && target.id === 'modal-container') {
            const hasModal = target.querySelector('#active-modal, #quickNoteModal, #timelineModal, #deleteModal, .modal-backdrop-clickable');
            if (hasModal) {
              openModalSync(true);
            }
          }
          if (window.lucide) lucide.createIcons();
        });

        document.addEventListener('DOMContentLoaded', function() {
          if (window.lucide) lucide.createIcons();
        });

        function insertTemplate(type) {
          const textarea = document.getElementById('aiInput');
          if (!textarea) return;
          if (type === 'markdown') {
            textarea.value = '- 出発地: 東京 (羽田 / HND)\\n- 目的地: パリ (CDG)\\n- 往路日: 2026/05/01, 復路日: 2026/05/08\\n- 乗継: 直行便のみ\\n- 目標予算: 160,000 円以下\\n- 希望航空会社: ANA, JAL, エールフランス';
          } else if (type === 'yaml') {
            textarea.value = 'trip: round_trip\\norigin: HND\\ndestination: CDG\\noutbound_date: 2026-05-01\\ninbound_date: 2026-05-08\\nmax_stops: direct_only\\ntarget_price: 160000\\npreferred_airlines:\\n  - ANA\\n  - JAL';
          } else if (type === 'natural') {
            textarea.value = '5月1日から8日まで東京からパリへの往復航空券を探しています。直行便希望で予算は16万円以内です。';
          }
          textarea.focus();
        }
        window.insertTemplate = insertTemplate;
      ")]]]))
