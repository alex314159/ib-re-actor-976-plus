(defproject ib-re-actor-976-plus "0.1.10.42.01-SNAPSHOT"
  :description "Clojure friendly wrapper for Interactive Brokers Java API"
  :url "https://github.com/alex314159/ib-re-actor-976-plus"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.12.3"]
                 [org.clojure/tools.logging "1.2.4"]
                 [com.google.protobuf/protobuf-java "4.29.5"]
                 [com.github.javaparser/javaparser-core "3.25.10"]]
  :plugins [[lein-marginalia "0.9.1"]]
  :profiles {:dev {:dependencies [[twsapi "10.42.01"]
                                  [midje "1.10.9"]
                                  [criterium "0.4.6"]]
                   :plugins      [[lein-midje "3.2.1"]]}}
  :main ^:skip-aot ib-re-actor-976-plus.core
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :creds :gpg}]])
