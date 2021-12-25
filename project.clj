(defproject ib-re-actor-976-plus "0.1.6-SNAPSHOT"
  :description "Clojure friendly wrapper for Interactive Brokers Java API"
  :url "https://github.com/alex314159/ib-re-actor-976-plus"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 ;[clj-time "0.14.2"]
                 [org.clojure/tools.logging "1.2.3"]
                 ;[clj-logging-config "1.9.12"]
                 ]
  :profiles {:dev {:dependencies [[twsapi "10.11.01"]
                                  [midje "1.10.5"]]
                   :plugins      [[lein-midje "3.2.1"]
                                  ;[com.gfredericks/how-to-ns "0.1.6"]
                                  ;[lein-localrepo "0.5.4"]
                                  ]
                   ;:how-to-ns {:require-docstring? false}
                   }
             }
  :main ^:skip-aot ib-re-actor-976-plus.core
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :creds :gpg}]])
