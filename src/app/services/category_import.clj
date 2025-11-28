(ns app.services.category-import
  (:require [app.services.organizze-external-api :as external-api]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn get-all-categories-from-db
  [datasource]
  (log/info "Fetching all categories from database")
  (let [select-query (sql/format {:select [:name]
                                  :from   :categories})
        result (jdbc/execute! datasource select-query {:builder-fn rs/as-unqualified-kebab-maps})
        category-names (set (map :name result))]
    (log/info (str "Found " (count category-names) " categories in database"))
    category-names))

(defn map-api-category-to-db
  [api-category]
  {:id_organizze (:id api-category)
   :name          (:name api-category)
   :group_id      (:group_id api-category)
   :essential     (:essential api-category)
   :uuid          (:uuid api-category)
   :kind          (:kind api-category)})

(defn save-category!
  [datasource category]
  (log/info (str "Saving category: " (:name category)))
  (let [insert-query (sql/format {:insert-into :categories
                                  :values      [category]})
        _ (jdbc/execute! datasource insert-query)]
    (log/info (str "Category saved successfully: " (:name category)))))

(defn fetch-api-categories
  [config]
  (log/info "Fetching categories from external API")
  (let [api-response (external-api/fetch-categories config)
        api-categories (json/parse-string (:body api-response) true)]
    (log/info (str "Fetched " (count api-categories) " categories from external API"))
    api-categories))

(defn filter-new-categories
  [api-categories db-category-names]
  (let [new-categories (filter (fn [api-cat]
                                 (not (contains? db-category-names (:name api-cat))))
                               api-categories)]
    (log/info (str "Found " (count new-categories) " new categories to import"))
    new-categories))

(defn save-all-categories
  [datasource categories]
  (doseq [category categories]
    (save-category! datasource category))
  (log/info (str "Category import completed. " (count categories) " categories imported")))

(defn build-import-result
  [imported-count api-count db-count]
  {:imported imported-count
   :total-api api-count
   :total-db  db-count})

(defn import-categories
  [datasource config]
  (log/info "Starting category import process")
  (try
    (let [db-category-names (get-all-categories-from-db datasource)
          api-categories (fetch-api-categories config)
          new-categories (filter-new-categories api-categories db-category-names)
          mapped-categories (map map-api-category-to-db new-categories)]
      (save-all-categories datasource mapped-categories)
      (build-import-result (count mapped-categories)
                          (count api-categories)
                          (count db-category-names)))
    (catch Exception e
      (log/error e "Error during category import")
      (throw e))))

