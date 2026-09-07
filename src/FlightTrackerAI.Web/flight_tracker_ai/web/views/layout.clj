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
      ;; Toast Container
      [:div {:id "toastContainer" :class "fixed top-4 right-4 z-50 flex flex-col space-y-2 pointer-events-none"}]

      ;; Header
      [:header {:class "border-b border-slate-800 bg-slate-950/90 backdrop-blur sticky top-0 z-40"}
       [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between"}
        [:div {:class "flex items-center space-x-3"}
         [:div {:class "bg-sky-600 p-2 rounded-lg text-white shadow-md flex items-center justify-center w-9 h-9"}
          [:i {:class "fa-solid fa-plane text-base"}]]
         [:div
          [:div {:class "flex items-center space-x-1"}
           [:span {:class "text-lg font-bold tracking-tight text-white"} "FlightTracker"
            [:span {:class "text-sky-400"} "AI"]]]
          [:p {:class "text-[10px] text-slate-400 hidden sm:block leading-none mt-0.5"}
           "Google Flights & Skyscanner 自動巡回・価格監視"]]]

        [:div {:class "flex items-center space-x-2 sm:space-x-3"}
         [:div {:class "hidden md:flex items-center space-x-2 text-xs bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800"}
          [:span {:class "relative flex h-2 w-2"}
           [:span {:class "animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"}]
           [:span {:class "relative inline-flex rounded-full h-2 w-2 bg-emerald-500"}]]
          [:span {:class "text-slate-300"} "巡回ワーカー: 稼働中 (1分間隔)"]]

         [:button {:class "p-2 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition text-sm"
                   :title "全体設定"
                   :hx-get "/api/settings/modal"
                   :hx-target "#modal-container"}
          [:i {:class "fa-solid fa-gear text-base"}]]

         [:button {:class "bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold px-3 sm:px-4 py-2 rounded-lg shadow-sm transition flex items-center gap-1.5"
                   :hx-get "/api/tasks/new-modal"
                   :hx-target "#modal-container"}
          [:i {:class "fa-solid fa-plus text-xs"}]
          [:span {:class "hidden sm:inline"} "新規タスク登録"]]]]]

      ;; Main Container
      [:main {:class "flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full"}
       body-content]

      ;; Footer
      [:footer {:class "border-t border-slate-800 bg-slate-950 py-4 text-center text-xs text-slate-500"}
       [:p "FlightTrackerAI • Specification-Driven Development on ClojureCLR (.NET 10)"]]

      ;; Modal Container
      [:div {:id "modal-container"}]

      ;; Client-Side Scripts
      [:script (h/raw "
        function showToast(message, isSuccess) {
          const container = document.getElementById('toastContainer');
          if (!container) return;
          const toast = document.createElement('div');
          toast.className = 'pointer-events-auto flex items-center space-x-2 px-4 py-2.5 rounded-xl shadow-xl text-xs font-medium border transition-all duration-300 transform translate-y-2 opacity-0 ' +
            (isSuccess ? 'bg-emerald-950 border-emerald-800 text-emerald-200' : 'bg-rose-950 border-rose-800 text-rose-200');
          toast.innerHTML = `<i class=\"fa-solid ${isSuccess ? 'fa-circle-check text-emerald-400' : 'fa-circle-exclamation text-rose-400'}\"></i><span>${message}</span>`;
          container.appendChild(toast);
          setTimeout(() => { toast.classList.remove('translate-y-2', 'opacity-0'); }, 10);
          setTimeout(() => {
            toast.classList.add('opacity-0', 'translate-y-2');
            setTimeout(() => toast.remove(), 300);
          }, 4000);
        }

        function closeCurrentModal() {
          const container = document.getElementById('modal-container');
          if (container) container.innerHTML = '';
        }
      ")]]]))
