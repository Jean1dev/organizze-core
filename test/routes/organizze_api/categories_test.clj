(ns routes.organizze-api.categories-test
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

(defn create-test-categorie
  [datasource categorie-data]
  (let [ds (get-datasource-object datasource)
        insert-query (-> {:insert-into [:categories]
                          :values      [categorie-data]}
                         (sql/format))
        _ (jdbc/execute! ds insert-query)
        select-query (-> {:select [:id]
                          :from   [:categories]
                          :where  [:= :uuid (:uuid categorie-data)]}
                         (sql/format))
        result (jdbc/execute-one!
                 ds
                 select-query
                 {:builder-fn rs/as-unqualified-lower-maps})]
    (:id result)))

(deftest get-by-id-categorie-test
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
              test-categorie {:id_organizze 1
                              :name         "Test Category"
                              :group_id     "group1"
                              :essential    true
                              :uuid         (str (UUID/randomUUID))
                              :kind         "expense"}
              id (create-test-categorie datasource test-categorie)
              {:keys [status body]} (-> (sut->url sut
                                                  (url-for :get-by-id-categorie
                                                           {:path-params {:categorie-id id}}))
                                        (client/get {:accept           :json
                                                     :as               :json
                                                     :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 200 status))
          (is (= {:id           id
                  :id-organizze (:id_organizze test-categorie)
                  :name         (:name test-categorie)
                  :group-id     (:group_id test-categorie)
                  :essential    (:essential test-categorie)
                  :uuid         (:uuid test-categorie)
                  :kind         (:kind test-categorie)}
                 (select-keys body [:id :id-organizze :name :group-id :essential :uuid :kind]))))
        (testing "Empty body is return for random categorie id"
          (is (= {:body   ""
                  :status 404}
                 (-> (sut->url sut
                               (url-for :get-by-id-categorie
                                        {:path-params {:categorie-id 99999}}))
                     (client/get {:throw-exceptions false})
                     (select-keys [:body :status]))
                 ))))
      (finally
        (.stop database-container)))))

(deftest get-all-categories-test
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
              test-categories [{:id_organizze 1
                                :name         "Category 1"
                                :group_id     "group1"
                                :essential    true
                                :uuid         (str (UUID/randomUUID))
                                :kind         "expense"}
                               {:id_organizze 2
                                :name         "Category 2"
                                :group_id     "group2"
                                :essential    false
                                :uuid         (str (UUID/randomUUID))
                                :kind         "income"}]]
          (doseq [categorie test-categories]
            (create-test-categorie datasource categorie))
          (let [{:keys [status body]} (-> (sut->url sut
                                                    (url-for :get-categories))
                                          (client/get {:accept           :json
                                                       :as               :json
                                                       :throw-exceptions false})
                                          (select-keys [:body :status]))]
            (is (= 200 status))
            (is (vector? body))
            (is (>= (count body) 2))
            (is (every? #(contains? % :id) body))
            (is (every? #(contains? % :name) body))
            (is (every? #(contains? % :uuid) body))
            (is (every? #(contains? % :kind) body)))))
      (finally
        (.stop database-container)))))

(deftest post-categorie-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server  {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [test-categorie {:id_organizze 1
                              :name         "New Category"
                              :group_id     "group1"
                              :essential    true
                              :uuid         (str (UUID/randomUUID))
                              :kind         "expense"}
              {:keys [status body]} (-> (sut->url sut
                                                  (url-for :post-categorie))
                                        (client/post {:accept           :json
                                                      :content-type     :json
                                                      :body             (cheshire/generate-string test-categorie)
                                                      :as               :json
                                                      :throw-exceptions false})
                                        (select-keys [:body :status]))]
          (is (= 201 status))
          (is (contains? body :id))
          (is (number? (:id body)))
          (testing "Verify the categorie was actually created in database"
            (let [{:keys [status body]} (-> (sut->url sut
                                                      (url-for :get-by-id-categorie
                                                               {:path-params {:categorie-id (:id body)}}))
                                            (client/get {:accept           :json
                                                         :as               :json
                                                         :throw-exceptions false})
                                            (select-keys [:body :status]))]
              (is (= 200 status))
              (is (= {:id           (:id body)
                      :id-organizze (:id_organizze test-categorie)
                      :name         (:name test-categorie)
                      :group-id     (:group_id test-categorie)
                      :essential    (:essential test-categorie)
                      :uuid         (:uuid test-categorie)
                      :kind         (:kind test-categorie)}
                     (select-keys body [:id :id-organizze :name :group-id :essential :uuid :kind]))))))
        (testing "POST with invalid data should return error"
          (let [invalid-categorie {:id_organizze "invalid"
                                   :name         ""
                                   :group_id     "group1"
                                   :essential    "not-boolean"
                                   :uuid         "invalid-uuid"
                                   :kind         "invalid-kind"}
                {:keys [status body]} (-> (sut->url sut
                                                    (url-for :post-categorie))
                                          (client/post {:accept           :json
                                                        :content-type     :json
                                                        :body             (cheshire/generate-string invalid-categorie)
                                                        :as               :json
                                                        :throw-exceptions false})
                                          (select-keys [:body :status]))]
            (is (not= 201 status))
            (is (or (and (map? body) (contains? body :error))
                    (string? body)
                    (and (map? body) (contains? body :type))))))))))
