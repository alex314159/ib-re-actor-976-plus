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
            [ib-re-actor.client-socket :as cs]
            [ib-re-actor.wrapper :as wrapper :refer [error-end? request-end?]]))

;; Default port for live trading
(defonce default-port 7496)
;; Default port for paper trading (not real money)
(defonce default-paper-port 7497)

(defonce default-server-log-level :error)


(defn next-order-updater
  "Returns a function that will take the value from :next-valid-order-id
  messages and update the next-order-id atom accordingly."
  [next-order-id]
  (fn [{:keys [type value]}]
    (when (= :next-valid-order-id type)
      (log/info "Next order ID:" value)
      (reset! next-order-id value))))


(defn next-id
  "Use this utility function to get the next valid id.

  type should be one of :order :ticker :request
  "
  [type connection]
  (let [id @(get-in connection [:next-id type])]
    (swap! (get-in connection [:next-id type]) inc)
    id))


(defn subscribe!
  "Adds f to the functions that will get called with every message that is
  received from the server. Subscribing the same function more than once has no
  effect.

  If a type and id are passed, keep track of the link between the
  request/order/ticker id and the subscriber so that it can be removed if the
  request is canceled.

  Returns the connection.

  Note: Subscribers are called sequentially and are not expected to take a lot
  of time to execute. Processing should be sent to a thread to avoid slowing
  down the system.
  "
  ([connection f]
   (swap! (:subscribers connection) conj f)
   connection)
  ([connection type id f]
   (swap! (:subscribers-data connection) assoc [type id] f)
   (subscribe! connection f)))


(defn- get-subscriber
  "Get the handler that was saved for that request id."
  [connection type id]
  (get @(:subscribers-data connection) [type id]))


