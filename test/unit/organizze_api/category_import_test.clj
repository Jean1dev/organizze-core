(ns unit.organizze-api.category-import-test
  (:require [app.core :as core]
            [app.services.category-import :as category-import]
            [app.services.organizze-external-api :as external-api]
            [cheshire.core :as json]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (org.testcontainers.containers PostgreSQLContainer)))

(defn create-database-container
  []
  (PostgreSQLContainer. "postgres:16"))

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (component/stop ~bound-var)))))

(defn datasource-only-system
  [config]
  (component/system-map
    :datasource (core/datasource-component config)))

(defn get-datasource-object
  [datasource]
  (if (fn? datasource)
    (datasource)
    datasource))

(defn create-test-category
  [datasource category-data]
  (let [ds (get-datasource-object datasource)
        insert-query (-> {:insert-into [:categories]
                          :values      [category-data]}
                         (sql/format))
        _ (jdbc/execute! ds insert-query)]))

(deftest import-categories-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (datasource-only-system
               {:db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              config {:organizze-external-api {:baseUrl           "https://api.organizze.com.br/rest/v2"
                                                :headerUserName    "Test User (test@example.com)"
                                                :basicAuthPassword "test_password"
                                                :basicUsername     "test@example.com"}}
              mock-api-categories [{:id          105185905
                                    :name        "Bares e restaurantes"
                                    :color       "626491"
                                    :parent_id   nil
                                    :group_id    "bars_and_restaurants"
                                    :fixed       false
                                    :essential   false
                                    :default     false
                                    :uuid        "16171a111ba3b22033a489358aab4afa7de648da"
                                    :kind        "expenses"
                                    :archived    false}
                                   {:id          105185906
                                    :name        "Supermercado"
                                    :color       "626492"
                                    :parent_id   nil
                                    :group_id    "supermarket"
                                    :fixed       false
                                    :essential   false
                                    :default     false
                                    :uuid        "16171a111ba3b22033a489358aab4afa7de648db"
                                    :kind        "expenses"
                                    :archived    false}]
              mock-response {:status 200
                             :body   (json/generate-string mock-api-categories)}]
          (with-redefs [external-api/fetch-categories (fn [_] mock-response)]
            (let [ds (get-datasource-object datasource)
                  existing-category {:id_organizze 105185905
                                     :name         "Bares e restaurantes"
                                     :group_id     "bars_and_restaurants"
                                     :essential    false
                                     :uuid         "16171a111ba3b22033a489358aab4afa7de648da"
                                     :kind         "expenses"}]
              (create-test-category ds existing-category)
              (let [result (category-import/import-categories ds config)]
                (is (= {:imported 1
                        :total-api 2
                        :total-db 1}
                       result))
                (let [select-query (-> {:select [:name]
                                        :from   [:categories]}
                                       (sql/format))
                      all-categories (jdbc/execute! ds select-query {:builder-fn rs/as-unqualified-kebab-maps})
                      category-names (set (map :name all-categories))]
                  (is (contains? category-names "Bares e restaurantes"))
                  (is (contains? category-names "Supermercado"))
                  (is (= 2 (count category-names)))))))))
      (finally
        (.stop database-container)))))

(deftest import-categories-with-no-new-categories-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (datasource-only-system
               {:db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              config {:organizze-external-api {:baseUrl           "https://api.organizze.com.br/rest/v2"
                                                :headerUserName    "Test User (test@example.com)"
                                                :basicAuthPassword "test_password"
                                                :basicUsername     "test@example.com"}}
              mock-api-categories [{:id          105185905
                                    :name        "Bares e restaurantes"
                                    :color       "626491"
                                    :parent_id   nil
                                    :group_id    "bars_and_restaurants"
                                    :fixed       false
                                    :essential   false
                                    :default     false
                                    :uuid        "16171a111ba3b22033a489358aab4afa7de648da"
                                    :kind        "expenses"
                                    :archived    false}]
              mock-response {:status 200
                             :body   (json/generate-string mock-api-categories)}]
          (with-redefs [external-api/fetch-categories (fn [_] mock-response)]
            (let [ds (get-datasource-object datasource)
                  existing-category {:id_organizze 105185905
                                     :name         "Bares e restaurantes"
                                     :group_id     "bars_and_restaurants"
                                     :essential    false
                                     :uuid         "16171a111ba3b22033a489358aab4afa7de648da"
                                     :kind         "expenses"}]
              (create-test-category ds existing-category)
              (let [result (category-import/import-categories ds config)]
                (is (= {:imported 0
                        :total-api 1
                        :total-db 1}
                       result))
                (let [select-query (-> {:select [:name]
                                        :from   [:categories]}
                                       (sql/format))
                      all-categories (jdbc/execute! ds select-query {:builder-fn rs/as-unqualified-kebab-maps})]
                  (is (= 1 (count all-categories)))))))))
      (finally
        (.stop database-container)))))

(deftest import-categories-with-empty-database-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (datasource-only-system
               {:db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              config {:organizze-external-api {:baseUrl           "https://api.organizze.com.br/rest/v2"
                                                :headerUserName    "Test User (test@example.com)"
                                                :basicAuthPassword "test_password"
                                                :basicUsername     "test@example.com"}}
              mock-api-categories [{:id          105185905
                                    :name        "Bares e restaurantes"
                                    :color       "626491"
                                    :parent_id   nil
                                    :group_id    "bars_and_restaurants"
                                    :fixed       false
                                    :essential   false
                                    :default     false
                                    :uuid        "16171a111ba3b22033a489358aab4afa7de648da"
                                    :kind        "expenses"
                                    :archived    false}
                                   {:id          105185906
                                    :name        "Supermercado"
                                    :color       "626492"
                                    :parent_id   nil
                                    :group_id    "supermarket"
                                    :fixed       false
                                    :essential   false
                                    :default     false
                                    :uuid        "16171a111ba3b22033a489358aab4afa7de648db"
                                    :kind        "expenses"
                                    :archived    false}]
              mock-response {:status 200
                             :body   (json/generate-string mock-api-categories)}]
          (with-redefs [external-api/fetch-categories (fn [_] mock-response)]
            (let [ds (get-datasource-object datasource)
                  result (category-import/import-categories ds config)]
              (is (= {:imported 2
                      :total-api 2
                      :total-db 0}
                     result))
              (let [select-query (-> {:select [:name]
                                      :from   [:categories]}
                                     (sql/format))
                    all-categories (jdbc/execute! ds select-query {:builder-fn rs/as-unqualified-kebab-maps})
                    category-names (set (map :name all-categories))]
                (is (contains? category-names "Bares e restaurantes"))
                (is (contains? category-names "Supermercado"))
                (is (= 2 (count category-names))))))))
      (finally
        (.stop database-container)))))

