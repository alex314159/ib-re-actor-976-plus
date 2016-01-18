(ns ib-re-actor.synchronous
  "This namespace wraps asynchronous functions with the appropriate magic to
  make them synchronous.

  These are much easier to use in an interactive context (such as when using the
  REPL) but probably not what you would want to use in an application, as the
  asynchronous API is a much more natural fit for building programs that react
  to events in market data."
  (:require [ib-re-actor.gateway :as g]))


(defn single-value-handlers
  "This returns a map of handlers suitable for calls that will provide a single
  response."
  [result]
  {:data #(deliver result %)
   :error #(deliver result %)})


(defn resetting-handlers
  "This returns a map of handlers suitable for calls in which you are interested
  in the last response."
  [result]
  (let [data (atom nil)]
    {:data #(reset! data %)
     :end #(deliver result @data)
     :error #(deliver result %)}))


(defn conjing-handlers
  "This returns a map of handlers suitable for calls that return a collection of
  items and you want to return them all."
  [result]
  (let [data (atom nil)]
    {:data #(swap! data conj %)
     :end #(deliver result @data)
     :error #(deliver result %)}))


(defn server-time
  "Returns the server time"
  [connection]
  (let [result (promise)]
    (g/request-current-time connection
                            (single-value-handlers result))
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
  "Return account positions"
  [connection]
  (let [result (promise)]
    (g/request-positions connection (conjing-handlers result))
    @result))


(defn contract-details
  "Gets details for the specified contract.

  Will return a list of contract details matching the contract description. A
  non-ambiguous contract will yield a list of one item."
  [connection contract]
  (let [result (promise)]
    (g/request-contract-details connection contract
                                (conjing-handlers result))
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
