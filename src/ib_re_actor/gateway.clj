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
            [clojure.core.async :refer [chan mult go-loop tap close! <!]]
            [ib-re-actor.client-socket :as cs]
            [ib-re-actor.wrapper :as wrapper]))

(defonce client-id (atom 100))


(defn connect [host port]
  "Returns a connection."
  (let [ch (chan)
        m (mult ch)
        ch-next-order-id (chan)]
    (tap m ch-next-order-id)
    (go-loop [{:keys [type value]} (<! ch-next-order-id)]
      (when (= type :next-valid-order-id)
        (log/info "Next order ID:" value)
        (reset! cs/next-order-id value))
      (recur (<! ch-next-order-id)))
    {:ecs (cs/connect (wrapper/create ch) host port (swap! client-id inc))
     :resp-chan ch
     :mult m}))

(defn disconnect [connection]
  (close! (:resp-chan connection))
  (cs/disconnect connection))
