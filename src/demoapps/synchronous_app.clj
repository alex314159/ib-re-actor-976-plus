(ns demoapps.synchronous-app
  (:require [ib-re-actor-976-plus.gateway :as g]
            [ib-re-actor-976-plus.synchronous :as sync]))

(def conn (g/connect 2 "localhost" 7496))

(def t (sync/server-time conn))
(def t-proto-buv (sync/server-time-proto-buf conn))

(def pos (sync/positions conn))
(def pos-proto-buf (sync/positions-proto-buf conn))

(def apple (sync/contract-details conn {:symbol "AAPL" :sec-type "STK" :exchange "SMART" :currency "USD"}))
(def apple-proto-buf (sync/contract-details-proto-buf conn {:symbol "AAPL" :sec-type "STK" :exchange "SMART" :currency "USD"}))