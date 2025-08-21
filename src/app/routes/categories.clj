(ns app.routes.categories
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [io.pedestal.http.body-params :as body-params]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
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
     {:status status
      :headers {"Content-Type" "application/json"}}
     (when body {:body (json/encode body)}))))

(def ok (partial response 200))
(def created (partial response 201))
(def not-found (partial response 404))

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

(def get-all-categories-handler
  {:name :get-all-categories-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [{:keys [datasource]} dependencies]
       (let [select-query (sql/format {:select [:id :name :uuid :kind]
                                       :from   :categories})
             result (jdbc/execute! (datasource) select-query {:builder-fn rs/as-unqualified-kebab-maps})]
         (assoc context :response (ok result)))))})

(def get-by-id-categories-handler
  {:name :get-by-id-categories-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [{:keys [datasource]} dependencies
           categorie-id (-> context
                             :request
                             :path-params
                             :categorie-id)
           categorie (jdbc/execute-one!
                  (datasource)
                  (-> {:select :*
                       :from   :categories
                       :where  [:= :id categorie-id]}
                      (sql/format))
                  {:builder-fn rs/as-unqualified-kebab-maps})
           response (if categorie
                      (ok categorie)
                      (not-found))]
       (assoc context :response response)))})

(def categories-routes
  #{
    ["/categories" :post [(body-params/body-params) post-categorie-handler] :route-name :post-categorie]
    ["/categories" :get get-all-categories-handler :route-name :get-categories]
    ["/categories/:categorie-id" :get get-by-id-categories-handler :route-name :get-by-id-categorie]
    })