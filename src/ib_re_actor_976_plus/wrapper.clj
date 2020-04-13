(ns ib-re-actor-976-plus.wrapper
  (:require
    [clojure.tools.logging :as log]
    [clojure.xml :as xml]
    [ib-re-actor-976-plus.mapping :refer [->map]]
    [ib-re-actor-976-plus.translation :refer [boolean-account-value? integer-account-value? numeric-account-value? translate]])
  (:import (com.ib.client Bar EWrapper TickAttrib Contract ContractDetails)
           (java.io ByteArrayInputStream)))


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


(defn matching-message? [handle-type id
                         {:keys [type request-id order-id ticker-id] :as message}]
  (and (= handle-type type)
       (or (nil? id)
           (= id (or request-id order-id ticker-id)))))


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
  ([req-id {:keys [type code id] :as msg}]
   (and (error? msg)
        (= req-id id))))


(def end-message-type {:tick :tick-snapshot-end
                       :open-order :open-order-end
                       :update-account-value :account-download-end
                       :update-portfolio :account-download-end
                       :account-summary :account-summary-end
                       :position :position-end
                       :contract-details :contract-details-end
                       :execution-details :execution-details-end
                       :price-bar :price-bar-complete
                       :scan-result :scan-end})


(defn request-end?
  "Predicate to determine if a message indicates a series of responses for a
  request is done.

  message-type is the type of the data coming in. For example: :price-bar
  or :open-order."
  [message-type req-id
   {:keys [type request-id order-id ticker-id] :as msg}]
  (and (= type (end-message-type message-type))
       (or (nil? req-id)
           (= req-id (or request-id order-id ticker-id)))))


