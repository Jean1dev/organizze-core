(ns app.services.organizze-external-api
  (:require [clj-http.client :as http])
  (:import (java.util Base64)))

(def ^:const TIMEOUT-MS 10000)

(defn build-basic-auth
  [config]
  (let [organizze-config (get config :organizze-external-api)
        username (get organizze-config :basicUsername)
        password (get organizze-config :basicAuthPassword)
        credentials (str username ":" password)
        encoded (.encodeToString (Base64/getEncoder) (.getBytes credentials "UTF-8"))]
    (str "Basic " encoded)))

(defn build-headers
  [config]
  (let [header-username (get-in config [:organizze-external-api :headerUserName])
        basic-auth (build-basic-auth config)]
    {"User-Agent" header-username
     "Authorization" basic-auth}))

(defn fetch-categories
  [config]
  (let [base-url (get-in config [:organizze-external-api :baseUrl])
        url (str base-url "/categories")
        headers (build-headers config)]
    (http/get url {:headers headers
                   :socket-timeout TIMEOUT-MS
                   :connection-timeout TIMEOUT-MS})))

(defn fetch-transactions
  [config]
  (let [base-url (get-in config [:organizze-external-api :baseUrl])
        url (str base-url "/transactions")
        headers (build-headers config)]
    (http/get url {:headers headers
                   :socket-timeout TIMEOUT-MS
                   :connection-timeout TIMEOUT-MS})))
