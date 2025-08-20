(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [app.core :as core]))

(component-repl/set-init
  (fn [_]
    (core/organizze-api-system
      {:server  {:port 8080}
       :htmx    {:server {:port 8081}}
       :db-spec {:jdbcUrl  "jdbc:mysql://localhost:3306/organizze_core"
                 :username "organizze"
                 :password "organizze123"}})))