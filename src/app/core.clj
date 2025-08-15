(ns app.core
  (:require [app.config :as config]
            [com.stuartsierra.component :as component]
            [app.components.example-component :as example-component]
            [app.components.pedestal-component :as pedestal-component]
            [app.components.in-memory-state-component :as in-memory-state-component]
            [io.pedestal.log :as log]
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
    :in-memory-state-component (app.components.in-memory-state-component config)

    :datasource (datasource-component config)

    :pedestal-component
    (component/using
      (pedestal-component/new-pedestal-component config)
      [:example-component
       :data-source
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