(ns app.core
  (:require [app.config :as config]
            [com.stuartsierra.component :as component]
            [app.components.example-component :as example-component]
            [app.components.pedestal-component :as pedestal-component]
            [app.components.in-memory-state-component :as in-memory-state-component]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)))

(defn organizze-api-system
  [config]
  (component/system-map
    :example-component (example-component/new-example-component config)
    :in-memory-state-component (app.components.in-memory-state-component config)

    :data-source (connection/component HikariDataSource (:db-spec config))

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