(defn- dispatch-message [cb msg]
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
    EWrapper

    ;;; Connection and Server
    (currentTime [this time]
      (dispatch-message cb {:type :current-time  :time time}))

    (^void error [this ^int id ^int errorCode ^String message]
      (dispatch-message cb {:type :error :id id :code errorCode :message message}))

    (^void error [this ^Exception ex]
      (dispatch-message cb {:type :error :exception (.toString ex)}))

    (^void error [this ^String message]
      (dispatch-message cb {:type :error :message message}))

    (connectionClosed [this]
      (dispatch-message cb {:type :connection-closed}))

    (connectAck [this]
      (dispatch-message cb {:type :connection-acknowledgement}))

    ;;;; Market Data
    ;(^void tickPrice [this ^int tickerId ^int field ^double price ^TickAttrib attribs]
    ;  (dispatch-message cb {:type :tick-price :ticker-id tickerId;AMENDED BY AA 28/11/17
    ;                        :value {:field (translate :from-ib :tick-field-code field)
    ;                                :value price
    ;                                :attribs attribs        ;we could expand but what's the point
    ;                                }}))

    ;;; Market Data
    ;(translate :from-ib :tick-field-code field)
    (tickPrice [this tickerId field price attribs]
      (dispatch-message cb {:type :tick-price :ticker-id tickerId :field field :price price :attribs attribs}))

    (tickSize [this tickerId field size]
      (dispatch-message cb {:type :tick-size :ticker-id tickerId :field field :size size}))

    (tickOptionComputation [this tickerId field impliedVolatility delta optPrice pvDividend gamma vega theta undPrice]
      (dispatch-message cb {:type :tick-option-computation :ticker-id tickerId :field field
                            :implied-volatility impliedVolatility :option-price optPrice :pv-dividend pvDividend
                            :underlying-price undPrice :delta delta :gamma gamma :theta theta :vega vega}))
    ;:value {:field (translate :from-ib :tick-field-code field)
    ;        :value {:implied-volatility impliedVol
    ;                :option-price optPrice
    ;                :pv-dividends pvDividend
    ;                :underlying-price undPrice
    ;                :delta delta :gamma gamma
    ;                :theta theta :vega vega}}

    (tickGeneric [this tickerId tickType value]
      (dispatch-message cb {:type :tick-generic :ticker-id tickerId :tick-type tickType :value value}))
    ;:value {:field tickType         ;(translate :from-ib :tick-field-code tickType)
    ;        :value value}

    (tickString [this tickerId field value]
      (dispatch-message cb {:type :tick-string :ticker-id tickerId :field field :value value}))
    ;:value {:field tickType       ;field
    ;        :value (case tickType ;field
    ;                 :last-timestamp
    ;                 (translate :from-ib :date-time value)
    ;
    ;                 value)}})))

    (tickReqParams [this tickerId minTick bboExchange snapshotPermissions]
      (dispatch-message cb {:type :tick-req-params :ticker-id tickerId :min-tick minTick :bbo-exchange bboExchange :snapshot-permissions snapshotPermissions}))

    (tickEFP [this tickerId tickType basisPoints formattedBasisPoints impliedFuture holdDays futureLastTradeDate dividendImpact dividendsToLastTradeDate]
      (dispatch-message cb {:type :tick :ticker-id tickerId
                            ;:value {:field (translate :from-ib :tick-field-code tickType)
                            ;        :basis-points basisPoints
                            ;        :formatted-basis-points formattedBasisPoints
                            ;        :implied-future impliedFuture :hold-days holdDays
                            ;        :future-expiry futureExpiry
                            ;        :dividend-impact dividendImpact
                            ;        :dividends-to-expiry dividendsToExpiry}
                            :tick-type tickType
                            :basis-points basisPoints
                            :formatted-basis-points formattedBasisPoints
                            :implied-future impliedFuture :hold-days holdDays
                            :future-last-trade-date futureLastTradeDate
                            :dividend-impact dividendImpact
                            :dividends-to-last-trade-date dividendsToLastTradeDate
                            }))

    (tickSnapshotEnd [this reqId]
      (dispatch-message cb {:type :tick-snapshot-end :request-id reqId}))

    (marketDataType [this reqId marketDataType]
      (dispatch-message cb {:type :market-data-type :request-id reqId :market-data-type marketDataType}))
    ;(translate :from-ib :market-data-type type)

    (marketRule [this marketRuleId priceIncrements]
      (dispatch-message cb {:type :market-rule :market-rule-id marketRuleId :price-increments priceIncrements}))

    (mktDepthExchanges [this depthMktDataDescriptions]
      (dispatch-message cb {:type :mkt-depth-exchanges :depthMktDataDescriptions depthMktDataDescriptions}))

    ;;;; Orders
    ;(orderStatus [this orderId status filled remaining avgFillPrice permId
    ;              parentId lastFillPrice clientId whyHeld]
    ;  (dispatch-message cb {:type :order-status :order-id orderId
    ;                        :value {:status (translate :from-ib :order-status status)
    ;                                :filled filled :remaining remaining
    ;                                :average-fill-price avgFillPrice
    ;                                :permanent-id permId :parent-id parentId
    ;                                :last-fill-price lastFillPrice :client-id clientId
    ;                                :why-held whyHeld}}))

    ;;; Orders
    (orderStatus [this orderId status filled remaining avgFillPrice permId parentId lastFillPrice clientId whyHeld mktCapPrice]
      (dispatch-message cb {:type :order-status :order-id orderId :status status :filled filled
                            :remaining remaining :average-fill-price avgFillPrice :permanent-id permId :parent-id parentId
                            :last-fill-price lastFillPrice :client-id clientId :why-held whyHeld :capped-price mktCapPrice}))
    ;:value {:status (translate :from-ib :order-status status)
    ;        :filled filled :remaining remaining
    ;        :average-fill-price avgFillPrice
    ;        :permanent-id permId :parent-id parentId
    ;        :last-fill-price lastFillPrice :client-id clientId
    ;        :why-held whyHeld
    ;        :capped-price mktCapPrice}


    (openOrder [this orderId contract order orderState]
      (dispatch-message cb {:type :open-order :order-id orderId :contract contract :order order :order-state orderState}))
    ;:value {:contract (->map contract)
    ;        :order (->map order)
    ;        :order-state (->map orderState)}
    ;

    (openOrderEnd [this]
      (dispatch-message cb {:type :open-order-end}))

    (orderBound [this orderId apiClientId apiOrderId]
      (dispatch-message cb {:type :order-bound :order-id orderId :api-client-id apiClientId :api-order-id apiOrderId}))

    (nextValidId [this orderId]
      (dispatch-message cb {:type :next-valid-order-id :order-id orderId}))

    (completedOrder [this contract order orderState]
      (dispatch-message cb {:type :completed-order :contract contract :order order :order-state orderState}))

    (completedOrdersEnd [this]
      (dispatch-message cb {:type :completed-orders-end}))

    (deltaNeutralValidation [this reqId deltaNeutralContract]
      (dispatch-message cb {:type :delta-neutral-validation :request-id reqId :delta-neutral-contract deltaNeutralContract}))
    ;;;TODO: Should we return the underComp directly here?
    ;:value {:underlying-component (->map underComp)}
    ;

    ;;; Account and Portfolio
    (updateAccountValue [this key value currency accountName]
      (dispatch-message cb {:type :update-account-value :key key :value value :currency currency :account accountName}))

    (updatePortfolio [this contract position marketPrice marketValue averageCost unrealizedPNL realizedPNL accountName]
      (dispatch-message cb {:type :update-portfolio
                            :contract contract      ;(->map contract)
                            :position position :market-price marketPrice
                            :market-value marketValue :average-cost averageCost
                            :unrealized-gain-loss unrealizedPNL
                            :realized-gain-loss realizedPNL
                            :account accountName}))

    (updateAccountTime [this timeStamp] ; AMENDED BY AA 26/11/17
      (dispatch-message cb {:type :update-account-time :timestamp timeStamp})) ; (translate :from-ib :time-of-day timeStamp)}))

    (accountDownloadEnd [this account-code]
      (dispatch-message cb {:type :account-download-end :account-code account-code}))

    (accountSummary [this reqId account tag value currency]
      (dispatch-message cb {:type :account-summary :request-id reqId :account account :tag tag :value value :currency currency}))

    (accountSummaryEnd [this reqId]
      (dispatch-message cb {:type :account-summary-end :request-id reqId}))

    (accountUpdateMulti [this reqId account modelCode key value currency]
      (dispatch-message cb {:type :account-update-multi :request-id reqId :account account :model-code modelCode :key key :value value :currency currency}))

    (accountUpdateMultiEnd [this reqId]
      (dispatch-message cb {:type :account-update-multi-end :request-id reqId}))

    (position [this account contract pos avgCost]
      (dispatch-message cb {:type :position :account account :contract contract :position pos :average-cost avgCost}))
    ;:value {:account account
    ;        :contract (->map contract)
    ;        :position pos
    ;        :average-cost avgCost}

    (positionEnd [this]
      (dispatch-message cb {:type :position-end}))

    (positionMulti [this requestId account modelCode contract pos avgCost]
      (dispatch-message cb {:type :position-multi :request-id requestId :model-code modelCode :account account :contract contract :position pos :average-cost avgCost}))

    (positionMultiEnd [this requestId]
      (dispatch-message cb {:type :position-multi-end :request-id requestId}))

    (pnl [this reqId dailyPnL unrealizedPnL realizedPnL]
      (dispatch-message cb {:type :pnl :request-id reqId :daily-pnl dailyPnL :unrealized-pnl unrealizedPnL :realized-pnl realizedPnL}))

    (pnlSingle [this reqId pos dailyPnL unrealizedPnL realizedPnL value]
      (dispatch-message cb {:type :pnl :request-id reqId :pos pos :daily-pnl dailyPnL :unrealized-pnl unrealizedPnL :realized-pnl realizedPnL :value value}))


    ;;; Contract Details
    ; (contractDetailsOld [this requestId contractDetails]
    ;   (let [{:keys [trading-hours liquid-hours time-zone-id] :as m} (->map contractDetails)
    ;         th (translate :from-ib :trading-hours [time-zone-id trading-hours])
    ;         lh (translate :from-ib :trading-hours [time-zone-id liquid-hours])]
    ;     (dispatch-message cb {:type :contract-details :request-id requestId
    ;                           :value (merge m
    ;                                         (when th {:trading-hours th})
    ;                                         (when lh {:liquid-hours lh}))})))

    (contractDetails [this requestId contractDetails]
      (dispatch-message cb {:type :contract-details :request-id requestId :contract-details contractDetails}))                           ;:ib-contract (.contract ^ContractDetails contractDetails)

    (contractDetailsEnd [this requestId]
      (dispatch-message cb {:type :contract-details-end :request-id requestId}))

    (bondContractDetails [this requestId contractDetails]
      (dispatch-message cb {:type :bond-contract-details :request-id requestId :contract-details contractDetails}))          ;(->map contractDetails)

    ;;; Execution Details
    (execDetails [this requestId contract execution]
      (dispatch-message cb {:type :execution-details :request-id requestId :contract contract :execution execution}))
    ;:value {:contract contract      ;(->map contract)
    ;        :value (->map execution)}

    (execDetailsEnd [this requestId]
      (dispatch-message cb {:type :execution-details-end :request-id requestId}))

    (commissionReport [this commissionReport]
      (dispatch-message cb {:type :commission-report :value commissionReport}))         ;(->map commissionReport)

    ;;; Market Depth
    (updateMktDepth [this tickerId position operation side price size]
      (dispatch-message cb {:type :update-market-depth :ticker-id tickerId :position position :operation operation :side side :price price :size size}))
    ;:value {:position position
    ;        :operation (translate :from-ib
    ;                              :market-depth-row-operation
    ;                              operation)
    ;        :side (translate :from-ib :market-depth-side side)
    ;        :price price :size size}



    (updateMktDepthL2 [this tickerId position marketMaker operation side price size isSmartDepth]
      (dispatch-message cb {:type :update-market-depth-level-2 :ticker-id tickerId :position position :market-maker marketMaker :operation operation :side side :price price :size size :is-smart-depth isSmartDepth}))

    ;;; News Bulletin
    (updateNewsBulletin [this msgId msgType message origExchange]
      (dispatch-message cb {:type :news-bulletin  :msg-id msgId  :msg-type msgType :message message :orig-exchange origExchange}))
    ;:value {:type (condp = msgType
    ;                0 :news-bulletin
    ;                1 :exchange-unavailable
    ;                2 :exchange-available)


    (newsArticle [this requestId articleType articleText]
      (dispatch-message cb {:type :news-article :request-id requestId :article-type articleType :article-text articleText}))

    (newsProviders [this newsProviders]
      (dispatch-message cb {:type :news-providers :news-providers newsProviders}))

    (tickNews [this tickerId timeStamp providerCode articleId headline extraData]
      (dispatch-message cb {:type :tick-news :ticker-id tickerId :timestamp timeStamp :provider-code providerCode :article-id articleId :headline headline :extra-data extraData}))

    ;;; Financial Advisors

    (familyCodes [this familyCodes]
      (dispatch-message cb {:type :family-codes :family-codes familyCodes}))

    (managedAccounts [this accountsList]
      (dispatch-message cb {:type :managed-accounts :accounts-list accountsList}))                            ;:value (->> (.split accountsList ",") (map #(.trim %)) vec)

    (receiveFA [this faDataType xml]
      (dispatch-message cb {:type :receive-fa :fa-data-type faDataType :value xml})) ;(translate :from-ib :financial-advisor-data-type faDataType)

    (headTimestamp [this reqId headTimestamp]
      (dispatch-message cb {:type :head-timestamp :head-timestamp headTimestamp}))

    (histogramData [this reqId data]
      (dispatch-message cb {:type :histogram-data :request-id reqId :data data}))

    (^void historicalData [this ^int requestId ^Bar bar]
      (dispatch-message cb {:type :price-bar :request-id requestId
                            :date (.time bar) :open (.open bar) :close (.close bar) :high (.high bar) :low (.low bar) :volume (.volume bar) :trade-count (.count bar) :WAP (.wap bar)}))

    (historicalDataEnd [this requestId start end]
      (dispatch-message cb {:type :historical-data-end :request-id requestId :start start :end end}))

    (^void historicalDataUpdate [this ^int requestId ^Bar bar]
      (dispatch-message cb {:type :price-bar-update :request-id requestId
                            :date (.time bar) :open (.open bar) :close (.close bar) :high (.high bar) :low (.low bar) :volume (.volume bar) :trade-count (.count bar) :WAP (.wap bar)}))

    (historicalNews [this requestId time providerCode articleId headline]
      (dispatch-message cb {:type :historical-news :request-id requestId :time time :provider-code providerCode :article-id articleId :headline headline}))

    (historicalNewsEnd [this requestId hasMore]
      (dispatch-message cb {:type :historical-news-end :request-id requestId :has-more hasMore}))

    (historicalTicks [this reqId ticks done]
      (dispatch-message cb {:type :historical-ticks :request-id reqId :ticks ticks :done done}))

    (historicalTicksBidAsk [this reqId ticks done]
      (dispatch-message cb {:type :historical-ticks-bid-ask :request-id reqId :ticks ticks :done done}))

    (historicalTicksLast [this reqId ticks done]
      (dispatch-message cb {:type :historical-ticks-last :request-id reqId :ticks ticks :done done}))

    (rerouteMktDataReq [this reqId conId exchange]
      (dispatch-message cb {:type :reroute-mkt-data-req :request-id reqId :conid conId :exchange exchange}))

    (rerouteMktDepthReq [this reqId conId exchange]
      (dispatch-message cb {:type :reroute-mkt-depth-req :request-id reqId :conid conId :exchange exchange}))

    ;;; Market Scanners
    (scannerParameters [this xml]
      (dispatch-message cb {:type :scanner-parameters :value xml}))

    (scannerData [this requestId rank contractDetails distance benchmark projection legsStr]
      (dispatch-message cb {:type :scanner-data :request-id requestId :rank rank :contract-details contractDetails :distance distance :benchmark benchmark :projection projection :legs legsStr}))

    (scannerDataEnd [this requestId]
      (dispatch-message cb {:type :scanner-data-end :request-id requestId}))

    (securityDefinitionOptionalParameter [this reqId exchange underlyingConId tradingClass multiplier expirations strikes]
      (dispatch-message cb {:type :security-definition-optional-parameter :request-id reqId :exchange exchange :underlying-conid underlyingConId :trading-class tradingClass :multiplier multiplier :expirations expirations :strikes strikes}))

    (securityDefinitionOptionalParameterEnd [this reqId]
      (dispatch-message cb {:type :security-definition-optional-parameter-end :request-id reqId}))

    (smartComponents [this reqId theMap]
      (dispatch-message cb {:type :smart-components :request-id reqId :the-map theMap}))

    (softDollarTiers [this reqId tiers]
      (dispatch-message cb {:type :soft-dollar-tiers :request-id reqId :tiers tiers}))

    (symbolSamples [this reqId contractDescriptions]
      (dispatch-message cb {:type :symbol-samples :request-id reqId :contract-descriptions contractDescriptions}))

    (tickByTickAllLast [this reqId tickType time price size tickAttribLast exchange specialConditions]
      (dispatch-message cb {:type :tick-by-tick-all-last :request-id reqId :tick-type tickType :time time :price price :size size :tick-attrib-last tickAttribLast :exchange exchange :special-conditions specialConditions}))

    (tickByTickBidAsk [this reqId time bidPrice askPrice bidSize askSize tickAttribBidAsk]
      (dispatch-message cb {:type :tick-by-tick-bid-ask :request-id reqId :time time :bid-price bidPrice :ask-price askPrice :bid-size bidSize :ask-size askSize :tick-attrib-bid-ask tickAttribBidAsk}))

    (tickByTickMidPoint [this reqId time midPoint]
      (dispatch-message cb {:type :tick-by-tick-mid-point :request-id reqId :time time :mid-point midPoint}))

    ;;; Real Time Bars
    (realtimeBar [this requestId date open high low close volume wap count]
      (dispatch-message cb {:type :price-bar :request-id requestId
                            :date date :open open :close close :high high :low low :volume volume :wap wap :count count}))

    ;;; Fundamental Data
    (fundamentalData [this requestId xml]
      (let [report-xml (xml/parse (ByteArrayInputStream. (.getBytes xml)))]
        (dispatch-message cb {:type :fundamental-data :request-id requestId :value report-xml})))

    ;;; Display Groups
    (displayGroupList [this reqId groups]
      (dispatch-message cb {:type :display-group-list :request-id reqId :groups groups}))
    ;:value (->> (.split groups "|") (map #(.trim %)) vec)

    (displayGroupUpdated [this reqId contractInfo]
      (dispatch-message cb {:type :display-group-updated :request-id reqId :contract-info contractInfo}))))
;:value {:contract-info contractInfo}
