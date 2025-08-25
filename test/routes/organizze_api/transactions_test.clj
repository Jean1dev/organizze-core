(ns routes.organizze-api.transactions-test
  (:require [app.components.server-component :refer [url-for]]
            [app.core :as core]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.net ServerSocket)
           (java.util UUID)
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

(defn get-datasource-object
  [datasource]
  (if (fn? datasource)
    (datasource)
    datasource))

(defn create-test-category
  [datasource]
  (let [ds (get-datasource-object datasource)
        category-data {:id_organizze 1
                       :name         "Test Category"
                       :group_id     "group1"
                       :essential    true
                       :uuid         (str (UUID/randomUUID))
                       :kind         "expense"}
        insert-query (-> {:insert-into [:categories]
                          :values      [category-data]}
                         (sql/format))
        _ (jdbc/execute! ds insert-query)
        select-query (-> {:select [:id]
                          :from   [:categories]
                          :where  [:= :uuid (:uuid category-data)]}
                         (sql/format))
        result (jdbc/execute-one!
                 ds
                 select-query
                 {:builder-fn rs/as-unqualified-lower-maps})]
    (:id result)))

(defn create-test-transaction
  [datasource transaction-data]
  (let [ds (get-datasource-object datasource)
        insert-query (-> {:insert-into [:transactions]
                          :values      [transaction-data]}
                         (sql/format))
        _ (jdbc/execute! ds insert-query)
        select-query (-> {:select [:id]
                          :from   [:transactions]
                          :where  [:and
                                   [:= :description (:description transaction-data)]
                                   [:= :category_id (:category_id transaction-data)]
                                   [:= :amount_cents (:amount_cents transaction-data)]]}
                         (sql/format))
        result (jdbc/execute-one!
                 ds
                 select-query
                 {:builder-fn rs/as-unqualified-lower-maps})]
    (:id result)))

(deftest get-all-transactions-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              category-id (create-test-category datasource)
              test-transactions [{:description  "Transaction 1"
                                  :notes        "Note 1"
                                  :category_id  category-id
                                  :amount_cents 1000
                                  :date         (java.time.LocalDate/now)}
                                 {:description  "Transaction 2"
                                  :notes        nil
                                  :category_id  category-id
                                  :amount_cents 2000
                                  :date         (java.time.LocalDate/now)}]]
          (doseq [transaction test-transactions]
            (create-test-transaction datasource transaction))
          (let [{:keys [status body]} (-> (sut->url sut
                                                    (url-for :get-transactions))
                                          (client/get {:accept           :json
                                                       :as               :json
                                                       :throw-exceptions false})
                                          (select-keys [:body :status]))]
            (is (= 200 status))
            (is (vector? body))
            (is (>= (count body) 2))
            (is (every? #(contains? % :id) body))
            (is (every? #(contains? % :description) body))
            (is (every? #(contains? % :category-id) body))
            (is (every? #(contains? % :amount-cents) body)))))
      (finally
        (.stop database-container)))))

(deftest post-transaction-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              category-id (create-test-category datasource)
              test-transaction {:description  "New Transaction"
                                :notes        "Test note"
                                :category_id  category-id
                                :amount_cents 1500
                                :tags         [{:name "tag1"}
                                               {:name "tag2"}]}
              {:keys [status body]} (-> (sut->url sut
                                                  (url-for :post-transaction))
                                        (client/post {:accept           :json
                                                      :content-type     :json
                                                      :body             (cheshire/generate-string test-transaction)
                                                      :as               :json
                                                      :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 201 status))
          (is (contains? body :id))
          (is (number? (:id body)))
          (testing "Verify the transaction was actually created in database"
            (let [transaction-id (:id body)
                  {:keys [status body]} (-> (sut->url sut
                                                      (url-for :get-transactions))
                                            (client/get {:accept           :json
                                                         :as               :json
                                                         :throw-exceptions false})
                                            (select-keys [:body :status]))]
              (is (= 200 status))
              (is (vector? body))
              (let [created-transaction (first (filter #(= (:id %) transaction-id) body))]
                (is (not (nil? created-transaction)))
                (is (= (:description test-transaction) (:description created-transaction)))
                (is (= (:category_id test-transaction) (:category-id created-transaction)))
                (is (= (:amount_cents test-transaction) (:amount-cents created-transaction))))))))
      (finally
        (.stop database-container)))))

(deftest post-transaction-invalid-data-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [invalid-transaction {:description  ""
                                   :notes        "Test note"
                                   :category_id  "invalid"
                                   :amount_cents "not-a-number"
                                   :tags         "invalid-tags"}
              {:keys [status body]} (-> (sut->url sut
                                                  (url-for :post-transaction))
                                        (client/post {:accept           :json
                                                      :content-type     :json
                                                      :body             (cheshire/generate-string invalid-transaction)
                                                      :as               :json
                                                      :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (not= 201 status))
          (is (or (and (map? body) (contains? body :error))
                  (string? body)
                  (and (map? body) (contains? body :type))))))
      (finally
        (.stop database-container)))))

(deftest post-transaction-without-tags-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              category-id (create-test-category datasource)
              test-transaction {:description  "Transaction without tags"
                                :notes        nil
                                :category_id  category-id
                                :amount_cents 2500}
              {:keys [status body]} (-> (sut->url sut
                                                  (url-for :post-transaction))
                                        (client/post {:accept           :json
                                                      :content-type     :json
                                                      :body             (cheshire/generate-string test-transaction)
                                                      :as               :json
                                                      :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 201 status))
          (is (contains? body :id))
          (is (number? (:id body)))
          (let [transaction-id (:id body)
                {:keys [status body]} (-> (sut->url sut
                                                    (url-for :get-transactions))
                                          (client/get {:accept           :json
                                                       :as               :json
                                                       :throw-exceptions false})
                                          (select-keys [:body :status]))]
            (is (= 200 status))
            (is (vector? body))
            (let [created-transaction (first (filter #(= (:id %) transaction-id) body))]
              (is (not (nil? created-transaction)))
              (is (= (:description test-transaction) (:description created-transaction)))
              (is (= (:category_id test-transaction) (:category-id created-transaction)))
              (is (= (:amount_cents test-transaction) (:amount-cents created-transaction))))))))))