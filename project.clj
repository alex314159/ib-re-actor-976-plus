(defproject ib-re-actor-976-plus "0.3.10.51.01-SNAPSHOT"
  :description "Clojure friendly wrapper for the Interactive Brokers Java API"
  :url "https://github.com/alex314159/ib-re-actor-976-plus"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.12.5"]
                 ;; IB's TwsApi.jar, repackaged for Clojars by scripts/publish-twsapi.sh.
                 ;; GPLv3, pulled in as a plain Maven dependency - no manual .m2 install.
                 ;; protobuf-java comes in transitively from its pom. protobuf.clj imports
                 ;; com.google.protobuf directly, so this is an undeclared direct dependency
                 ;; on purpose: an explicit pin here would win over twsapi's (nearest-wins)
                 ;; and could silently hand IB's jar a protobuf it wasn't compiled against.
                 [net.clojars.alex314159/twsapi "10.51.01"]
                 [org.clojure/tools.logging "1.3.1"]
                 [com.github.javaparser/javaparser-core "3.25.10"]]
  :plugins [[lein-marginalia "0.9.1"]]
  ;; resources/com/ib/** holds IB's own Java sources, kept locally so the mapping
  ;; generator can parse them. They are GPLv3 as of 10.49 and must not be shipped
  ;; in an EPL jar. .gitignore keeps them out of git; this keeps them out of the
  ;; jar, which lein builds from :resource-paths and not from git.
  ;; The generator reads them by filesystem path, never off the classpath.
  ;; The EWrapper_*.java at the resources root are NOT excluded - wrapper.clj
  ;; slurps the matching one off the classpath at load time.
  :jar-exclusions [#"^com/"]
  :profiles {:dev {:dependencies [[midje "1.10.9"]
                                  [criterium "0.4.6"]]
                   :plugins      [[lein-midje "3.2.1"]]}}
  :main ^:skip-aot ib-re-actor-976-plus.core
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :creds :gpg}]])
