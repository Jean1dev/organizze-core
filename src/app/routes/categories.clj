(ns app.routes.categories
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [io.pedestal.http.body-params :as body-params]
            [next.jdbc :as jdbc]
            [schema.core :as s]))

(s/defschema
  Categorie
  {:id_organizze s/Int
   :name         s/Str
   :group_id     s/Str
   :essential    s/Bool
   :uuid         s/Str
   :kind         s/Str})

(defn response
  ([status]
   (response status nil))
  ([status body]
   (merge
     {:status  status
      :headers {"Content-Type" "application/json"}}
     (when body {:body (json/encode body)}))))

(def created (partial response 201))

(def get-categorie-id!
  (fn [datasource uuid]
    (let [select-query (sql/format {:select [:id]
                                    :from   :categories
                                    :where  [:= :uuid uuid]})
          result (first (jdbc/execute! datasource select-query))]
      (:categories/id result))))

(def save-categorie!
  (fn [datasource categorie]
    (log/info "Saving categorie:" categorie)
    (let [insert-query (sql/format {:insert-into :categories
                                    :values      [categorie]})
          _ (jdbc/execute! datasource insert-query)]
      (get-categorie-id! datasource (:uuid categorie)))))

(def post-categorie-handler
  {:name :post-categorie-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           categorie (s/validate Categorie (:json-params request))
           id (save-categorie! ((:datasource dependencies)) categorie)]
       (assoc context :response (created {:id id}))))})

(def categories-routes
  #{
    ["/categories" :post [(body-params/body-params) post-categorie-handler] :route-name :post-categorie]
    })