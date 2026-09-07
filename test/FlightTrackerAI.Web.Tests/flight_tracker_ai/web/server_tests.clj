(ns flight-tracker-ai.web.server-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.web.server :as server]
            [clojure.string :as str])
  (:import [System Guid]
           [System.Net.Http HttpClient]))

(defn- create-test-db []
  (let [conn-str (str "Data Source=file:server_db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared")]
    (db/initialize-database conn-str)
    conn-str))

(deftest test-server-starts-and-serves-dashboard
  (testing "HttpListener server starts and serves 200 on /"
    (let [conn-str (create-test-db)
          port "58921"
          listener (server/start-server conn-str port)
          client (HttpClient.)]
      (try
        (let [resp (.GetResult (.GetAwaiter (.GetAsync client (str "http://localhost:" port "/"))))
              body (.GetResult (.GetAwaiter (.ReadAsStringAsync (.Content resp))))]
          (is (= 200 (int (.StatusCode resp))))
          (is (str/includes? body "FlightTrackerAI")))
        (finally
          (.Stop listener)
          (.Close listener))))))
