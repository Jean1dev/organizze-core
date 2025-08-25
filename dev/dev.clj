(ns dev
  (:require [app.core :as core]
            [com.stuartsierra.component.repl :as component-repl]))

(component-repl/set-init
  (fn [_]
    (core/organizze-api-system
      {:server  {:port 8080}
       :htmx    {:server {:port 8081}}
       :db-spec {:jdbcUrl  "jdbc:mysql://localhost:3306/organizze_core?useSSL=false&allowPublicKeyRetrieval=true&verifyServerCertificate=false"
                 :username "organizze"
                 :password "organizze123"}})))