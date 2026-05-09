(ns ib-re-actor-976-plus.core
  (:gen-class)
  (:require [ib-re-actor-976-plus.gateway :as gateway]
            [ib-re-actor-976-plus.mapping :refer [map->]]
            [ib-re-actor-976-plus.client-socket :as cs]
            [ib-re-actor-976-plus.wrapper :as wrapper]
            [ib-re-actor-976-plus.translation :as translation]))

(defn -main
  [& args]
  (println "Detected TWS API version" translation/tws-version))
