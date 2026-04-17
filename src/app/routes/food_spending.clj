(ns app.routes.food-spending
  (:require [app.routes.utils :as utils]
            [app.services.food-spending-calculation :as food-calculation]
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
  MonthlyFoodSpendingPayload
  {:amount_cents s/Int
   :year         s/Int
   :month        s/Int})

(defn save-monthly-food-spending!
  [datasource payload]
  (let [row {:amount_cents (:amount_cents payload)
             :year         (:year payload)
             :month        (:month payload)}
        insert-query (sql/format {:insert-into :monthly_food_spending
                                  :values      [row]})
        _ (jdbc/execute! datasource insert-query)
        select-query (sql/format {:select   [:id]
                                  :from     :monthly_food_spending
                                  :where    [:and
                                             [:= :amount_cents (:amount_cents payload)]
                                             [:= :year (:year payload)]
                                             [:= :month (:month payload)]]
                                  :order-by [[:id :desc]]
                                  :limit    1})
        result (first (jdbc/execute! datasource select-query {:builder-fn rs/as-unqualified-kebab-maps}))]
    (:id result)))

(defn current-year [] (.getYear (LocalDate/now)))
(defn current-month [] (.getMonthValue (LocalDate/now)))

(def get-food-spending-handler
  {:name :get-food-spending-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (try
       (let [config (:config dependencies)
             year (current-year)
             month (current-month)
             categories-response (external-api/fetch-categories config)
             categories (json/parse-string (:body categories-response) true)
             transactions-response (external-api/fetch-transactions config)
             transactions (json/parse-string (:body transactions-response) true)
             food-txs (food-calculation/monthly-food-transactions categories transactions year month)
             items (mapv food-calculation/transaction-summary food-txs)
             total-cents (food-calculation/total-food-spending-cents food-txs)
             total-brl (/ total-cents 100.0)
             body {:total_cents total-cents
                   :total_brl   total-brl
                   :year        year
                   :month       month
                   :items       items}]
         (assoc context :response (utils/ok body)))
       (catch Exception e
         (log/error e "Error fetching food spending")
         (assoc context :response {:status  500
                                   :headers {"Content-Type" "application/json"}
                                   :body    (json/encode {:error "Failed to fetch food spending"})}))))})

(def post-food-spending-handler
  {:name :post-food-spending-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           payload (s/validate MonthlyFoodSpendingPayload (:json-params request))
           datasource (:datasource dependencies)
           id (save-monthly-food-spending! (datasource) payload)]
       (assoc context :response (utils/created {:id id}))))})

(def food-spending-routes
  #{["/food-spending" :get get-food-spending-handler :route-name :get-food-spending]
    ["/food-spending" :post [(body-params/body-params) post-food-spending-handler] :route-name :post-food-spending]})
