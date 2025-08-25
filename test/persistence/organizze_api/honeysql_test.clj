(ns persistence.organizze-api.honeysql-test
  (:require [app.core :as core]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (org.testcontainers.containers MySQLContainer)))


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

(defn create-database-container
  []
  (doto (MySQLContainer. "mysql:8.0")
    (.withUrlParam "useSSL" "false")
    (.withUrlParam "allowPublicKeyRetrieval" "true")
    (.withUrlParam "verifyServerCertificate" "false")))

(deftest migrations-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (datasource-only-system
               {:db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              select-query (sql/format {:select :*
                                        :from   :schema-version})
              [schema-version :as schema-versions]
              (jdbc/execute!
                (if (fn? datasource) (datasource) datasource)
                select-query
                {:builder-fn rs/as-unqualified-lower-maps})]
          (is (= ["SELECT * FROM schema_version"]
                 select-query))
          (is (= 3 (count schema-versions)))
          (is (= {:description "add todo tables"
                  :script      "V1__add_todo_tables.sql"
                  :success     true}
                 (select-keys schema-version [:description :script :success])))))
      (finally
        (.stop database-container)))))

(deftest todo-table-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (datasource-only-system
               {:db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (let [{:keys [datasource]} sut
              insert-query (-> {:insert-into [:todo]
                                :columns     [:title]
                                :values      [["my todo list"]
                                              ["other todo list"]]}
                               (sql/format))
              _ (jdbc/execute!
                  (if (fn? datasource) (datasource) datasource)
                  insert-query)
              select-results (jdbc/execute!
                               (if (fn? datasource) (datasource) datasource)
                               (-> {:select :*
                                    :from   :todo}
                                   (sql/format))
                               {:builder-fn rs/as-unqualified-lower-maps})]
          (is (= ["INSERT INTO todo (title) VALUES (?), (?)"
                  "my todo list"
                  "other todo list"] insert-query))
          (is (= 2 (count select-results)))
          (is (= #{"my todo list"
                   "other todo list"}
                 (->> select-results (map :title) (into #{}))))))
      (finally
        (.stop database-container)))))
