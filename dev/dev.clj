(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [app.core :as core]))

(component-repl/set-init
  (fn [_old-system]
    (core/organizze-api-system {:server {:port 8080}})))