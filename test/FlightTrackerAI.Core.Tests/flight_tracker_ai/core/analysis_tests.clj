(ns flight-tracker-ai.core.analysis-tests
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [flight-tracker-ai.core.analysis :as analysis])
  (:import [System DateTimeOffset TimeSpan Guid]))

(deftest test-calculate-elapsed-minutes-timezones
  (testing "calculate-elapsed-minutes correctly computes duration across timezones"
    ;; HND (Tokyo +09:00) 10:35 -> SIN (Singapore +08:00) 16:35
    ;; 10:35 (+9) = 01:35 UTC. 16:35 (+8) = 08:35 UTC. Elapsed = 7h = 420m
    (let [dep (DateTimeOffset. 2026 5 1 10 35 0 (TimeSpan/FromHours 9.0))
          arr (DateTimeOffset. 2026 5 1 16 35 0 (TimeSpan/FromHours 8.0))
          minutes (analysis/calculate-elapsed-minutes dep arr)]
      (is (= 420 minutes)))))

(deftest test-calculate-elapsed-minutes-dateline
  (testing "calculate-elapsed-minutes correctly handles dateline crossing"
    ;; HND (Tokyo +09:00) Dep: 2026-08-10 21:00 -> HNL (Honolulu -10:00) Arr: 2026-08-10 09:30
    ;; 21:00 (+9) = 12:00 UTC. 09:30 (-10) = 19:30 UTC. Elapsed = 7.5h = 450m
    (let [dep (DateTimeOffset. 2026 8 10 21 0 0 (TimeSpan/FromHours 9.0))
          arr (DateTimeOffset. 2026 8 10 9 30 0 (TimeSpan/FromHours -10.0))
          minutes (analysis/calculate-elapsed-minutes dep arr)]
      (is (= 450 minutes)))))

(deftest test-summarize-leg
  (testing "summarize-leg correctly calculates multi-segment flight and layover totals"
    (let [seg1 {:leg-index 0
                :segment-index 0
                :departure-airport "HND"
                :arrival-airport "SIN"
                :marketing-airline "全日空"
                :flight-duration-minutes 420
                :layover-minutes-next 135}
          seg2 {:leg-index 0
                :segment-index 1
                :departure-airport "SIN"
                :arrival-airport "CDG"
                :marketing-airline "シンガポール航空"
                :flight-duration-minutes 805
                :layover-minutes-next nil}
          summary (analysis/summarize-leg [seg1 seg2])]
      (is (= 1225 (:total-flight-minutes summary)))
      (is (= 135 (:total-layover-minutes summary)))
      (is (= 1360 (:total-duration-minutes summary))))))

(deftest test-find-lowest-offer
  (testing "find-lowest-offer returns the lowest priced offer"
    (let [o1 {:id (Guid/NewGuid) :price-jpy 185000 :provider :skyscanner}
          o2 {:id (Guid/NewGuid) :price-jpy 148200 :provider :google-flights}
          o3 {:id (Guid/NewGuid) :price-jpy 192000 :provider :google-flights}
          lowest (analysis/find-lowest-offer [o1 o2 o3])]
      (is (some? lowest))
      (is (= 148200 (:price-jpy lowest)))
      (is (= :google-flights (:provider lowest))))))

(deftest test-evaluate-price-opportunity
  (testing "evaluate-price-opportunity triggers notification on target met or drop"
    (let [task {:id (Guid/NewGuid)
                :target-price-jpy 160000
                :last-lowest-price-jpy 170000}
          offer {:price-jpy 148200}
          eval-res (analysis/evaluate-price-opportunity task offer)]
      (is (true? (:is-target-met eval-res)))
      (is (true? (:should-notify eval-res)))
      (is (< (:price-change-percent eval-res) -10.0)))))

(deftest test-humanize-error-message
  (testing "humanize-error-message converts technical errors to user-friendly text"
    (is (.Contains (analysis/humanize-error-message "Timeout expired waiting for selector") "タイムアウト"))
    (is (.Contains (analysis/humanize-error-message "Press and hold challenge detected") "セキュリティ認証チャレンジ"))
    (is (.Contains (analysis/humanize-error-message "429 Too Many Requests") "アクセス制限"))))