(defn- del-subscriber!
  "Removes the subscriber from the :subscribers-data."
  [connection f]
  (swap! (:subscribers-data connection)
         #(into {} (filter (comp (partial not= f) second) %))))


(defn unsubscribe!
  "Removes f from the list of functions that will get called with every message
  from the server.

  If f is also present in :subscriber-data it will be removed."
  ([connection f]
   (swap! (:subscribers connection) disj f)
   (del-subscriber! connection f)
   connection)
  ([connection type id]
   (unsubscribe! connection
                 (get-subscriber connection type id))))


(defn- message-dispatcher
  "Returns a closure responsible for calling every subscriber with messages
  passed to it."
  [subscribers]
  (fn [message]
    (doseq [f @subscribers]
      (try
        (f message)
        (catch Throwable t
          (log/error t "Error dispatching" message "to" f))))))


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
   (let [subscribers (atom #{})
         subscribers-data (atom nil)
         next-order-id (atom 1)
         next-request-id (atom 1)
         next-ticker-id (atom 1)]
     (swap! subscribers conj (next-order-updater next-order-id))
     (try
       (let [wr (wrapper/create (message-dispatcher subscribers))
             ecs (cs/connect wr host port client-id)]
         (when-not (= :error default-server-log-level)
           (cs/set-server-log-level ecs default-server-log-level))
         {:ecs ecs
          :subscribers subscribers
          :subscribers-data subscribers-data
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


(defonce default-handlers
  {:data #(log/info "Received:" %)
   :end #(log/info "End of the request.")
   :error #(log/error "Error: " %)})


(defonce nil-handlers
  {})


(defn single-message-handler
  "Handler to wait for a single message, call the appropriate handlers and
  unsubscribe from the connection when there is nothing else to be done."
  [connection message-type req-id {:keys [data end error]}]
  (fn this [{:keys [type id value] :as msg}]
    (cond
      (and (= type message-type)
           (or (nil? id)
               (= req-id id))) (do (and data (data value))
                                   (and end (end))
                                   (unsubscribe! connection this))

      (error-end? msg) (do (and error (error msg))
                           (and end (end))
                           (unsubscribe! connection this)))))


(defn multiple-messages-handler
  "Handler to wait for multiple messages and eventually the appropriate end
  message. It will call the appropriate handlers at the time data is received
  and unsubscribe from the connection when there is nothing else to be done."
  [connection message-type id {:keys [data end error]}]
  (fn this [{:keys [type value] :as msg}]
    (cond
      (= type message-type) (and data (data value))

      (request-end? message-type id msg) (do (and end (end))
                                             (unsubscribe! connection this))

      (error-end? msg) (do (and error (error msg))
                           (and end (end))
                           (unsubscribe! connection this)))))


(defn request-current-time [connection handlers]
  (subscribe! connection
              (single-message-handler connection :current-time nil handlers))
  (cs/request-current-time (:ecs connection)))


(defn request-market-data
  "Returns the ticker-id that can be used to cancel the request."
  ([connection contract handlers]
   (request-market-data connection contract "" false handlers))
  ([connection contract tick-list snapshot? handlers]
   (let [ticker-id (next-id :ticker connection)]
     (subscribe! connection :ticker ticker-id
                 (multiple-messages-handler connection :tick ticker-id handlers))
     (cs/request-market-data (:ecs connection)
                             ticker-id contract tick-list snapshot?)
     ticker-id)))


(defn cancel-market-data [connection ticker-id]
  (cs/cancel-market-data (:ecs connection) ticker-id)
  (unsubscribe! connection :ticker ticker-id))


(defn calculate-implied-vol
  "Returns the ticker-id that can be used to cancel the request."
  [connection contract option-price underlying-price handlers]
  (let [ticker-id (next-id :ticker connection)]
    (subscribe! connection :ticker ticker-id
                (single-message-handler connection :tick ticker-id handlers))
    (cs/calculate-implied-volatility (:ecs connection)
                                     ticker-id contract
                                     option-price underlying-price)
    ticker-id))


(defn cancel-calculate-implied-vol
  [connection ticker-id]
  (cs/cancel-calculate-implied-volatility (:ecs connection) ticker-id)
  (unsubscribe! connection :ticker ticker-id))


(defn calculate-option-price
  "Returns the ticker-id that can be used to cancel the request."
  [connection contract volatility underlying-price handlers]
  (let [ticker-id (next-id :ticker connection)]
    (subscribe! connection :ticker ticker-id
                (single-message-handler connection :tick ticker-id handlers))
    (cs/calculate-option-price (:ecs connection)
                               ticker-id contract
                               volatility underlying-price)
    ticker-id))


(defn cancel-calculate-option-price
  [connection ticker-id]
  (cs/cancel-calculate-option-price (:ecs connection) ticker-id)
  (unsubscribe! connection :ticker ticker-id))



(defn order-monitor-handler
  "Listen for messages about the order and forwards them to the :data handler
  while waiting for the order to be filled or canceled and call the end
  handler."
  [connection ord-id {:keys [data end error]}]
  (fn this [{:keys [type id order-id value] :as msg}]
    (let [order-status (= type :order-status)
          done (#{:filled :cancelled} (:status value))
          this-order (= order-id ord-id)]
      (cond
        (and order-status
             this-order) (do (and data (data value))
                             (when done
                               (and end (end))
                               (unsubscribe! connection this)))

        (and (= id order-id)
             (error-end? msg)) (do (and error (error msg))
                                   (and end (end))
                                   (unsubscribe! connection this))))))


(defn place-and-monitor-order
  "Returns the order-id that can be used to cancel or modify the order."
  [connection contract order handlers]
  (let [order-id (next-id :order connection)]
    (subscribe! connection :order order-id
                (order-monitor-handler connection order-id handlers))
    (cs/place-order (:ecs connection)
                    order-id contract (assoc order :order-id order-id))
    order-id))


(defn modify-order [connection order-id contract order]
  (cs/place-order (:ecs connection)
                  order-id contract (assoc order :order-id order-id))
  order-id)


(defn cancel-order [connection order-id]
  (cs/cancel-order (:ecs connection) order-id)
  (unsubscribe! connection :order order-id))


(defn request-open-orders [connection handlers]
  (subscribe! connection
              (multiple-messages-handler connection
                                         :open-order nil handlers))
  (cs/request-open-orders (:ecs connection))
  connection)


(defn cancel-all-orders
  "This will cancel all orders including the ones entered in TWS."
  [connection]
  (cs/request-global-cancel (:ecs connection))
  connection)


(defn request-contract-details [connection contract handlers]
  (let [request-id (next-id :request connection)]
    (subscribe! connection :request request-id
                (multiple-messages-handler connection
                                           :contract-details request-id handlers))
    (cs/request-contract-details (:ecs connection) request-id contract)
    request-id))


(defn request-historical-data
  ([connection contract end
    duration duration-unit bar-size bar-size-unit
    what-to-show use-regular-trading-hours? handlers]
   (let [request-id (next-id :request connection)]
     (subscribe! connection :request request-id
                 (multiple-messages-handler connection :price-bar request-id handlers))
     (cs/request-historical-data (:ecs connection) request-id contract
                                 end duration duration-unit bar-size bar-size-unit
                                 what-to-show use-regular-trading-hours?)
     request-id))
  ([connection contract end
    duration duration-unit bar-size bar-size-unit
    what-to-show handlers]
   (request-historical-data connection contract end duration duration-unit
                            bar-size bar-size-unit what-to-show true handlers))
  ([connection contract end
    duration duration-unit bar-size bar-size-unit handlers]
   (request-historical-data connection contract end duration duration-unit
                            bar-size bar-size-unit :trades true handlers)))


(defn request-fundamental-data
  [connection contract report-type handlers]
  (let [request-id (next-id :request connection)]
    (subscribe! connection :request request-id
                (multiple-messages-handler connection :fundamental-data request-id
                                           handlers))
    (cs/request-fundamental-data (:ecs connection) request-id contract report-type)
    request-id))


(defn cancel-fundamental-data
  [connection request-id]
  (cs/cancel-fundamental-data (:ecs connection) request-id)
  (unsubscribe! connection :request request-id))
