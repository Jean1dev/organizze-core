(ns app.core
  (:require [app.components.example-component :as example-component]
            [app.components.in-memory-state-component :as in-memory-state-component]
            [app.components.server-component :as server-component]
            [app.config :as config]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)
           (org.flywaydb.core Flyway)))

(defn datasource-component
  [config]
  (connection/component
    HikariDataSource
    (assoc (:db-spec config)
      :init-fn (fn [datasource]
                 (log/info "Running database init")
                 (.migrate
                   (.. (Flyway/configure)
                       (dataSource datasource)
                       ; https://www.red-gate.com/blog/database-devops/flyway-naming-patterns-matter
                       (locations (into-array String ["classpath:database/migrations"]))
                       (table "schema_version")
                       (load)))))))

(defn organizze-api-system
  [config]
  (component/system-map
    :example-component (example-component/new-example-component config)
    :in-memory-state-component (in-memory-state-component/new-in-memory-component config)

    :datasource (datasource-component config)

    :server-component
    (component/using
      (server-component/new-http-server-component config)
      [:example-component
       :datasource
       :in-memory-state-component])))

(defn -main
  []
  (let [system (-> (config/read-config)
                   (organizze-api-system)
                   (component/start-system))]
    (println "App started with config:" system)
    (.addShutdownHook
      (Runtime/getRuntime)
      (new Thread #(component/stop-system system)))))