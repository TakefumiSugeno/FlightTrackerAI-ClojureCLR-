(ns flight-tracker-ai.infrastructure.settings-repository-tests
  (:require [clojure.test :refer [deftest is testing]]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.settings-repository :as repo])
  (:import [System Guid]))

(defn- create-test-db []
  (let [conn-str (str "Data Source=file:settings_db_" (.ToString (Guid/NewGuid) "N") "?mode=memory&cache=shared")]
    (db/initialize-database conn-str)
    conn-str))

(deftest test-get-and-update-settings
  (testing "get-settings returns initial defaults and update-settings modifies them"
    (let [conn-str (create-test-db)
          initial (repo/get-settings conn-str)]
      (is (= 12 (:default-check-interval-hours initial)))
      (is (true? (:enable-google-flights initial)))
      (is (true? (:enable-skyscanner initial)))
      (is (true? (:headless-mode initial)))

      (let [updated {:default-check-interval-hours 6
                     :default-webhook-url "https://discord.com/webhook/test"
                     :openrouter-api-key "sk-or-v1-xxx"
                     :enable-google-flights true
                     :enable-skyscanner false
                     :headless-mode false}]
        (repo/update-settings conn-str updated)
        (let [reloaded (repo/get-settings conn-str)]
          (is (= 6 (:default-check-interval-hours reloaded)))
          (is (= "https://discord.com/webhook/test" (:default-webhook-url reloaded)))
          (is (= "sk-or-v1-xxx" (:openrouter-api-key reloaded)))
          (is (true? (:enable-google-flights reloaded)))
          (is (false? (:enable-skyscanner reloaded)))
          (is (false? (:headless-mode reloaded))))))))
