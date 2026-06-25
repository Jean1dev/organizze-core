(ns app.routes.monthly-summary
  (:require [app.routes.utils :as utils]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.time LocalDate)))

(defn- last-n-months
  "Returns a seq of [year month] pairs for the last n months, inclusive of current."
  [n]
  (let [now (LocalDate/now)]
    (for [i (range (dec n) -1 -1)]
      (let [d (.minusMonths now i)]
        [(.getYear d) (.getMonthValue d)]))))

(defn- parse-int [s]
  (when s (Integer/parseInt (str s))))

(defn- fetch-interest
  [datasource where-clause]
  (let [query (sql/format {:select   [:year :month :amount_cents]
                           :from     :monthly_interest
                           :where    where-clause
                           :order-by [[:year :asc] [:month :asc]]})]
    (jdbc/execute! datasource query {:builder-fn rs/as-unqualified-kebab-maps})))

(defn- fetch-food
  [datasource where-clause]
  (let [query (sql/format {:select   [:year :month :amount_cents]
                           :from     :monthly_food_spending
                           :where    where-clause
                           :order-by [[:year :asc] [:month :asc]]})]
    (jdbc/execute! datasource query {:builder-fn rs/as-unqualified-kebab-maps})))

(defn- build-period-filter [year month]
  (cond
    (and year month) [:and [:= :year year] [:= :month month]]
    year             [:= :year year]
    :else
    (let [months (last-n-months 6)
          conditions (mapv (fn [[y m]] [:and [:= :year y] [:= :month m]]) months)]
      (into [:or] conditions))))

(defn- index-by-ym [rows]
  (reduce (fn [acc row]
            (assoc acc [(:year row) (:month row)] (:amount-cents row)))
          {}
          rows))

(defn- build-summary [interest-rows food-rows]
  (let [interest-idx (index-by-ym interest-rows)
        food-idx     (index-by-ym food-rows)
        all-keys     (distinct (concat (keys interest-idx) (keys food-idx)))
        sorted-keys  (sort all-keys)]
    (mapv (fn [[y m]]
            {:year              y
             :month             m
             :interest_cents    (get interest-idx [y m] nil)
             :food_spending_cents (get food-idx [y m] nil)})
          sorted-keys)))

(def get-monthly-summary-handler
  {:name :get-monthly-summary-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request    (:request context)
           params     (:query-params request)
           year       (parse-int (get params :year))
           month      (parse-int (get params :month))
           datasource (:datasource dependencies)
           ds         (datasource)
           filter     (build-period-filter year month)
           interest   (fetch-interest ds filter)
           food       (fetch-food ds filter)
           summary    (build-summary interest food)]
       (assoc context :response (utils/ok {:data summary}))))})

(def monthly-summary-routes
  #{["/monthly-summary" :get get-monthly-summary-handler :route-name :get-monthly-summary]})
