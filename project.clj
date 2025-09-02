(defproject organizze-core "0.1.0-SNAPSHOT"
  :description "Organizze Core API"
  :url "https://github.com/yourusername/organizze-core"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [aero/aero "1.1.6"]
                 [io.pedestal/pedestal.service "0.6.0"]
                 [io.pedestal/pedestal.route "0.6.0"]
                 [io.pedestal/pedestal.jetty "0.6.0"]
                 [org.slf4j/slf4j-simple "2.0.7"]
                 [com.stuartsierra/component "1.1.0"]
                 [com.stuartsierra/component.repl "0.2.0"]
                 [clj-http/clj-http "3.12.3"]
                 [prismatic/schema "1.4.1"]
                 [com.github.seancorfield/next.jdbc "1.3.883"]
                 [mysql/mysql-connector-java "8.0.11"]
                 [com.zaxxer/HikariCP "5.0.1"]
                 [com.github.seancorfield/honeysql "2.4.1066"]
                 [org.flywaydb/flyway-core "9.21.2"]
                 [org.flywaydb/flyway-mysql "9.0.2"]
                 [hiccup/hiccup "2.0.0-RC1"]
                 [faker/faker "0.3.2"]]
  :profiles {:uberjar {:aot :all
                        :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[org.testcontainers/testcontainers "1.18.0"]
                                 [org.testcontainers/mysql "1.21.3"]]
                   :source-paths ["src" "resources" "dev" "test"]}
             :test {:dependencies [[org.testcontainers/testcontainers "1.18.0"]
                                  [org.testcontainers/mysql "1.21.3"]
                                  [io.github.cognitect-labs/test-runner "0.5.1"]]
                    :source-paths ["src" "resources" "test"]
                    :test-paths ["test"]}}
  :main app.core
  :target-path "target/%s"
  :resource-paths ["resources"]
  :source-paths ["src"]
  :test-paths ["test"]
  :min-lein-version "2.0.0"
  :jvm-opts ["-server" "-Xmx2g"]
  :clean-targets ^{:protect false} [:target-path :compile-path :resource-paths])
