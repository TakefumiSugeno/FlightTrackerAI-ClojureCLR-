(ns flight-tracker-ai.web.server-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.web.server :as server]
            [clojure.string :as str])
  (:import [System Guid]
           [System.Net.Http HttpClient StringContent]
           [System.Text Encoding]))

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

(deftest test-server-serves-new-task-page
  (testing "HttpListener server serves standalone new task page on /tasks/new"
    (let [conn-str (create-test-db)
          port "58922"
          listener (server/start-server conn-str port)
          client (HttpClient.)]
      (try
        (let [resp (.GetResult (.GetAwaiter (.GetAsync client (str "http://localhost:" port "/tasks/new?origin=HND&destination=SIN"))))
              body (.GetResult (.GetAwaiter (.ReadAsStringAsync (.Content resp))))]
          (is (= 200 (int (.StatusCode resp))))
          (is (str/includes? body "新規フライト監視タスク登録"))
          (is (str/includes? body "HND"))
          (is (str/includes? body "SIN")))
        (finally
          (.Stop listener)
          (.Close listener))))))

(deftest test-server-returns-custom-headers-on-api
  (testing "HttpListener server properly propagates custom ASCII headers like HX-Trigger on API responses"
    (let [conn-str (create-test-db)
          port "58923"
          listener (server/start-server conn-str port)
          client (HttpClient.)]
      (try
        (let [content (StringContent. "defaultCheckIntervalHours=24" Encoding/UTF8 "application/x-www-form-urlencoded")
              resp (.GetResult (.GetAwaiter (.PostAsync client (str "http://localhost:" port "/api/settings") content)))
              headers (.Headers resp)]
          (is (= 200 (int (.StatusCode resp))))
          (is (.Contains headers "HX-Trigger"))
          (is (= "closeModal" (first (.GetValues headers "HX-Trigger")))))
        (finally
          (.Stop listener)
          (.Close listener))))))
