(defproject ib-re-actor "0.1.4-SNAPSHOT"
  :description "Clojure friendly wrapper for InteractiveBrokers java API"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-time "0.11.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [clj-logging-config "1.9.12"]
                 [org.clojure/core.async "0.2.374"]
                 [twsapi "971.01"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]]
                   :plugins [[lein-midje "3.0.0"]]}})
