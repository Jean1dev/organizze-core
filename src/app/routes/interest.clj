(ns app.routes.interest
  (:require [app.routes.utils :as utils]
            [app.services.interest-calculation :as interest-calculation]
            [app.services.organizze-external-api :as external-api]
            [cheshire.core :as json]
            [clojure.tools.logging :as log])
  (:import (java.time LocalDate)))

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

(def interest-routes
  #{["/interest" :get get-monthly-interest-handler :route-name :get-monthly-interest]})
