(ns dev
  (:require [app.core :as core]
            [com.stuartsierra.component.repl :as component-repl]))

(component-repl/set-init
  (fn [_]
    (core/organizze-api-system
      {:server                 {:port 8080}
       :htmx                   {:server {:port 8081}}
       :db-spec                {:jdbcUrl  "jdbc:postgresql://localhost:5432/core2"
                                :username "organizze"
                                :password "organizze123"}
       :organizze-external-api {:baseUrl           "https://api.organizze.com.br/rest/v2"
                                :headerUserName    "Jeanluca (...)"
                                :basicAuthPassword "..."
                                :basicUsername     "..."}})))