(ns demoapps.basic-app
  (:require [ib-re-actor-976-plus.gateway :as gateway]
            [ib-re-actor-976-plus.mapping :refer [map->]]
            [ib-re-actor-976-plus.client-socket :as cs])
  (:import (com.ib.client Contract Order))
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;EXAMPLES WORKFLOWS;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;USE AT YOUR OWN RISK - YOU SHOULD REALLY BE USING A PAPER ACCOUNT;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;Try running these functions one by one.
;We will create a connection that simply prints IB messages

(def account "U1217609")                                   ;you need to fill this for the examples to work
(def requests (atom 0))                                     ; this is the counter for IB requests
(def default-port 7496)
(def default-paper-port 7497)

;Create the connection. This will return a map with the client and the subscribers.
(def connection (gateway/connect 2 "localhost" default-port println)) ;you may need to change the port

;Create data structures - either plain maps or IB objects
(def ESZ2-map {:symbol "ES" :sec-type "FUT" :exchange "CME" :currency "USD" :last-trade-date-or-contract-month "20221216" :multiplier 50})
(def ESZ2-contract (map-> com.ib.client.Contract ESZ2-map))

(def ESU0C3000-map {:symbol "ES" :sec-type "FOP" :exchange "CME" :currency "USD" :last-trade-date-or-contract-month "20200918" :right :call :strike 3000 :multiplier 50})
(def ESU0C3000-contract (map-> com.ib.client.Contract ESU0C3000-map))

(def MSFT-map {:symbol "MSFT" :sec-type "STK" :currency "USD" :exchange "SMART" :primary-exch "NASDAQ"})
(def MSFT-contract (map-> com.ib.client.Contract MSFT-map))

(def safe-limit-buy-order-map {:action :buy :quantity 2 :order-type :limit :limit-price 1})
(def safe-limit-buy-order-order (map-> com.ib.client.Order safe-limit-buy-order-map))


;Example calls - these should all print stuff - could be errors if you don't subscribe to the market daa

(defn example-historical-request []
  (cs/request-historical-data
    (:ecs connection)
    (swap! requests inc)
    ESZ2-map
    "20221210 00:00:00 UTC"                                      ;the format is important. It defaults to TWS timezone if not specified
    10 :days
    1 :day
    :trades
    true
    1
    false))

(defn example-historical-request-interop []
  (.reqHistoricalData
    (:ecs connection)
    (swap! requests inc)
    ESZ2-contract
    "20221210 00:00:00 UTC" ;the format is important. Having issues with US/Eastern
    "10 D"
    "1 day"
    "TRADES"
    1
    1
    false
    nil))

(defn example-streaming-data-request []
  (cs/request-market-data
    (:ecs connection)
    (swap! requests inc)
    ESZ2-map
    nil
    false
    false))

(defn example-contract-detail-request []
  (cs/request-contract-details (:ecs connection) (swap! requests inc) MSFT-map))

(defn example-sending-order [order-id]
  ;the next available order-id would have been printed at connection
  (cs/place-order (:ecs connection) order-id MSFT-map safe-limit-buy-order-map))

(defn example-sending-order-interop [order-id]
  ;the next available order-id would have been printed at connection
  (.placeOrder (:ecs connection) order-id MSFT-contract safe-limit-buy-order-order))

(defn example-order-cancel [order-id]
  ;need to submit same order-id as above
  (cs/cancel-order (:ecs connection) order-id))

(defn example-account-data-request []
  ;will keep going until you send the same request with false instead of true
  (cs/request-account-updates (:ecs connection) true account))

;;;;;;;;;;;;;;;
;END BASIC APP;
;;;;;;;;;;;;;;;
