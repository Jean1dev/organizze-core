(ns app.services.interest-calculation
  (:import (java.time LocalDate)))

(def ^:const INTEREST_RATE 0.04)

(def ^:private recargapay-pattern #"(?i)RECARGAPAY")

(defn expense?
  [transaction]
  (neg? (get transaction :amount_cents 0)))

(defn recargapay-transaction?
  [transaction]
  (let [description (or (get transaction :description) "")]
    (boolean (re-find recargapay-pattern description))))

(defn transaction-in-month?
  [transaction year month]
  (let [date-str (get transaction :date)
        tx-date (when date-str (LocalDate/parse date-str))]
    (and tx-date
         (= year (.getYear tx-date))
         (= month (.getMonthValue tx-date)))))

(defn filter-expenses
  [transactions]
  (filter expense? transactions))

(defn filter-recargapay
  [transactions]
  (filter recargapay-transaction? transactions))

(defn filter-by-month
  [transactions year month]
  (filter #(transaction-in-month? % year month) transactions))

(defn interest-cents-for-transaction
  [transaction]
  (let [amount-cents (get transaction :amount_cents 0)
        absolute-cents (Math/abs amount-cents)]
    (long (* absolute-cents INTEREST_RATE))))

(defn total-interest-cents
  [transactions]
  (reduce + 0 (map interest-cents-for-transaction transactions)))

(defn monthly-recargapay-transactions
  [transactions year month]
  (-> transactions
      (filter-by-month year month)
      (filter-expenses)
      (filter-recargapay)))

(defn transaction-summary-for-conference
  [transaction]
  {:id             (get transaction :id)
   :description    (get transaction :description)
   :date           (get transaction :date)
   :amount_cents   (get transaction :amount_cents)
   :interest_cents  (interest-cents-for-transaction transaction)})

(defn monthly-recargapay-interest-cents
  [transactions year month]
  (-> transactions
      (monthly-recargapay-transactions year month)
      (total-interest-cents)))
