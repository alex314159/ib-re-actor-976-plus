(defproject ib-re-actor-976-plus "0.1.0-SNAPSHOT"
  :description "Clojure friendly wrapper for InteractiveBrokers java API"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-time "0.14.2"]
                 [org.clojure/tools.logging "1.0.0"]
                 [clj-logging-config "1.9.12"]]
  :profiles {:dev {:dependencies [[twsapi "9.76.01"]
                                  [midje "1.9.1"]]
                   :plugins [[lein-midje "3.2.1"]
                             [com.gfredericks/how-to-ns "0.1.6"]]
                   :how-to-ns {:require-docstring? false}}})
