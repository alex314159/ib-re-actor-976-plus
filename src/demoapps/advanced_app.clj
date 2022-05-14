(ns demoapps.advanced_app
  (:require [ib-re-actor-976-plus.gateway :as gateway]
            [clojure.tools.logging :as log]
            [ib-re-actor-976-plus.translation :as translation]
            [ib-re-actor-976-plus.mapping :refer [map->]]
            [ib-re-actor-976-plus.client-socket :as cs]
            )
  (:import (com.ib.client Contract ContractDetails)
           (java.time ZonedDateTime)))

;;;;;;;;;;;;;;
;ADVANCED APP;
;;;;;;;;;;;;;;

;This will be a more complex listener that also allows you to watch several accounts at the same time.
;You would need several gateways / TWS running at the same time.
;Call-backs that are interesting will be saved in atoms. Rest will be logged, so nothing is lost


;this atom will have all the data we want to keep.
(def IB-state (atom {}))
(def account-state (atom {}))
(def temporary-ib-request-results (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;DEFINING LISTENER BELOW;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti listener (fn [account message] (:type message)))

(defmethod listener :update-account-value [account message]
  (let [{:keys [key value currency]} message
        avk (translation/translate :from-ib :account-value-key key)
        val (cond
              (translation/integer-account-value? avk) (Integer/parseInt value)
              (translation/numeric-account-value? avk) (Double/parseDouble value)
              (translation/boolean-account-value? avk) (Boolean/parseBoolean value)
              :else value)]
    (if currency ;nil punning
      (swap! account-state assoc-in [:ib-account-data account (keyword (str (name avk) "-" currency))] [val currency])
      (swap! account-state assoc-in [:ib-account-data account avk] val))
    ))

(defmethod listener :update-account-time [account message]
  (swap! account-state assoc-in [:ib-account-data account :last-updated] (:time-stamp message)))

(defmethod listener :update-portfolio [account message]
  (let [{:keys [^Contract contract position market-price]} message]
    (swap! account-state assoc-in [:actual-positions account (.conid contract)] {:local-symbol (.localSymbol contract) :position position :price market-price})))

(defmethod listener :account-download-end [account message]
  (log/trace "Account data updated for " account))

(defmethod listener :next-valid-id [account message] (swap! IB-state assoc-in [:order-ids account] (:order-id message))) ;invoked upon connecting

(defmethod listener :tick-price [account message]
  (let [fld (translation/translate :from-ib :tick-field-code (:field message)) price (:price message)]
    (swap! temporary-ib-request-results assoc-in [account (:ticker-id message) fld] {:dt (.toString (ZonedDateTime/now)) :value price})))

(defmethod listener :tick-size [account message] (log/trace message))

(defmethod listener :tick-string [account message] (log/trace message))

(defmethod listener :tick-snapshot-end [account message]
  (let [req-id (:req-id message)]
    (deliver (get-in @IB-state [:req-id-complete account req-id]) true)))

(defmethod listener :order-status [account message]
  "This is messy. If remaining to be filled is 0, then average fill price is traded price.
  Retrieve characteristics of order, and log it depending on what the order is."
  (let [{:keys [order-id filled remaining avg-fill-price]} message]
    (when (and filled (zero? remaining) (nil? (get-in @IB-state [:orders account order-id :traded-price])))
      (let [order (get-in (swap! IB-state assoc-in [:orders account order-id :traded-price] avg-fill-price) [:orders account order-id])]
        (log/trace order)))))

(defmethod listener :open-order [account message]
  (let [{:keys [type order-id contract order order-state]} message]
    (log/trace account type order-id (map-> contract) (map-> order) (map-> order-state))))

(defmethod listener :exec-details [account message]
  (let [{:keys [type req-id contract execution]} message]
    (log/trace account type req-id (map-> contract) (map-> execution))))

(defmethod listener :historical-data [account message]
  (let [{:keys [req-id bar]} message
        {:keys [time open high low close volume]} (map-> bar)]
    (swap! IB-state update-in [:historical-data-requests req-id] conj [(read-string time) open high low close volume])))

(defmethod listener :historical-data-end [account message] (deliver (get-in @IB-state [:req-id-complete account (:req-id message)]) true))

(defmethod listener :contract-details [account message]
  (let [ib-contract (.contract ^ContractDetails (:contract-details message))]
    (swap! temporary-ib-request-results update-in [account (:req-id message)] conj ib-contract)))

(defmethod listener :contract-details-end [account message]
  (let [req-id (:req-id message)]
    (swap! IB-state assoc-in [:req-id-to-ins req-id] nil)
    (deliver (get-in @IB-state [:req-id-complete account req-id]) true)))

(defmethod listener :error [account message]
  (let [id (:id message) code (:code message) msg (:message message)]
    (if
      (or
        (and (= id -1) (= code 2100)) ;API client has been unsubscribed from account data.
        (and (= id -1) (= code 2104)) ;Market data farm connection is OK:usfarm
        (and (= id -1) (= code 2106))) ;HMDS data farm connection is OK:ushmds
      (log/trace  account " [API.msg2] " msg " {" id ", " code "}")
      (log/info   account " [API.msg2] " msg " {" id ", " code "}"))
    (when (or (= code 201) (= code 322))
      ; //Order rejected: do something!
      (log/info   account " [API.msg2] ORDER REJECTTED " msg " {" id ", " code "}")
      )))

(defmethod listener :default [account message] (log/info (name account) message))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;END LISTENER;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


;Define accounts and static data
(def accounts ["U1234567" "U9876543"])
(def portfolio-static
  {:U1234567 {:clientId           1
              :port               7496}
   :U9876543 {:clientId           1
              :port               7490}})

(defn connect-account! [account]
  "could replace localhost by 127.0.0.1"
  (swap! IB-state assoc-in
         [:connections account]
         (gateway/connect
           (get-in portfolio-static [account :clientId]);adding one to clientId at this point
           "localhost"
           (get-in portfolio-static [account :port])
           (fn [message] (listener account message))))
  (Thread/sleep 1000)
  (log/info "Connected account " account))

;Actual connection
(defn connect-all []
  (doseq [account accounts]
    (connect-account! account))
  (log/info "Next valid order-ids" (:order-ids @IB-state))) ;we get this upon connecting (above)

