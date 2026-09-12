(ns flight-tracker-ai.web.views.html-dsl-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.web.views.html-dsl :as h]
            [clojure.string :as str]))

(deftest test-render-html-basic
  (testing "render-html renders tags and attributes properly"
    (let [res (h/render-html [:div {:class "test-class" :id "main"} "Hello World"])]
      (is (= "<div class=\"test-class\" id=\"main\">Hello World</div>" res))))

  (testing "render-html escapes text properly"
    (let [res (h/render-html [:p "<script>alert('xss')</script>"])]
      (is (str/includes? res "&lt;script&gt;"))
      (is (not (str/includes? res "<script>")))))

  (testing "render-html supports void tags without closing slash/tag"
    (let [res (h/render-html [:input {:type "text" :name "query"}])]
      (is (= "<input type=\"text\" name=\"query\">" res))))

  (testing "render-html renders raw content inside script and style tags as body, not attributes"
    (let [res1 (h/render-html [:script (h/raw "console.log('run');")])
          res2 (h/render-html [:script {:type "text/javascript"} (h/raw "alert(1);")])
          res3 (h/render-html [:style (h/raw ".btn { color: red; }")])
          res4 (h/render-html [:div (h/raw "<span>raw</span>")])]
      (is (= "<script>console.log('run');</script>" res1))
      (is (= "<script type=\"text/javascript\">alert(1);</script>" res2))
      (is (= "<style>.btn { color: red; }</style>" res3))
      (is (= "<div><span>raw</span></div>" res4))))
)
