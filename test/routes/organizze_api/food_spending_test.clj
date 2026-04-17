(ns routes.organizze-api.food-spending-test
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

(defn current-date-str []
  (str (LocalDate/now)))

(defn mock-food-categories []
  [{:id        105185901
    :name      "Bares e restaurantes"
    :color     "626491"
    :parent_id nil
    :group_id  "bars"
    :kind      "expenses"}
   {:id        105185902
    :name      "Alimentação"
    :color     "626492"
    :parent_id nil
    :group_id  "food"
    :kind      "expenses"}
   {:id        105185903
    :name      "Meu Almoco"
    :color     "626493"
    :parent_id nil
    :group_id  "lunch"
    :kind      "expenses"}
   {:id        105185904
    :name      "Mercado"
    :color     "626494"
    :parent_id nil
    :group_id  "market"
    :kind      "expenses"}
   {:id        105185905
    :name      "Transporte"
    :color     "626495"
    :parent_id nil
    :group_id  "transport"
    :kind      "expenses"}])

(defn mock-transactions []
  (let [date (current-date-str)]
    [{:id          1001
      :description "Restaurante Fino"
      :date        date
      :amount_cents -8500
      :category_id 105185901}
     {:id          1002
      :description "Supermercado Big"
      :date        date
      :amount_cents -15000
      :category_id 105185904}
     {:id          1003
      :description "Uber para trabalho"
      :date        date
      :amount_cents -2500
      :category_id 105185905}
     {:id          1004
      :description "Almoço executivo"
      :date        date
      :amount_cents -3500
      :category_id 105185903}
     {:id          1005
      :description "Estorno restaurante"
      :date        date
      :amount_cents 5000
      :category_id 105185901}]))

(deftest get-food-spending-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [mock-categories-response {:status 200 :body (cheshire/generate-string (mock-food-categories))}
              mock-transactions-response {:status 200 :body (cheshire/generate-string (mock-transactions))}]
          (with-redefs [external-api/fetch-categories (fn [_] mock-categories-response)
                        external-api/fetch-transactions (fn [_] mock-transactions-response)]
            (let [{:keys [status body]} (-> (sut->url sut (url-for :get-food-spending))
                                            (client/get {:accept           :json
                                                         :as               :json
                                                         :throw-exceptions false})
                                            (select-keys [:body :status]))]
              (is (= 200 status))
              (is (map? body))
              (is (contains? body :total_cents))
              (is (contains? body :total_brl))
              (is (contains? body :year))
              (is (contains? body :month))
              (is (contains? body :items))
              (is (vector? (:items body)))
              (is (= 3 (count (:items body))) "Should include Restaurante, Supermercado and Almoco, excluding Transporte and income")
              (is (= 27000 (:total_cents body)) "8500 + 15000 + 3500 = 27000")
              (is (= 270.0 (:total_brl body)))
              (is (= (.getYear (LocalDate/now)) (:year body)))
              (is (= (.getMonthValue (LocalDate/now)) (:month body)))
              (is (every? #(contains? % :id) (:items body)))
              (is (every? #(contains? % :description) (:items body)))
              (is (every? #(contains? % :amount_cents) (:items body)))
              (is (every? #(contains? % :date) (:items body)))
              (is (every? #(contains? % :category_id) (:items body)))
              (is (every? #(neg? (:amount_cents %)) (:items body)) "All items should be expenses")))))
      (finally
        (.stop database-container)))))

(deftest get-food-spending-empty-transactions-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (with-redefs [external-api/fetch-categories (fn [_] {:status 200 :body "[]"})
                      external-api/fetch-transactions (fn [_] {:status 200 :body "[]"})]
          (let [{:keys [status body]} (-> (sut->url sut (url-for :get-food-spending))
                                          (client/get {:accept           :json
                                                       :as               :json
                                                       :throw-exceptions false})
                                          (select-keys [:body :status]))]
            (is (= 200 status))
            (is (= 0 (:total_cents body)))
            (is (= 0.0 (:total_brl body)))
            (is (= [] (:items body))))))
      (finally
        (.stop database-container)))))

(deftest get-food-spending-api-failure-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (with-redefs [external-api/fetch-categories (fn [_] (throw (ex-info "API error" {})))]
          (let [{:keys [status body]} (-> (sut->url sut (url-for :get-food-spending))
                                          (client/get {:accept           :json
                                                       :as               :json
                                                       :throw-exceptions false})
                                          (select-keys [:body :status]))]
            (is (= 500 status))
            (let [body-map (if (string? body) (cheshire/parse-string body true) body)]
              (is (contains? body-map :error))))))
      (finally
        (.stop database-container)))))

(deftest post-food-spending-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [payload {:amount_cents 27000
                       :year         2026
                       :month        4}
              {:keys [status body]} (-> (sut->url sut (url-for :post-food-spending))
                                        (client/post {:accept           :json
                                                      :content-type     :json
                                                      :body             (cheshire/generate-string payload)
                                                      :as               :json
                                                      :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 201 status))
          (is (contains? body :id))
          (is (number? (:id body)))
          (is (pos? (:id body)))))
      (finally
        (.stop database-container)))))

(deftest post-food-spending-multiple-months-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [payload-jan {:amount_cents 20000 :year 2026 :month 1}
              payload-feb {:amount_cents 25000 :year 2026 :month 2}
              post! (fn [payload]
                      (-> (sut->url sut (url-for :post-food-spending))
                          (client/post {:accept           :json
                                        :content-type     :json
                                        :body             (cheshire/generate-string payload)
                                        :as               :json
                                        :throw-exceptions false})
                          (select-keys [:body :status])))
              resp-jan (post! payload-jan)
              resp-feb (post! payload-feb)]
          (is (= 201 (:status resp-jan)))
          (is (= 201 (:status resp-feb)))
          (is (pos? (get-in resp-jan [:body :id])))
          (is (pos? (get-in resp-feb [:body :id])))
          (is (not= (get-in resp-jan [:body :id])
                    (get-in resp-feb [:body :id])) "Each month should get a distinct record")))
      (finally
        (.stop database-container)))))

(deftest post-food-spending-invalid-payload-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [invalid-payload {:amount_cents "not-a-number"
                               :year         2026
                               :month        4}
              {:keys [status]} (-> (sut->url sut (url-for :post-food-spending))
                                   (client/post {:accept           :json
                                                 :content-type     :json
                                                 :body             (cheshire/generate-string invalid-payload)
                                                 :as               :json
                                                 :throw-exceptions false})
                                   (select-keys [:status]))]
          (is (not= 201 status))))
      (finally
        (.stop database-container)))))
