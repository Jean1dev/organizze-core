(ns routes.organizze-api.interest-test
  (:require [app.components.server-component :refer [url-for]]
            [app.core :as core]
            [app.services.organizze-external-api :as external-api]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component])
  (:import (java.net ServerSocket)
           (java.time LocalDate)
           (org.testcontainers.containers MySQLContainer)))

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
  (doto (MySQLContainer. "mysql:8.0")
    (.withUrlParam "useSSL" "false")
    (.withUrlParam "allowPublicKeyRetrieval" "true")
    (.withUrlParam "verifyServerCertificate" "false")))

(defn current-month-transactions
  []
  (let [current (LocalDate/now)
        date-str (str current)]
    [{:id                3142390127
      :description       "RECARGAPAY *JEANLUCAF"
      :date              date-str
      :paid              true
      :amount_cents      -1365
      :total_installments 1
      :installment       1
      :recurring         true
      :account_id        5760998
      :category_id       105185925}
     {:id                3142390128
      :description       "RECARGAPAY *OTHER"
      :date              date-str
      :paid              true
      :amount_cents      -500
      :total_installments 1
      :installment       1
      :recurring         false
      :account_id        5760998
      :category_id        105185925}]))

(deftest get-monthly-interest-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [mock-transactions (current-month-transactions)
              mock-response {:status 200
                             :body   (cheshire/generate-string mock-transactions)}]
          (with-redefs [external-api/fetch-transactions (fn [_] mock-response)]
            (let [{:keys [status body]} (-> (sut->url sut (url-for :get-monthly-interest))
                                            (client/get {:accept           :json
                                                         :as               :json
                                                         :throw-exceptions false})
                                            (select-keys [:body :status]))]
              (is (= 200 status))
              (is (map? body))
              (is (contains? body :interest_cents))
              (is (contains? body :interest_brl))
              (is (contains? body :year))
              (is (contains? body :month))
              (is (= 74 (:interest_cents body)))
              (is (= 0.74 (:interest_brl body)))
              (is (= (.getYear (LocalDate/now)) (:year body)))
              (is (= (.getMonthValue (LocalDate/now)) (:month body)))
              (is (contains? body :items))
              (is (vector? (:items body)))
              (is (= 2 (count (:items body))))
              (is (every? #(contains? % :interest_cents) (:items body)))
              (is (every? #(contains? % :amount_cents) (:items body)))
              (is (every? #(contains? % :description) (:items body)))
              (let [items (:items body)
                    by-desc (fn [d] (first (filter #(= d (:description %)) items)))]
                (is (= 54 (:interest_cents (by-desc "RECARGAPAY *JEANLUCAF"))))
                (is (= 20 (:interest_cents (by-desc "RECARGAPAY *OTHER")))))))))
      (finally
        (.stop database-container)))))

(deftest get-monthly-interest-empty-transactions-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [mock-response {:status 200 :body "[]"}]
          (with-redefs [external-api/fetch-transactions (fn [_] mock-response)]
            (let [{:keys [status body]} (-> (sut->url sut (url-for :get-monthly-interest))
                                            (client/get {:accept           :json
                                                         :as               :json
                                                         :throw-exceptions false})
                                            (select-keys [:body :status]))]
              (is (= 200 status))
              (is (= 0 (:interest_cents body)))
              (is (= 0.0 (:interest_brl body)))
              (is (= [] (:items body)))))))
      (finally
        (.stop database-container)))))

(deftest get-monthly-interest-api-failure-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (with-redefs [external-api/fetch-transactions (fn [_] (throw (ex-info "API error" {})))]
          (let [{:keys [status body]} (-> (sut->url sut (url-for :get-monthly-interest))
                                          (client/get {:accept           :json
                                                       :as               :json
                                                       :throw-exceptions false})
                                          (select-keys [:body :status]))]
            (is (= 500 status))
            (is (contains? body :error)))))
      (finally
        (.stop database-container)))))
