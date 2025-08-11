(ns app.core
  (:require [app.config :as config]
            [com.stuartsierra.component :as component]
            [app.components.example-component :as example-component]
            [app.components.pedestal-component :as pedestal-component]))

(defn organizze-api-system
  [config]
  (component/system-map
    :example-component (example-component/new-example-component config)

    :pedestal-component
    (component/using
      (pedestal-component/new-pedestal-component config)
      [:example-component])))

(defn -main
  []
  (let [system (-> (config/read-config)
                   (organizze-api-system)
                   (component/start-system))]
    (println "App started with config:" system)
    (.addShutdownHook
      (Runtime/getRuntime)
      (new Thread #(component/stop-system system)))))