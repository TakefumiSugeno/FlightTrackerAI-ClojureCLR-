(ns flight-tracker-ai.web.views.html-dsl
  (:require [clojure.string :as str]))

(defn- escape-html [^String s]
  (if (nil? s)
    ""
    (-> s
        (str/replace "&" "&amp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;")
        (str/replace "\"" "&quot;"))))

(defn- format-attrs [attrs]
  (if (empty? attrs)
    ""
    (str " "
         (str/join " "
                   (map (fn [[k v]]
                          (let [attr-name (name k)]
                            (if (boolean? v)
                              (if v attr-name "")
                              (str attr-name "=\"" (escape-html (str v)) "\""))))
                        attrs)))))

(defn raw [s]
  {:raw (str s)})

(defn render-html [node]
  (cond
    (nil? node) ""
    (string? node) (escape-html node)
    (number? node) (str node)
    (keyword? node) (escape-html (name node))
    (and (map? node) (contains? node :raw)) (:raw node)
    (vector? node)
    (if (empty? node)
      ""
      (let [tag (name (first node))
            rest-items (rest node)
            has-attrs (and (seq rest-items) 
                           (map? (first rest-items)) 
                           (not (contains? (first rest-items) :raw)))
            attrs (if has-attrs (first rest-items) {})
            children (if has-attrs (rest rest-items) rest-items)
            void-tags #{"area" "base" "br" "col" "embed" "hr" "img" "input" "link" "meta" "param" "source" "track" "wbr"}]
        (if (void-tags tag)
          (str "<" tag (format-attrs attrs) ">")
          (str "<" tag (format-attrs attrs) ">"
               (str/join "" (map render-html children))
               "</" tag ">"))))
    (sequential? node) (str/join "" (map render-html node))
    :else (escape-html (str node))))
