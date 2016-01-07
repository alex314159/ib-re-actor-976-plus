;; # The Gateway
;; The main user interface. Functions for connecting to Interactive Brokers TWS Gateway and sending
;; requests to it.
;;
;; ## Big Picture
;; The IB API requires consumers to instantate an object of type com.ib.client.EClientSocket
;; which is a socket client to the gateway, or TWS.  When instantating this object you pass in
;; another object which must implement the com.ib.client.EWrapper interface.  The EWrapper is a
;; collection of callbacks. You then invoke
;; methods (like requesting price data) on the the EClientSocket object and the results
;; (price update events) is returned by the EWrapper callbacks.
;; So the typical pattern for dealing with the IB API is to implement an EWrapper type object for each
;; application, which requires a lot of knowledge of the API.
;;
;; This library takes a slightly different approach to try ease that burden.  The library implements a listener framework
;; around each callback in the EWrapper object.  So what happens is that each time the IB Gateway
;; calls back to an method in the EWrapper, our object parses the response and packages it up into a tidy Clojure map
;; which its hands to any registered listeners.
;;
;; Consumers of this library thus do not need to care about the mechanics of EWrapper, ESocketClient etc,
;; they simply need to register a listener and will receive events.
;;
;; ## Basic Usage
;; 1. Connect to a running Gateway of TWS, using the connect function
;; 2. Register a listener for an event using the subscribe function
;; 3. Request the generation of the appropriate event using the request-* functions
;; 4. Cancel the request with the cancel-* when done.
;;
(ns ib-re-actor.gateway
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan mult go-loop tap pub close! <!]]
            [ib-re-actor.client-socket :as cs]
            [ib-re-actor.wrapper :as wrapper]))

;; Default port for live trading
(defonce default-port 7496)
;; Default port for paper trading (not real money)
(defonce default-paper-port 7497)

(defonce default-server-log-level :error)


(defn- keep-next-order-id-updated
  "This creates a go-loop that will listen for :next-valid-order-id messages and
  update the next-order-id atom accordingly."
  [m next-order-id]
  (let [ch (chan 1 (filter #(= :next-valid-order-id (:type %))))]
    (tap m ch)
    (go-loop []
      (let [v (:value (<! ch))]
        (log/info "Next order ID:" v)
        (reset! next-order-id v)
        (when v (recur))))))


(defn next-id
  "Use this utility function to get the next valid id.

  type should be one of :order :ticker :request
  "
  [type connection]
  (let [id @(get-in connection [:next-id type])]
    (swap! (get-in connection [:next-id type]) inc)
    id))


;;;
;;; Synchronous calls
;;;
(defn connect
  "Returns a connection that is required for all other calls.
  If the connection fails, returns nil. See log in that case.

  client-id identifies this connection. Only one connection can be made per
  client-id to the same server..

  host is the hostname or address of the server running IB Gateway or TWS.

  port is the port configured for that server.
  "
  ([client-id]
   (connect client-id "localhost" default-paper-port))
  ([client-id host port]
   (let [ch (chan)
         m (mult ch)
         next-order-id (atom 1)
         next-request-id (atom 1)
         next-ticker-id (atom 1)]
     (keep-next-order-id-updated m next-order-id)
     (try
       (let [ecs (cs/connect (wrapper/create ch) host port client-id)]
         (when-not (= :error default-server-log-level)
           (cs/set-server-log-level ecs default-server-log-level))
         {:ecs ecs
          :resp-chan ch
          :mult m
          :next-id {:order next-order-id
                    :request next-request-id
                    :ticker next-ticker-id}})
       (catch Exception ex
         (log/error "Error trying to connect to " host ":" port ": " ex))))))

(defn disconnect [connection]
  (cs/disconnect (:ecs connection)))

(defn is-connected? [connection]
  (and (:ecs connection)
       (cs/is-connected? (:ecs connection))))


(defn request-market-data
  "Returns the ticker-id that can be used to cancel the request."
  ([connection contract]
   (request-market-data connection contract "" false))
  ([connection contract tick-list snapshot?]
   (let [ticker-id (next-id :ticker connection)]
     (cs/request-market-data (:ecs connection)
                             ticker-id contract tick-list snapshot?)
     ticker-id)))


(defn cancel-market-data [connection ticker-id]
  (cs/cancel-market-data (:ecs connection) ticker-id))


(defn calculate-implied-vol
  "Returns the ticker-id that can be used to cancel the request."
  [connection contract option-price underlying-price]
  (let [ticker-id (next-id :ticker connection)]
    (cs/calculate-implied-volatility (:ecs connection)
                                     ticker-id contract
                                     option-price underlying-price)
    ticker-id))


(defn place-order
  "Returns the order-id that can be used to cancel or modify the order."
  [connection contract order]
  (let [order-id (next-id :order connection)]
    (cs/place-order (:ecs connection)
                    order-id contract (assoc order :order-id order-id))
    order-id))


(defn modify-order [connection order-id contract order]
  (cs/place-order (:ecs connection)
                  order-id contract (assoc order :order-id order-id))
  order-id)


(defn cancel-order [connection order-id]
  (cs/cancel-order (:ecs connection) order-id))


(defn request-contract-details [connection contract]
  (let [request-id (next-id :request connection)]
    (log/debug "Requesting contract details #" request-id " for " (pr-str contract))
    (cs/request-contract-details (:ecs connection) request-id contract)
    request-id))


(defn request-historical-data
  ([connection contract end
    duration duration-unit bar-size bar-size-unit
    what-to-show use-regular-trading-hours?]
   (let [request-id (next-id :request connection)]
     (cs/request-historical-data (:ecs connection) request-id contract
                                 end duration duration-unit bar-size bar-size-unit
                                 what-to-show use-regular-trading-hours?)
     request-id))
  ([connection contract end
    duration duration-unit bar-size bar-size-unit
    what-to-show]
   (request-historical-data connection contract end duration duration-unit
                            bar-size bar-size-unit what-to-show true))
  ([connection contract end
    duration duration-unit bar-size bar-size-unit]
   (request-historical-data connection contract end duration duration-unit
                            bar-size bar-size-unit :trades true)))


(defn request-fundamental-data
  [connection contract report-type]
  (let [request-id (next-id :request connection)]
    (cs/request-fundamental-data (:ecs connection) request-id contract report-type)
    request-id))
