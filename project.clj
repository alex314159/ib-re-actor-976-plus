(defproject ib-re-actor-976-plus "0.1.2-SNAPSHOT"
  :description "Clojure friendly wrapper for InteractiveBrokers Java API"
  :url "https://github.com/alex314159/ib-re-actor-976-plus"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 ;[clj-time "0.14.2"]
                 [org.clojure/tools.logging "1.0.0"]
                 [clj-logging-config "1.9.12"]]
  :profiles {:dev {:dependencies [[twsapi "9.80.03"]
                                  [midje "1.9.1"]]
                   :plugins [[lein-midje "3.2.1"]
                             [com.gfredericks/how-to-ns "0.1.6"]
                             [lein-localrepo "0.5.4"]]
                   :how-to-ns {:require-docstring? false}}}
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :creds :gpg}]])
