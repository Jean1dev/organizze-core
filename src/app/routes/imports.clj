(ns app.routes.imports
  (:require [app.routes.utils :as utils]
            [app.services.category-import :as category-import]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(def import-categories-handler
  {:name :import-categories-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (log/info "Import categories handler called")
     (try
       (let [datasource (:datasource dependencies)
             config (:config dependencies)]
         (log/info "Datasource and config retrieved from dependencies")
         (let [result (category-import/import-categories (datasource) config)]
           (log/info "Import completed successfully")
           (assoc context :response (utils/ok result))))
       (catch Exception e
         (log/error e "Error importing categories")
         (assoc context :response {:status 500
                                   :headers {"Content-Type" "application/json"}
                                   :body (json/encode {:error "Failed to import categories"})}))))})

(def import-routes
  #{
    ["/import/categories" :post import-categories-handler :route-name :import-categories]
    })

