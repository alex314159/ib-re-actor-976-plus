(ns ib-re-actor.wrapper
  (:require [clojure.tools.logging :as log]
            [ib-re-actor.mapping :refer [->map]]
            [clojure.xml :as xml]
            [ib-re-actor.translation
             :refer [translate integer-account-value? numeric-account-value?
                     boolean-account-value?]]))


(defn- get-stack-trace [ex]
  (let [sw (java.io.StringWriter.)
        pw (java.io.PrintWriter. sw)]
    (.printStackTrace ex pw)
    (.toString sw)))


(defn- log-exception
  ([ex msg]
   (log/error msg ": " (.getMessage ex))
   (log/error "Stack Trace: " (get-stack-trace ex)))
  ([ex]
   (log-exception ex "Error")))


(defn- is-finish? [date-string]
  (.startsWith date-string "finished"))


(defn warning-code?
  "One would think that warnings start at 2100 but there are error codes
  starting at 10000."
  [code]
  (<= 2100 code 2200))


(defn error-code? [code]
  (complement warning-code?))


(defn connection-error-code? [code]
  (#{504                                ; Not connected
     1100                               ; Connectivity between IB and TWS has been lost
     } code))


(defn warning?
  "A message is a warning if it has :type :error and has a code that is a
  warning code.

  IB also sends warnings with error codes but with a message containing
  \"Warning:\". For example, when you submit an order outside the trading hours
  you get an error code 399 but the message indicates that it is only a warning.
  "
  [{:keys [type code message] :as msg}]
  (and (= :error type)
       (or (and code (warning-code? code))
           (and message (re-seq #"Warning:" message)))))


(defn error? [{:keys [type] :as message}]
  (and (= :error type)
       (not (warning? message))))


(defn error-end?
  "Determines if a message is an error message that ends a request.

  id is the request, order or ticker id for the request."
  ([msg]
   (error-end? nil msg))
  ([id {:keys [type code request-id order-id ticker-id] :as msg}]
   (and (error? msg)
        (or (connection-error-code? code)
            (= id (or request-id order-id ticker-id))))))

(def end-message-type? #{:tick-snapshot-end
                         :open-order-end
                         :account-download-end
                         :account-summary-end
                         :position-end
                         :contract-details-end
                         :execution-details-end
                         :price-bar-complete
                         :scan-end})

(defn request-end?
  "Predicate to determine if a message indicates a series of responses for a
  request is done"
  ;;TODO: This is too general as if two simultaneous requests that do not
  ;;receive an end message that has no request id they will clash
  [req-id {:keys [type request-id order-id ticker-id] :as msg}]
  (and (end-message-type? type)
       (or (nil? req-id)
           (= req-id (or request-id order-id ticker-id)))))


(defn end?
  "Predicate to determine if a message is either an error-end? or a request-end?."
  ([msg]
   (end? nil msg))
  ([id msg]
   (or (error-end? id msg)
       (request-end? id msg))))


(defn- dispatch-message [cb msg]
  (log/debug "Dispatching: " msg)
  (cb msg))


(defn create
  "Creates a wrapper that flattens the Interactive Brokers EWrapper interface,
   calling a single function (cb) with maps that all have a :type to indicate
  what type of messages was received, and the massaged parameters from the
  event.

   See: https://www.interactivebrokers.com/en/software/api/api.htm
  "
  [cb]
  (reify
    com.ib.client.EWrapper

    ;;; Connection and Server
    (currentTime [this time]
      (dispatch-message cb {:type :current-time
                            :value (translate :from-ib :date-time time)}))

    (^void error [this ^int id ^int errorCode ^String message]
     (dispatch-message cb {:type :error :id id :code errorCode
                           :message message}))

    (^void error [this ^Exception ex]
     (log-exception ex)
     (dispatch-message cb {:type :error :exception (.toString ex)}))

    (^void error [this ^String message]
     (dispatch-message cb {:type :error :message message}))

    (connectionClosed [this]
      (log/info "Connection closed")
      (dispatch-message cb {:type :connection-closed}))

    ;;; Market Data
    (tickPrice [this tickerId field price canAutoExecute]
      (dispatch-message cb {:type :tick :ticker-id tickerId
                            :value {:field (translate :from-ib :tick-field-code field)
                                    :value price
                                    :can-auto-execute? (= 1 canAutoExecute)}}))

    (tickSize [this tickerId field size]
      (dispatch-message cb {:type :tick :ticker-id tickerId
                            :value {:field (translate :from-ib :tick-field-code field)
                                    :value size}}))

    (tickOptionComputation [this tickerId field impliedVol delta optPrice
                            pvDividend gamma vega theta undPrice]
      (dispatch-message cb {:type :tick :ticker-id tickerId
                            :value {:field (translate :from-ib :tick-field-code field)
                                    :implied-volatility impliedVol
                                    :option-price optPrice
                                    :pv-dividends pvDividend
                                    :underlying-price undPrice
                                    :delta delta :gamma gamma :theta theta :vega vega}}))

    (tickGeneric [this tickerId tickType value]
      (dispatch-message cb {:type :tick :ticker-id tickerId
                            :value {:field (translate :from-ib :tick-field-code tickType)
                                    :value value}}))

    (tickString [this tickerId tickType value]
      (let [field (translate :from-ib :tick-field-code tickType)]
        (dispatch-message cb {:type :tick :ticker-id tickerId
                              :value {:field field
                                      :value (case field
                                               :last-timestamp
                                               (translate :from-ib :date-time value)

                                               value)}})))

    (tickEFP [this tickerId tickType basisPoints formattedBasisPoints
              impliedFuture holdDays futureExpiry dividendImpact dividendsToExpiry]
      (dispatch-message cb {:type :tick :ticker-id tickerId
                            :value {:field (translate :from-ib :tick-field-code tickType)
                                    :basis-points basisPoints
                                    :formatted-basis-points formattedBasisPoints
                                    :implied-future impliedFuture :hold-days holdDays
                                    :future-expiry futureExpiry
                                    :dividend-impact dividendImpact
                                    :dividends-to-expiry dividendsToExpiry}}))

    (tickSnapshotEnd [this reqId]
      (dispatch-message cb {:type :tick-snapshot-end :request-id reqId}))

    (marketDataType [this reqId type]
      (dispatch-message cb {:type :market-data-type :request-id reqId
                            :value (translate :from-ib :market-data-type type)}))

    ;;; Orders
    (orderStatus [this orderId status filled remaining avgFillPrice permId
                  parentId lastFillPrice clientId whyHeld]
      (dispatch-message cb {:type :order-status :order-id orderId
                            :value {:status (translate :from-ib :order-status status)
                                    :filled filled :remaining remaining
                                    :average-fill-price avgFillPrice
                                    :permanent-id permId :parent-id parentId
                                    :last-fill-price lastFillPrice :client-id clientId
                                    :why-held whyHeld}}))

    (openOrder [this orderId contract order orderState]
      (dispatch-message cb {:type :open-order :order-id orderId
                            :value {:contract (->map contract)
                                    :order (->map order)
                                    :order-state (->map orderState)}}))

    (openOrderEnd [this]
      (dispatch-message cb {:type :open-order-end}))

    (nextValidId [this orderId]
      (dispatch-message cb {:type :next-valid-order-id
                            :value orderId}))

    ;; In newer docs
    (deltaNeutralValidation [this reqId underComp]
      (dispatch-message cb {:type :delta-neutral-validation :request-id reqId
                            ;;TODO: Should we return the underComp directly here?
                            :value {:underlying-component (->map underComp)}}))

    ;;; Account and Portfolio
    (updateAccountValue [this key value currency accountName]
      (let [avk (translate :from-ib :account-value-key key)
            val (cond
                  (integer-account-value? avk) (Integer/parseInt value)
                  (numeric-account-value? avk) (Double/parseDouble value)
                  (boolean-account-value? avk) (Boolean/parseBoolean value)
                  :else value)]
        (dispatch-message cb {:type :update-account-value
                              :value {:key avk :value val
                                      :currency currency :account accountName}})))

    (updatePortfolio [this contract position marketPrice marketValue averageCost
                      unrealizedPNL realizedPNL accountName]
      (dispatch-message cb {:type :update-portfolio
                            :value {:contract (->map contract)
                                    :position position :market-price marketPrice
                                    :market-value marketValue :average-cost averageCost
                                    :unrealized-gain-loss unrealizedPNL
                                    :realized-gain-loss realizedPNL
                                    :account accountName}}))

    (updateAccountTime [this timeStamp]
      (dispatch-message cb {:type :update-account-time
                            :value (translate :from-ib :time-of-day timeStamp)}))

    (accountDownloadEnd [this account-code]
      (dispatch-message cb {:type :account-download-end :account-code account-code}))

    (accountSummary [this reqId account tag value currency]
      (dispatch-message cb {:type :account-summary :request-id reqId
                            :value {:account account
                                    :tag tag
                                    :value value :currency currency}}))

    (accountSummaryEnd [this reqId]
      (dispatch-message cb {:type :account-summary-end :request-id reqId}))

    (position [this account contract pos avgCost]
      (dispatch-message cb {:type :position
                            :value {:account account
                                    :contract (->map contract)
                                    :position pos
                                    :average-cost avgCost}}))

    (positionEnd [this]
      (dispatch-message cb {:type :position-end}))

    ;;; Contract Details
    (contractDetails [this requestId contractDetails]
      (let [{:keys [trading-hours liquid-hours time-zone-id] :as m} (->map contractDetails)
            th (translate :from-ib :trading-hours [time-zone-id trading-hours])
            lh (translate :from-ib :trading-hours [time-zone-id liquid-hours])]
        (dispatch-message cb {:type :contract-details :request-id requestId
                              :value (merge m
                                            (when th {:trading-hours th})
                                            (when lh {:liquid-hours lh}))})))

    (contractDetailsEnd [this requestId]
      (dispatch-message cb {:type :contract-details-end :request-id requestId}))

    (bondContractDetails [this requestId contractDetails]
      (dispatch-message cb {:type :contract-details :request-id requestId
                            :value (->map contractDetails)}))

    ;;; Execution Details
    (execDetails [this requestId contract execution]
      (dispatch-message cb {:type :execution-details :request-id requestId
                            :value {:contract (->map contract)
                                    :value (->map execution)}}))

    (execDetailsEnd [this requestId]
      (dispatch-message cb {:type :execution-details-end :request-id requestId}))

    (commissionReport [this commissionReport]
      (dispatch-message cb {:type :commission-report
                            :value (->map commissionReport)}))

    ;;; Market Depth
    (updateMktDepth [this tickerId position operation side price size]
      (dispatch-message cb {:type :update-market-depth :ticker-id tickerId
                            :value {:position position
                                    :operation (translate :from-ib
                                                          :market-depth-row-operation
                                                          operation)
                                    :side (translate :from-ib :market-depth-side side)
                                    :price price :size size}}))

    (updateMktDepthL2 [this tickerId position marketMaker operation side price size]
      (dispatch-message cb {:type :update-market-depth-level-2 :ticker-id tickerId
                            :value {:position position
                                    :market-maker marketMaker
                                    :operation (translate :from-ib
                                                          :market-depth-row-operation
                                                          operation)
                                    :side (translate :from-ib :market-depth-side side)
                                    :price price :size size}}))

    ;;; News Bulletin
    (updateNewsBulletin [this msgId msgType message origExchange]
      (dispatch-message cb {:type :news-bulletin
                            :id msgId
                            :value {:type (condp = msgType
                                            0 :news-bulletin
                                            1 :exchange-unavailable
                                            2 :exchange-available)
                                    :message message
                                    :exchange origExchange}}))

    ;;; Financial Advisors
    (managedAccounts [this accountsList]
      (dispatch-message cb {:type :managed-accounts
                            :value (->> (.split accountsList ",") (map #(.trim %)) vec)}))

    (receiveFA [this faDataType xml]
      (dispatch-message cb {:type (translate :from-ib
                                             :financial-advisor-data-type faDataType)
                            :value xml}))

    ;;; Historical Data
    (historicalData [this requestId date open high low close volume count wap hasGaps]
      (if (is-finish? date)
        (dispatch-message cb {:type :price-bar-complete :request-id requestId})
        (dispatch-message cb
                          {:type :price-bar :request-id requestId
                           :value {:time (translate :from-ib :timestamp date)
                                   :open open :close close
                                   :high high :low low  :volume volume
                                   :trade-count count :WAP wap :has-gaps? hasGaps}})))

    ;;; Market Scanners
    (scannerParameters [this xml]
      (dispatch-message cb {:type :scan-parameters :value xml}))

    (scannerData [this requestId rank contractDetails distance benchmark
                  projection legsStr]
      (dispatch-message cb {:type :scan-result :request-id requestId
                            :value {:rank rank
                                    :contract-details (->map contractDetails)
                                    :distance distance :benchmark benchmark
                                    :projection projection :legs legsStr}}))

    (scannerDataEnd [this requestId]
      (dispatch-message cb {:type :scan-end :request-id requestId}))

    ;;; Real Time Bars
    (realtimeBar [this requestId time open high low close volume wap count]
      (dispatch-message cb {:type :price-bar :request-id requestId
                            :value {:time (translate :from-ib :date-time time)
                                    :open open :close close
                                    :high high :low low :volume volume
                                    :count count :WAP wap}}))

    ;;; Fundamental Data
    (fundamentalData [this requestId xml]
      (let [report-xml (xml/parse (java.io.ByteArrayInputStream (.getBytes xml)))]
        (dispatch-message cb {:type :fundamental-data :request-id requestId
                              :value report-xml})))

    ;;; Display Groups
    (displayGroupList [this reqId groups]
      (dispatch-message cb {:type :display-group-list :request-id reqId
                            :value (->> (.split groups "|") (map #(.trim %)) vec)}))

    (displayGroupUpdated [this reqId contractInfo]
      (dispatch-message cb {:type :display-group-updated :request-id reqId
                            :value {:contract-info contractInfo}}))))
