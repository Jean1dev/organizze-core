(ns app.services.food-spending-calculation
  (:import (java.time LocalDate)))

(def ^:const FOOD_CATEGORIES
  #{"Bares e restaurantes" "Alimentação" "Meu Almoco" "Mercado"})

(defn food-category?
  [category]
  (contains? FOOD_CATEGORIES (get category :name "")))

(defn food-category-ids
  [categories]
  (set (map :id (filter food-category? categories))))

(defn expense?
  [transaction]
  (neg? (get transaction :amount_cents 0)))

(defn transaction-in-month?
  [transaction year month]
  (let [date-str (get transaction :date)
        tx-date (when date-str (LocalDate/parse date-str))]
    (if tx-date
      (and (= year (.getYear tx-date))
           (= month (.getMonthValue tx-date)))
      false)))

(defn food-transaction?
  [transaction food-ids]
  (contains? food-ids (get transaction :category_id)))

(defn filter-food-transactions
  [transactions food-ids year month]
  (filter (fn [tx]
            (and (food-transaction? tx food-ids)
                 (expense? tx)
                 (transaction-in-month? tx year month)))
          transactions))

(defn total-food-spending-cents
  [transactions]
  (reduce + 0 (map #(Math/abs (long (get % :amount_cents 0))) transactions)))

(defn transaction-summary
  [transaction]
  {:id           (get transaction :id)
   :description  (get transaction :description)
   :date         (get transaction :date)
   :amount_cents (get transaction :amount_cents)
   :category_id  (get transaction :category_id)})

(defn monthly-food-transactions
  [categories transactions year month]
  (let [food-ids (food-category-ids categories)]
    (vec (filter-food-transactions transactions food-ids year month))))
