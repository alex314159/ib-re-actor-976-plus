(ns demoapps.synchronous-app
  (:require [ib-re-actor-976-plus.gateway :as g]
            [ib-re-actor-976-plus.synchronous :as sync]))

(def conn (g/connect 2 "localhost" 7496))

(def pos (sync/positions conn))