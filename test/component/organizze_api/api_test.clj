(ns component.organizze-api.api-test
  (:require [app.components.server-component :refer [url-for]]
            [app.core :as core]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component])
  (:import (java.net ServerSocket)
           (java.util UUID)
           (org.testcontainers.containers MySQLContainer)))

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (component/stop ~bound-var)))))

(defn sut->url
  [sut path]
  (str/join ["http://localhost:"
             (-> sut :server-component :config :server :port)
             path]))

(defn create-database-container
  []
  (doto (MySQLContainer. "mysql:8.0")
    (.withUrlParam "useSSL" "false")
    (.withUrlParam "allowPublicKeyRetrieval" "true")
    (.withUrlParam "verifyServerCertificate" "false")))

(defn get-free-port
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(deftest greeting-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server {:port 8080}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (is (= {:body   "Hello, World!"
                :status 200}
               (-> (sut->url sut (url-for :home))
                   (client/get {:accept :json})
                   (select-keys [:body :status])))))
      (finally
        (.stop database-container)))))

(deftest get-todo-test
  (let [database-container (create-database-container)
        todo-id-1 (str (UUID/randomUUID))
        todo-1 {:id    todo-id-1
                :name  "My todo for test"
                :items [{:id   (str (UUID/randomUUID))
                         :name "finish the test"}]}]
    (try
      (.start database-container)
      (with-system
        [sut (core/organizze-api-system
               {:server {:port (get-free-port)}
                :db-spec {:jdbcUrl  (.getJdbcUrl database-container)
                          :username (.getUsername database-container)
                          :password (.getPassword database-container)}})]
        (reset! (-> sut :in-memory-state-component :state-atom)
                [todo-1])
        (is (= {:body   todo-1
                :status 200}
               (-> (sut->url sut
                             (url-for :get-todo
                                      {:path-params {:todo-id todo-id-1}}))
                   (client/get {:accept           :json
                                :as               :json
                                :throw-exceptions false})
                   (select-keys [:body :status]))))
        (testing "Empty body is return for random todo id"
          (is (= {:body   ""
                  :status 404}
                 (-> (sut->url sut
                               (url-for :get-todo
                                        {:path-params {:todo-id (UUID/randomUUID)}}))
                     (client/get {:throw-exceptions false})
                     (select-keys [:body :status]))
                 ))))
      (finally
        (.stop database-container)))))

(deftest a-simple-api-test
  (is (= 1 1)))