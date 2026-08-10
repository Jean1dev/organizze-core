(ns routes.organizze-api.monthly-summary-test
  (:require [app.components.server-component :refer [url-for]]
            [app.core :as core]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component])
  (:import (java.net ServerSocket)
           (org.testcontainers.containers PostgreSQLContainer)))

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (component/stop ~bound-var)))))

(defn sut->url
  [sut path]
  (str/join ["http://localhost:"
             (-> sut :server-component :config :server :port)
             path]))

(defn get-free-port
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn create-database-container
  []
  (PostgreSQLContainer. "postgres:16"))

(defn post-interest! [sut amount year month]
  (client/post (sut->url sut (url-for :post-monthly-interest))
               {:accept           :json
                :content-type     :json
                :body             (cheshire/generate-string {:amount_cents amount :year year :month month})
                :as               :json
                :throw-exceptions false}))

(defn post-food! [sut amount year month]
  (client/post (sut->url sut (url-for :post-food-spending))
               {:accept           :json
                :content-type     :json
                :body             (cheshire/generate-string {:amount_cents amount :year year :month month})
                :as               :json
                :throw-exceptions false}))

(deftest get-monthly-summary-default-last-6-months-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (post-interest! sut 4200 2026 1)
        (post-food! sut 85000 2026 1)
        (post-interest! sut 3800 2026 2)
        (let [{:keys [status body]} (-> (sut->url sut (url-for :get-monthly-summary))
                                        (client/get {:accept           :json
                                                     :as               :json
                                                     :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 200 status))
          (is (map? body))
          (is (contains? body :data))
          (is (vector? (:data body)))))
      (finally
        (.stop database-container)))))

(deftest get-monthly-summary-filter-by-year-and-month-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (post-interest! sut 4200 2026 3)
        (post-food! sut 85000 2026 3)
        (post-interest! sut 9999 2025 12)
        (let [{:keys [status body]} (-> (str (sut->url sut (url-for :get-monthly-summary)) "?year=2026&month=3")
                                        (client/get {:accept           :json
                                                     :as               :json
                                                     :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 200 status))
          (is (= 1 (count (:data body))))
          (let [entry (first (:data body))]
            (is (= 2026 (:year entry)))
            (is (= 3 (:month entry)))
            (is (= 4200 (:interest_cents entry)))
            (is (= 85000 (:food_spending_cents entry))))))
      (finally
        (.stop database-container)))))

(deftest get-monthly-summary-filter-by-year-only-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (post-food! sut 10000 2025 6)
        (post-food! sut 20000 2025 7)
        (post-interest! sut 500 2024 1)
        (let [{:keys [status body]} (-> (str (sut->url sut (url-for :get-monthly-summary)) "?year=2025")
                                        (client/get {:accept           :json
                                                     :as               :json
                                                     :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 200 status))
          (is (= 2 (count (:data body))))
          (is (every? #(= 2025 (:year %)) (:data body)))))
      (finally
        (.stop database-container)))))

(deftest get-monthly-summary-null-when-no-record-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (post-interest! sut 1000 2026 5)
        (let [{:keys [status body]} (-> (str (sut->url sut (url-for :get-monthly-summary)) "?year=2026&month=5")
                                        (client/get {:accept           :json
                                                     :as               :json
                                                     :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 200 status))
          (let [entry (first (:data body))]
            (is (= 1000 (:interest_cents entry)))
            (is (nil? (:food_spending_cents entry))))))
      (finally
        (.stop database-container)))))

(deftest get-monthly-summary-empty-result-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [status body]} (-> (str (sut->url sut (url-for :get-monthly-summary)) "?year=1999&month=1")
                                        (client/get {:accept           :json
                                                     :as               :json
                                                     :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 200 status))
          (is (= [] (:data body)))))
      (finally
        (.stop database-container)))))
