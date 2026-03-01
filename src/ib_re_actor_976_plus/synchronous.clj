(ns ib-re-actor-976-plus.synchronous
  "This namespace wraps asynchronous functions with the appropriate magic to
  make them synchronous.

  These are much easier to use in an interactive context (such as when using the
  REPL) but probably not what you would want to use in an application, as the
  asynchronous API is a much more natural fit for building programs that react
  to events in market data.

  IMPORTANT: Functions in this namespace require a connection created with
  `gateway/connect`, which includes automatic request ID management via the
  :next-id atom.

  Example usage:
    (require '[ib-re-actor-976-plus.gateway :as g])
    (require '[ib-re-actor-976-plus.synchronous :as sync])
    (def conn (g/connect 1))  ; client-id 1, paper trading on localhost
    (sync/server-time conn)
    (sync/contract-details conn {:symbol \"AAPL\" :sec-type \"STK\" :exchange \"SMART\" :currency \"USD\"})
    (g/disconnect conn)"
  (:require
   [clojure.tools.logging :as log]
   [ib-re-actor-976-plus.gateway :as g]
   [ib-re-actor-976-plus.mapping :as m]))

(defn single-value-handlers
  "This returns a map of handlers suitable for calls that will provide a single
  response."
  [result]
  {:data #(deliver result %)
   :error #(do (log/error %)
               (deliver result %))})

(defn single-value-handlers-proto-buf
  "This returns a map of handlers suitable for calls that will provide a single
  response."
  [result]
  {:data #(deliver result (m/decode-protobuf-vals %))
   :error #(do (log/error (m/decode-protobuf-vals %))
               (deliver result (m/decode-protobuf-vals %)))})

(defn resetting-handlers
  "This returns a map of handlers suitable for calls in which you are interested
  in the last response."
  [result]
  (let [data (atom nil)]
    {:data #(reset! data %)
     :end #(deliver result @data)
     :error #(do (log/error %)
                 (deliver result %))}))

(defn conjing-handlers
  "This returns a map of handlers suitable for calls that return a collection of
  items and you want to return them all."
  [result]
  (let [data (atom nil)]
    {:data #(swap! data conj %)
     :end  #(deliver result @data)
     :error #(deliver result %)}))

(def res (atom nil))

(defn conjing-handlers-proto-buf
  "This returns a map of handlers suitable for calls that return a collection of
  items and you want to return them all."
  [result]
  (let [data (atom nil)]
    {:data #(swap! data conj (m/decode-protobuf-vals %))
     :end  #(deliver result @data)
     :error #(deliver result (m/decode-protobuf-vals %))}))

(defn server-time
  "Returns the server time
  tested 20260301"
  [connection]
  (let [result (promise)]
    (g/request-current-time connection
                            (single-value-handlers result))
    @result))

(defn server-time-proto-buf
  "Returns the server time
  tested 20260301"
  [connection]
  (let [result (promise)]
    (g/request-current-time-proto-buf connection
                            (single-value-handlers-proto-buf result))
    @result))

(defn market-snapshot
  "Returns a snapshot of the market for the specified contract."
  [connection contract]
  (let [result (promise)
        data (atom nil)
        handlers {:data (fn [{:keys [field value]}] (swap! data assoc field value))
                  :error #(reset! data %)
                  :end #(deliver result @data)}]
    (g/request-market-data connection contract nil true handlers)
    @result))

(defn implied-vol
  "Returns detailed information about an option contract based on its price
  including implied volatility, greeks, etc."
  [connection contract option-price underlying-price]
  (let [result (promise)]
    (g/calculate-implied-vol connection contract option-price underlying-price
                             (single-value-handlers result))
    @result))

(defn option-price
  "Returns detailed information about an option contract based on its volatility
  including implied volatility, greeks, etc."
  [connection contract option-price underlying-price]
  (let [result (promise)]
    (g/calculate-option-price connection contract option-price underlying-price
                              (single-value-handlers result))
    @result))

(defn execute-order
  "Executes an order, returning only when the order is filled or canceled."
  [connection contract order]
  (let [result (promise)]
    (g/place-and-monitor-order connection contract order
                               (resetting-handlers result))
    @result))

(defn open-orders
  "Returns open orders"
  [connection]
  (let [result (promise)]
    (g/request-open-orders connection
                           (conjing-handlers result))
    @result))

(defn positions
  "Return account positions
  tested 20260301"
  [connection]
  (let [result (promise)]
    (g/request-positions connection (conjing-handlers result))
    @result))

(defn positions-proto-buf
  "Return account positions
  tested 20260301"
  [connection]
  (let [result (promise)]
    (g/request-positions-proto-buf connection (conjing-handlers-proto-buf result))
    @result))

(defn contract-details
  "Gets details for the specified contract.

  Will return a list of contract details matching the contract description. A
  non-ambiguous contract will yield a list of one item."
  [connection contract]
  (let [result (promise)]
    (g/request-contract-details connection contract (conjing-handlers result))
    @result))

(defn contract-details-proto-buf
  "Gets details for the specified contract.

  Will return a list of contract details matching the contract description. A
  non-ambiguous contract will yield a list of one item."
  [connection contract]
  (let [result (promise)]
    (g/request-contract-details-proto-buf connection contract (conjing-handlers-proto-buf result))
    @result))

(defn historical-data
  "Gets historical price bars for a contract."
  ([connection contract end-time duration duration-unit bar-size bar-size-unit
    what-to-show use-regular-trading-hours?]
   (let [result (promise)]
     (g/request-historical-data connection contract end-time
                                duration duration-unit bar-size bar-size-unit
                                what-to-show use-regular-trading-hours?
                                (conjing-handlers result))
     @result))
  ([connection contract end
    duration duration-unit bar-size bar-size-unit]
   (historical-data connection contract end duration duration-unit
                    bar-size bar-size-unit :trades true)))
