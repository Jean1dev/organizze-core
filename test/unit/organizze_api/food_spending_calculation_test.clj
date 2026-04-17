(ns unit.organizze-api.food-spending-calculation-test
  (:require [app.services.food-spending-calculation :as food-calculation]
            [clojure.test :refer :all])
  (:import (java.time LocalDate)))

(deftest food-category?-test
  (is (true? (food-calculation/food-category? {:name "Bares e restaurantes"})))
  (is (true? (food-calculation/food-category? {:name "Alimentação"})))
  (is (true? (food-calculation/food-category? {:name "Meu Almoco"})))
  (is (true? (food-calculation/food-category? {:name "Mercado"})))
  (is (false? (food-calculation/food-category? {:name "Transporte"})))
  (is (false? (food-calculation/food-category? {:name "Saúde"})))
  (is (false? (food-calculation/food-category? {}))))

(deftest food-category-ids-test
  (let [categories [{:id 1 :name "Bares e restaurantes"}
                    {:id 2 :name "Transporte"}
                    {:id 3 :name "Mercado"}
                    {:id 4 :name "Alimentação"}
                    {:id 5 :name "Saúde"}
                    {:id 6 :name "Meu Almoco"}]
        ids (food-calculation/food-category-ids categories)]
    (is (= #{1 3 4 6} ids)))
  (is (= #{} (food-calculation/food-category-ids [])))
  (is (= #{} (food-calculation/food-category-ids [{:id 1 :name "Transporte"}]))))

(deftest expense?-test
  (is (false? (food-calculation/expense? {:amount_cents 100})))
  (is (true? (food-calculation/expense? {:amount_cents -500})))
  (is (false? (food-calculation/expense? {:amount_cents 0})))
  (is (false? (food-calculation/expense? {}))))

(deftest transaction-in-month?-test
  (is (true? (food-calculation/transaction-in-month? {:date "2026-04-10"} 2026 4)))
  (is (false? (food-calculation/transaction-in-month? {:date "2026-04-10"} 2026 3)))
  (is (false? (food-calculation/transaction-in-month? {:date "2025-04-10"} 2026 4)))
  (is (false? (food-calculation/transaction-in-month? {} 2026 4)))
  (is (true? (food-calculation/transaction-in-month? {:date "2026-01-01"} 2026 1)))
  (is (true? (food-calculation/transaction-in-month? {:date "2026-12-31"} 2026 12))))

(deftest food-transaction?-test
  (let [food-ids #{1 3 5}]
    (is (true? (food-calculation/food-transaction? {:category_id 1} food-ids)))
    (is (true? (food-calculation/food-transaction? {:category_id 5} food-ids)))
    (is (false? (food-calculation/food-transaction? {:category_id 2} food-ids)))
    (is (false? (food-calculation/food-transaction? {:category_id 99} food-ids)))
    (is (false? (food-calculation/food-transaction? {} food-ids)))))

(deftest total-food-spending-cents-test
  (let [transactions [{:amount_cents -5000} {:amount_cents -3000} {:amount_cents -2000}]]
    (is (= 10000 (food-calculation/total-food-spending-cents transactions))))
  (is (= 0 (food-calculation/total-food-spending-cents [])))
  (let [transactions [{:amount_cents -1365}]]
    (is (= 1365 (food-calculation/total-food-spending-cents transactions)))))

(deftest monthly-food-transactions-test
  (let [categories [{:id 1 :name "Bares e restaurantes"}
                    {:id 2 :name "Transporte"}
                    {:id 3 :name "Mercado"}]
        transactions [{:id 101 :category_id 1 :amount_cents -5000 :date "2026-04-10" :description "Restaurante X"}
                      {:id 102 :category_id 2 :amount_cents -2000 :date "2026-04-10" :description "Uber"}
                      {:id 103 :category_id 3 :amount_cents -3000 :date "2026-04-10" :description "Supermercado"}
                      {:id 104 :category_id 1 :amount_cents -1000 :date "2026-03-10" :description "Bar mes anterior"}
                      {:id 105 :category_id 1 :amount_cents  1000 :date "2026-04-10" :description "Estorno restaurante"}]
        result (food-calculation/monthly-food-transactions categories transactions 2026 4)]
    (is (= 2 (count result)))
    (is (every? #(neg? (:amount_cents %)) result))
    (is (some #(= 101 (:id %)) result))
    (is (some #(= 103 (:id %)) result))
    (is (not (some #(= 102 (:id %)) result)) "Transporte should be excluded")
    (is (not (some #(= 104 (:id %)) result)) "Previous month should be excluded")
    (is (not (some #(= 105 (:id %)) result)) "Income (positive) should be excluded")))

(deftest monthly-food-transactions-all-categories-test
  (let [categories [{:id 10 :name "Bares e restaurantes"}
                    {:id 11 :name "Alimentação"}
                    {:id 12 :name "Meu Almoco"}
                    {:id 13 :name "Mercado"}]
        current-date (str (LocalDate/now))
        year (.getYear (LocalDate/now))
        month (.getMonthValue (LocalDate/now))
        transactions [{:id 1 :category_id 10 :amount_cents -1000 :date current-date :description "Bar"}
                      {:id 2 :category_id 11 :amount_cents -2000 :date current-date :description "Alimentação"}
                      {:id 3 :category_id 12 :amount_cents -3000 :date current-date :description "Almoço"}
                      {:id 4 :category_id 13 :amount_cents -4000 :date current-date :description "Mercado"}]
        result (food-calculation/monthly-food-transactions categories transactions year month)]
    (is (= 4 (count result)) "All 4 food categories should be included")
    (is (= 10000 (food-calculation/total-food-spending-cents result)))))

(deftest monthly-food-transactions-empty-test
  (is (= [] (food-calculation/monthly-food-transactions [] [] 2026 4)))
  (let [categories [{:id 1 :name "Bares e restaurantes"}]]
    (is (= [] (food-calculation/monthly-food-transactions categories [] 2026 4))))
  (let [transactions [{:id 1 :category_id 99 :amount_cents -1000 :date "2026-04-10"}]]
    (is (= [] (food-calculation/monthly-food-transactions [] transactions 2026 4)))))

(deftest transaction-summary-test
  (let [tx {:id 42 :description "Restaurante Fino" :date "2026-04-10" :amount_cents -15000 :category_id 1}
        summary (food-calculation/transaction-summary tx)]
    (is (= 42 (:id summary)))
    (is (= "Restaurante Fino" (:description summary)))
    (is (= "2026-04-10" (:date summary)))
    (is (= -15000 (:amount_cents summary)))
    (is (= 1 (:category_id summary)))))
