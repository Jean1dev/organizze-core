(ns unit.organizze-api.interest-calculation-test
  (:require [app.services.interest-calculation :as interest-calculation]
            [clojure.test :refer :all])
  (:import (java.time LocalDate)))

(deftest expense?-test
  (is (false? (interest-calculation/expense? {:amount_cents 100})))
  (is (true? (interest-calculation/expense? {:amount_cents -1365})))
  (is (false? (interest-calculation/expense? {:amount_cents 0})))
  (is (false? (interest-calculation/expense? {}))))

(deftest recargapay-transaction?-test
  (is (true? (interest-calculation/recargapay-transaction? {:description "RECARGAPAY *JEANLUCAF"})))
  (is (true? (interest-calculation/recargapay-transaction? {:description "recargapay *foo"})))
  (is (false? (interest-calculation/recargapay-transaction? {:description "Other payment"})))
  (is (false? (interest-calculation/recargapay-transaction? {}))))

(deftest transaction-in-month?-test
  (is (true? (interest-calculation/transaction-in-month? {:date "2026-02-05"} 2026 2)))
  (is (false? (interest-calculation/transaction-in-month? {:date "2026-02-05"} 2026 1)))
  (is (false? (interest-calculation/transaction-in-month? {:date "2026-01-15"} 2026 2)))
  (is (false? (interest-calculation/transaction-in-month? {} 2026 2))))

(deftest filter-expenses-test
  (let [transactions [{:amount_cents 100} {:amount_cents -50} {:amount_cents -200}]]
    (is (= 2 (count (interest-calculation/filter-expenses transactions))))
    (is (every? #(neg? (:amount_cents %)) (interest-calculation/filter-expenses transactions)))))

(deftest filter-recargapay-test
  (let [transactions [{:description "RECARGAPAY *X"}
                      {:description "Other"}
                      {:description "RECARGAPAY *Y"}]]
    (is (= 2 (count (interest-calculation/filter-recargapay transactions))))))

(deftest interest-cents-for-transaction-test
  (is (= 54 (interest-calculation/interest-cents-for-transaction {:amount_cents -1365})))
  (is (= 40 (interest-calculation/interest-cents-for-transaction {:amount_cents -1000})))
  (is (= 0 (interest-calculation/interest-cents-for-transaction {:amount_cents 0}))))

(deftest total-interest-cents-test
  (let [transactions [{:amount_cents -1000} {:amount_cents -500}]]
    (is (= 60 (interest-calculation/total-interest-cents transactions))))
  (is (= 0 (interest-calculation/total-interest-cents []))))

(deftest monthly-recargapay-transactions-test
  (let [transactions [{:description "RECARGAPAY *A" :amount_cents -1000 :date "2026-02-05"}
                      {:description "RECARGAPAY *B" :amount_cents -500 :date "2026-02-10"}
                      {:description "Other" :amount_cents -200 :date "2026-01-15"}]
        result (vec (interest-calculation/monthly-recargapay-transactions transactions 2026 2))]
    (is (= 2 (count result)) "expected 2 recargapay expenses in 2026-02")
    (is (every? #(re-find #"(?i)RECARGAPAY" (or (:description %) "")) result) "all must have RECARGAPAY in description")
    (is (every? #(neg? (:amount_cents %)) result) "all must be expenses")))

(deftest transaction-summary-for-conference-test
  (let [tx {:id 1 :description "RECARGAPAY *X" :date "2026-02-05" :amount_cents -1000}
        summary (interest-calculation/transaction-summary-for-conference tx)]
    (is (= 1 (:id summary)) "id")
    (is (= "RECARGAPAY *X" (:description summary)) "description")
    (is (= "2026-02-05" (:date summary)) "date")
    (is (= -1000 (:amount_cents summary)) "amount_cents")
    (is (= 40 (:interest_cents summary)) "interest_cents 4% of 1000")))

(deftest monthly-recargapay-interest-cents-test
  (let [current (LocalDate/now)
        year (.getYear current)
        month (.getMonthValue current)
        date-str (str current)
        transactions [{:description "RECARGAPAY *A" :amount_cents -1000 :date date-str}
                      {:description "RECARGAPAY *B" :amount_cents -500 :date date-str}
                      {:description "Other" :amount_cents -200 :date date-str}
                      {:description "RECARGAPAY *C" :amount_cents 100 :date date-str}]]
    (is (= 60 (interest-calculation/monthly-recargapay-interest-cents transactions year month)) "current month: 40+20=60"))
  (let [transactions [{:description "RECARGAPAY *A" :amount_cents -1000 :date "2026-02-05"}
                      {:description "RECARGAPAY *B" :amount_cents -500 :date "2026-01-15"}]]
    (is (= 40 (interest-calculation/monthly-recargapay-interest-cents transactions 2026 2)) "2026-02: only first tx"))
  (let [transactions []]
    (is (= 0 (interest-calculation/monthly-recargapay-interest-cents transactions 2026 2)) "empty list")))
