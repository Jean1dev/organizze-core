(ns app.core
  (:require [app.config :as config]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]))

(defn respond-handler
  [request]
  {:status 200
   :body   "Hello, World!"})

(def routes
  (route/expand-routes
    #{["/" :get respond-handler :route-name :home]}))

(defn create-server []
  (http/create-server
    {::http/routes routes
     ::http/type   :jetty
     ::http/port   8080}))

(defn start []
  (http/start (create-server)))

(defn -main
  []
  (let [config (config/read-config)]
    (println "App started with config:" config)
    (start)))