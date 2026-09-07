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
      (is (= "<input type=\"text\" name=\"query\">" res)))))
