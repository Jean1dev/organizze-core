(ns app.routes.transactions
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [io.pedestal.http.body-params :as body-params]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [schema.core :as s])
  (:import (java.time LocalDate)))

(s/defschema
  Transaction
  {:description           s/Str
   :notes                 (s/maybe s/Str)
   :category_id           s/Int
   :amount_cents          s/Int
   (s/optional-key :tags) [{:name s/Str}]})

(defn response
  ([status]
   (response status nil))
  ([status body]
   (merge
     {:status  status
      :headers {"Content-Type" "application/json"}}
     (when body {:body (json/encode body)}))))

(def ok (partial response 200))
(def created (partial response 201))
(def not-found (partial response 404))

(defn save-transaction-tags!
  [datasource transaction-id tags]
  (log/info "Saving tags for transaction ID:" transaction-id "tags:" tags)
  (when (seq tags)
    (let [tag-values (map (fn [tag] {:transaction_id transaction-id :name (:name tag)}) tags)
          insert-query (sql/format {:insert-into :transaction_tags
                                    :values      tag-values})]
      (log/info "Tag insert query:" insert-query)
      (jdbc/execute! datasource insert-query))))

(def get-transaction-id!
  (fn [datasource description category-id amount-cents]
    (let [select-query (sql/format {:select [:id]
                                    :from   :transactions
                                    :where  [:and
                                             [:= :description description]
                                             [:= :category_id category-id]
                                             [:= :amount_cents amount-cents]]})
          result (first (jdbc/execute! datasource select-query))]
      (log/info "Transaction ID found:" result)
      (:transactions/id result))))

(defn save-transaction!
  [datasource transaction]
  (log/info "Saving transaction:" transaction)
  (let [current-date (LocalDate/now)
        transaction-data (-> transaction
                             (dissoc :tags)
                             (assoc :date current-date))
        insert-query (sql/format {:insert-into :transactions
                                  :values      [transaction-data]})
        _ (jdbc/execute! datasource insert-query)
        transaction-id (get-transaction-id! datasource
                                            (:description transaction)
                                            (:category_id transaction)
                                            (:amount_cents transaction))]
    (log/info "Transaction saved with ID:" transaction-id)
    (save-transaction-tags! datasource transaction-id (or (:tags transaction) []))
    transaction-id))

(def post-transaction-handler
  {:name :post-transaction-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           transaction (s/validate Transaction (:json-params request))
           id (save-transaction! ((:datasource dependencies)) transaction)]
       (assoc context :response (created {:id id}))))})

(def get-all-transactions-handler
  {:name :get-all-transactions-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [{:keys [datasource]} dependencies]
       (let [select-query (sql/format {:select [:id :description :date :category_id :amount_cents]
                                       :from   :transactions})
             result (jdbc/execute! (datasource) select-query {:builder-fn rs/as-unqualified-kebab-maps})]
         (assoc context :response (ok result)))))})

(def transactions-routes
  #{["/transactions" :post [(body-params/body-params) post-transaction-handler] :route-name :post-transaction]
    ["/transactions" :get get-all-transactions-handler :route-name :get-transactions]})
