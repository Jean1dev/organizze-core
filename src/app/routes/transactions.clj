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
  InstallmentsAttributes
  {:periodicity s/Str
   :total       s/Int})

(s/defschema
  Transaction
  {:description                    s/Str
   :notes                          (s/maybe s/Str)
   :category_id                    s/Int
   :amount_cents                   s/Int
   (s/optional-key :tags)          [{:name s/Str}]
   (s/optional-key :installments_attributes) InstallmentsAttributes})

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

(defn calculate-installment-dates
  [start-date periodicity total]
  (let [base-date (if (string? start-date)
                    (LocalDate/parse start-date)
                    start-date)]
    (case periodicity
      "monthly" (for [i (range total)]
                  (.plusMonths base-date i))
      "weekly" (for [i (range total)]
                 (.plusWeeks base-date i))
      "yearly" (for [i (range total)]
                 (.plusYears base-date i))
      (for [i (range total)]
        (.plusMonths base-date i)))))

(defn save-installments!
  [datasource transaction-id amount-cents installments-attributes]
  (log/info "Saving installments for transaction ID:" transaction-id "installments:" installments-attributes)
  (let [periodicity (:periodicity installments-attributes)
        total (:total installments-attributes)
        installment-amount (quot amount-cents total)
        current-date (LocalDate/now)
        installment-dates (calculate-installment-dates current-date periodicity total)
        installment-values (map-indexed (fn [idx date]
                                         {:transaction_id transaction-id
                                          :installment_number (inc idx)
                                          :due_date date
                                          :amount_cents installment-amount})
                                       installment-dates)
        insert-query (sql/format {:insert-into :installments
                                  :values      installment-values})]
    (log/info "Installment insert query:" insert-query)
    (jdbc/execute! datasource insert-query)))

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
        has-installments (contains? transaction :installments_attributes)
        transaction-data (-> transaction
                             (dissoc :tags :installments_attributes)
                             (assoc :date current-date)
                             (assoc :is_installment has-installments)
                             (assoc :installment_periodicity (when has-installments (:periodicity (:installments_attributes transaction))))
                             (assoc :installment_total (when has-installments (:total (:installments_attributes transaction)))))
        insert-query (sql/format {:insert-into :transactions
                                  :values      [transaction-data]})
        _ (jdbc/execute! datasource insert-query)
        transaction-id (get-transaction-id! datasource
                                            (:description transaction)
                                            (:category_id transaction)
                                            (:amount_cents transaction))]
    (log/info "Transaction saved with ID:" transaction-id)
    (save-transaction-tags! datasource transaction-id (or (:tags transaction) []))
    (when has-installments
      (save-installments! datasource transaction-id (:amount_cents transaction) (:installments_attributes transaction)))
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
       (let [select-query (sql/format {:select [:id :description :date :category_id :amount_cents :is_installment :installment_periodicity :installment_total]
                                       :from   :transactions})
             result (jdbc/execute! (datasource) select-query {:builder-fn rs/as-unqualified-kebab-maps})]
         (assoc context :response (ok result)))))})

(def get-transaction-installments-handler
  {:name :get-transaction-installments-handler
   :enter
   (fn [{:keys [dependencies request] :as context}]
     (let [{:keys [datasource]} dependencies
           transaction-id (Integer/parseInt (get-in request [:path-params :id]))]
       (let [select-query (sql/format {:select [:id :installment_number :due_date :amount_cents :paid :created_at]
                                       :from   :installments
                                       :where  [:= :transaction_id transaction-id]
                                       :order-by [:installment_number]})
             result (jdbc/execute! (datasource) select-query {:builder-fn rs/as-unqualified-kebab-maps})]
         (if (seq result)
           (assoc context :response (ok result))
           (assoc context :response (not-found {:error "No installments found for this transaction"}))))))})

(def transactions-routes
  #{["/transactions" :post [(body-params/body-params) post-transaction-handler] :route-name :post-transaction]
    ["/transactions" :get get-all-transactions-handler :route-name :get-transactions]
    ["/transactions/:id/installments" :get get-transaction-installments-handler :route-name :get-transaction-installments]})
