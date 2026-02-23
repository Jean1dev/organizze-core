(ns app.routes.interest
  (:require [app.routes.utils :as utils]
            [app.services.interest-calculation :as interest-calculation]
            [app.services.organizze-external-api :as external-api]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [io.pedestal.http.body-params :as body-params]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [schema.core :as s])
  (:import (java.time LocalDate)))

(s/defschema
  MonthlyInterestPayload
  {:amount_cents s/Int
   :year         s/Int
   :month        s/Int})

(defn save-monthly-interest!
  [datasource payload]
  (let [row {:amount_cents (:amount_cents payload)
             :year         (:year payload)
             :month        (:month payload)}
        insert-query (sql/format {:insert-into :monthly_interest
                                  :values      [row]})
        _ (jdbc/execute! datasource insert-query)
        select-query (sql/format {:select [:id]
                                 :from   :monthly_interest
                                 :where  [:and
                                          [:= :amount_cents (:amount_cents payload)]
                                          [:= :year (:year payload)]
                                          [:= :month (:month payload)]]
                                 :order-by [[:id :desc]]
                                 :limit 1})
        result (first (jdbc/execute! datasource select-query {:builder-fn rs/as-unqualified-kebab-maps}))]
    (:id result)))

(defn current-year []
  (.getYear (LocalDate/now)))

(defn current-month []
  (.getMonthValue (LocalDate/now)))

(def get-monthly-interest-handler
  {:name :get-monthly-interest-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (try
       (let [config (:config dependencies)
             year (current-year)
             month (current-month)
             api-response (external-api/fetch-transactions config)
             transactions (json/parse-string (:body api-response) true)
             filtered-txs (interest-calculation/monthly-recargapay-transactions
                          transactions year month)
             items (mapv interest-calculation/transaction-summary-for-conference filtered-txs)
             interest-cents (interest-calculation/total-interest-cents filtered-txs)
             interest-brl (/ interest-cents 100.0)
             body {:interest_cents interest-cents
                   :interest_brl   interest-brl
                   :year           year
                   :month          month
                   :items          items}]
         (assoc context :response (utils/ok body)))
       (catch Exception e
         (log/error e "Error fetching monthly interest")
         (assoc context :response {:status 500
                                  :headers {"Content-Type" "application/json"}
                                  :body (json/encode {:error "Failed to fetch monthly interest"})}))))})

(def post-monthly-interest-handler
  {:name :post-monthly-interest-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           payload (s/validate MonthlyInterestPayload (:json-params request))
           datasource (:datasource dependencies)
           id (save-monthly-interest! (datasource) payload)]
       (assoc context :response (utils/created {:id id}))))})

(def interest-routes
  #{["/interest" :get get-monthly-interest-handler :route-name :get-monthly-interest]
    ["/interest" :post [(body-params/body-params) post-monthly-interest-handler] :route-name :post-monthly-interest]})
