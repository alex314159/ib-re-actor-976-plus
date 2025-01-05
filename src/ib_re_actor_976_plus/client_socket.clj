(ns ib-re-actor-976-plus.client-socket
  "This namespace is a wrapper of the EClientSocket interface of the
  InteractiveBrokers (IB) API.

  It marshalls data from clojure to what is expected on the IB side.

  Note that the IB API is asynchronous for the most part and that responses will
  be received through the EWrapper. Please refer to the link below to know what
  is expected.

  https://www.interactivebrokers.com/en/software/api/api.htm
  "
  (:require
    [ib-re-actor-976-plus.mapping :refer [map->]]
    [ib-re-actor-976-plus.translation :refer [translate]])
  (:import
    (com.ib.client EClientSocket EReader EJavaSignal OrderCancel)))

;;;
;;; Connection and Server
;;;

(defn -process-messages [client reader signal]
  (while (.isConnected client)
    (.waitForSignal signal)
    (.processMsgs reader)))


(defn connect
  "This function must be called before any other. There is no feedback
   for a successful connection, but a subsequent attempt to connect
   will return the message 'Already connected.'

   wrapper is an implementation of the EWrapper interface.

   host is the hostname running IB Gateway or TWS.

   port is the port IB Gateway / TWS is running on.

   client-id identifies this client. Only one connection to a gateway can
   be made per client-id at a time."
  ([wr signal host port client-id]
   (let [ecs (EClientSocket. wr signal)]
     (.eConnect ecs host port client-id)
     (let [reader (EReader. ecs signal)]
       (.start reader)
       (.start (Thread. ^Runnable (fn [] (-process-messages ecs reader signal)))) ;we're not using a future as somehow they fail silently with
       ;java.lang.AbstractMethodError: Receiver class clibtrader.ib_re_actor.wrapper$create$reify__7992 does not define or inherit an implementation of the resolved method 'abstract void tickReqParams(int, double, java.lang.String, int)' of interface com.ib.client.EWrapper.
       ;seems somewhat linked to https://github.com/clojure-emacs/cider/issues/1404
       ecs))))


(defn disconnect
  "Call this function to terminate the connections with TWS.
   Calling this function does not cancel orders that have already been sent."
  [ecs]
  (.eDisconnect ecs))


(defn is-connected?
  "Call this method to check if there is a connection with TWS."
  [ecs]
  (.isConnected ecs))


(defn set-server-log-level
  "Call this function to set the log level used on the server. The default level
  is :error."
  [ecs log-level]
  (.setServerLogLevel ecs (translate :to-ib :log-level log-level)))


(defn request-current-time
  "Returns the current system time on the server side via the currentTime()
  EWrapper method."
  [ecs]
  (.reqCurrentTime ecs))


(defn server-version
  "Returns the version of the TWS instance to which the API application is
  connected."
  [ecs]
  (.serverVersion ecs))


(defn connection-time
  "Returns the time the API application made a connection to TWS."
  [ecs]
  ;(translate :from-ib :connection-time (.getTwsConnectionTime ecs))
  (.getTwsConnectionTime ecs))


;;;
;;; Market Data
;;;
(defn request-market-data
  "Call this function to request market data. The market data will be returned in
   :tick messages.

   For snapshots, a :tick-snapshot-end message will indicate the snapshot is done.

   ## Parameters
   - this
     The connection to use to make the request. Use (connect) to get this.

   - contract
     This contains attributes used to describe the contract. Use (make-contract) or
     (futures-contract) for example to create it.

   - tick-list (optional)
     A list of tick types:
     :option-volume                  Option Volume (currently for stocks)
     :option-open-interest           Option Open Interest (currently for stocks)
     :historical-volatility          Historical Volatility (currently for stocks)
     :option-implied-volatility      Option Implied Volatility (currently for stocks)
     :index-future-premium           Index Future Premium
     :miscellaneous-stats            Miscellaneous Stats
     :mark-price                     Mark Price (used in TWS P&L computations)
     :auction-values                 Auction values (volume, price and imbalance)
     :realtime-volume                RTVolume
     :shortable                      Shortable
     :inventory                      Inventory
     :fundamental-ratios             Fundamental Ratios
     :realtime-historical-volatility Realtime Historical Volatility

     if no tick list is specified, a single snapshot of market data will come back
     and have the market data subscription will be immediately canceled.

     mkt-data-options is usually nil
     "
  ([ecs ticker-id contract tick-list snapshot? regulatory-snapshot?]
   (.reqMktData ecs ticker-id
                (map-> com.ib.client.Contract contract)
                (translate :to-ib :tick-list tick-list)
                snapshot?
                regulatory-snapshot?
                nil)))


(defn cancel-market-data
  "After calling this method, market data for the specified Id will stop
  flowing."
  [ecs ticker-id]
  (.cancelMktData ecs ticker-id))


(defn calculate-implied-volatility
  "Call this function to calculate volatility for a supplied option price and
  underlying price.

  "
  [ecs ticker-id option-contract option-price underlying-price]
  (.calculateImpliedVolatility ecs ticker-id
                               (map-> com.ib.client.Contract option-contract)
                               option-price underlying-price nil))


(defn cancel-calculate-implied-volatility
  "Call this function to cancel a request to calculate volatility for a supplied
  option price and underlying price."
  [ecs ticker-id]
  (.cancelCalculateImpliedVolatility ecs ticker-id))


(defn calculate-option-price
  "Call this function to calculate option price and greek values for a supplied
  volatility and underlying price."
  [ecs ticker-id option-contract volatility underlying-price]
  (.calculateOptionPrice ecs ticker-id
                         (map-> com.ib.client.Contract option-contract)
                         volatility underlying-price nil))


(defn cancel-calculate-option-price
  "Call this function to cancel a request to calculate the option price and
  greek values for a supplied volatility and underlying price."
  [ecs ticker-id]
  (.cancelCalculateOptionPrice ecs ticker-id))


(defn request-sec-def-option-parameters
  [ecs reqId underlyingSymbol futFopExchange underlyingSecType underlyingConId]
  (.reqSecDefOptParams ecs reqId underlyingSymbol futFopExchange underlyingSecType underlyingConId))


(defn request-market-data-type
  "The API can receive frozen market data from Trader Workstation. Frozen market
  data is the last data recorded in our system. During normal trading hours, the
  API receives real-time market data. If you use this function, you are telling
  TWS to automatically switch to frozen market data after the close. Then,
  before the opening of the next trading day, market data will automatically
  switch back to real-time market data.

  type can be :frozen or :real-time-streaming
  "
  [ecs type]
  (.reqMarketDataType ecs (translate :to-ib :market-data-type type)))


;;;
;;; Orders
;;;
(defn place-order
  ([ecs order-id contract order]
   (.placeOrder ecs order-id
                (map-> com.ib.client.Contract contract)
                (map-> com.ib.client.Order order))))


(defn cancel-order
  "Call this method to cancel an order."
  [ecs order-id]
  (.cancelOrder ecs order-id (OrderCancel.)))


(defn request-open-orders
  "Call this method to request any open orders that were placed from this API
  client. Each open order will be fed back through the openOrder() and
  orderStatus() methods on the EWrapper.

  Note: The client with a clientId of \"0\" will also receive the TWS-owned open
  orders. These orders will be associated with the client and a new orderId will
  be generated. This association will persist over multiple API and TWS
  sessions."
  [ecs]
  (.reqOpenOrders ecs))


(defn request-all-open-orders
  "Call this method to request all open orders that were placed from all API
  clients linked to one TWS, and also from the TWS. Note that you can run up to
  8 API clients from a single TWS. Each open order will be fed back through the
  openOrder() and orderStatus() methods on the EWrapper.

  Note: No association is made between the returned orders and the requesting
  client."
  [ecs]
  (.reqAllOpenOrders ecs))


(defn request-auto-open-orders
  "Call this method to request that newly created TWS orders be implicitly
  associated with the client. When a new TWS order is created, the order will be
  associated with the client and automatically fed back through the openOrder()
  and orderStatus() methods on the EWrapper.

  Note:  TWS orders can only be bound to clients with a clientId of 0."
  [ecs auto-bind?]
  (.reqAutoOpenOrders ecs auto-bind?))


(defn request-completed-orders
  [ecs api-only?]
  (.reqCompletedOrders ecs api-only?))


(defn request-ids
  "Call this function to request the next valid ID that can be used when placing
  an order. After calling this method, the nextValidId() event will be
  triggered, and the id returned is that next valid ID. That ID will reflect any
  autobinding that has occurred (which generates new IDs and increments the next
  valid ID therein)."
  [ecs]
  (.reqIds ecs 1))


(defn exercise-options
  "Call this funtion to exercise options.

  action can be :exercise or :lapse

  account For institutional orders. Specifies the IB account.

  override? Specifies whether your setting will override the system's natural
  action. For example, if your action is \"exercise\" and the option is not
  in-the-money, by natural action the option would not exercise. If you have
  override? set to true the natural action would be overridden and the
  out-of-the money option would be exercised.

  Note: SMART is not an allowed exchange in exerciseOptions() calls, and TWS
  does a request for the position in question whenever any API initiated
  exercise or lapse is attempted."
  [ecs ticker-id contract action quantity account override?]
  (.exerciseOptions ecs ticker-id
                    (map-> com.ib.client.Contract contract)
                    (translate :to-ib :exercise-action action)
                    quantity
                    account
                    (if override? 1 0)))


(defn request-global-cancel
  "Use this method to cancel all open orders globally. It cancels both API and
  TWS open orders.

  If the order was created in TWS, it also gets canceled. If the order was
  initiated in the API, it also gets canceled."
  [ecs]
  (.reqGlobalCancel ecs))


;;;
;;; Histogram
;;;
(defn request-histogram-data
  [ecs ticker-id contract useRTH period]
  (.reqHistogramData ecs ticker-id (map-> com.ib.client.Contract contract) useRTH period))

(defn cancel-histogram-data
  [ecs ticker-id]
  (.cancelHistogramData ecs ticker-id))


;;;
;;; Account and Portfolio
;;;
(defn request-account-updates
  "Call this function to start getting account values, portfolio, and last
  update time information. The account data will be fed back through the
  updateAccountTime(), updateAccountValue() and updatePortfolio() EWrapper
  methods.

  The account information resulting from the invocation of reqAccountUpdates
  is the same information that appears in Trader Workstation’s Account Window.
  When trying to determine the definition of each variable or key within the API
  account data, it is essential that you use the TWS Account Window as
  guidance."
  [ecs subscribe? account-code]
  (.reqAccountUpdates ecs subscribe? account-code))


(defn request-account-summary
  "Call this method to request and keep up to date the data that appears on the
  TWS Account Window Summary tab. The data is returned by accountSummary().

  Note: This request can only be made when connected to a Financial Advisor (FA)
  account."
  [ecs request-id group tags]
  (.reqAccountSummary ecs request-id group tags))


(defn cancel-account-summary
  "Cancels the request for Account Window Summary tab data."
  [ecs request-id]
  (.cancelAccountSummary ecs request-id))                   ;  (.cancelAccountSummary ecs 63 1 request-id))


(defn request-positions
  "Requests real-time position data for all accounts."
  [ecs]
  (.reqPositions ecs))


(defn cancel-positions
  "Cancels real-time position updates."
  [ecs]
  (.cancelPositions ecs))


(defn request-account-updates-multi
  [ecs reqId account modelCode ledgerAndNLV?]
  (.reqAccountUpdatesMulti ecs reqId account modelCode ledgerAndNLV?))


(defn cancel-account-updates-multi
  [ecs reqId]
  (.cancelAccountUpdatesMulti ecs reqId))


(defn request-positions-multi
  [ecs reqId account modelCode]
  (.reqPositionsMulti ecs reqId account modelCode))


(defn cancel-positions-multi
  [ecs reqId]
  (.cancelPositionsMulti ecs reqId))


(defn request-pnl
  [ecs reqId account modelCode]
  (.reqPnL ecs reqId account modelCode))


(defn cancel-pnl
  [ecs reqId]
  (.cancelPnL ecs reqId))


(defn request-pnl-single
  [ecs reqId account modelCode conid]
  (.reqPnLSingle ecs reqId account modelCode conid))


(defn cancel-pnl-single
  [ecs reqId]
  (.cancelPnLSingle ecs reqId))


;;;
;;; Executions
;;;
(defn request-executions
  "When this method is called, the execution reports from the last 24 hours that
  meet the filter criteria are downloaded to the client via the execDetails()
  method. To view executions beyond the past 24 hours, open the Trade Log in TWS
  and, while the Trade Log is displayed, request the executions again from the
  API."
  [ecs req-id execution-filter]
  (.reqExecutions ecs req-id (map-> com.ib.client.ExecutionFilter execution-filter)))

;;;
;;; Contract Details
;;;
(defn request-contract-details
  "Call this function to download all details for a particular
  contract. The contract details will be received in a :contract-details
  message"
  ([ecs request-id contract]
   (.reqContractDetails ecs request-id (map-> com.ib.client.Contract contract))))


;;;
;;; Market Depth
;;;
(defn request-market-depth
  "Call this method to request market depth for a specific contract.
  The market depth will be returned by the updateMktDepth() and
  updateMktDepthL2() methods.

  mkt-depth-options usually nil
  "
  [ecs ticker-id contract rows is-smart-depth? mkt-depth-options]
  (.reqMktDepth ecs ticker-id (map-> com.ib.client.Contract contract) rows is-smart-depth? mkt-depth-options))


(defn cancel-market-depth
  "After calling this method, market depth data for the specified Id will stop
  flowing."
  [ecs ticker-id is-smart-depth?]
  (.cancelMktDepth ecs ticker-id is-smart-depth?))


(defn request-market-depth-exchanges
  [ecs]
  (.reqMktDepthExchanges ecs))


(defn request-market-rule
  "Requests details about a given market rule
  The market rule for an instrument on a particular exchange provides details about how the minimum price increment changes with price
  A list of market rule ids can be obtained by invoking reqContractDetails on a particular contract. The returned market rule ID list will provide the market rule ID for the instrument in the correspond valid exchange list in contractDetails."
  [ecs req-id]
  (.reqMarketRule ecs req-id))


(defn request-matching-symbols
  "Requests matching stock symbols."
  [ecs req-id pattern]
  (.reqMatchingSymbols ecs req-id pattern))


(defn request-smart-components
  [ecs req-id bbo-exchange]
  (.reqSmartComponents ecs req-id bbo-exchange))

;;;
;;; News Bulletins
;;;
(defn request-news-bulletins
  "Call this function to start receiving news bulletins. Each bulletin will
   be sent in a :news-bulletin, :exchange-unavailable, or :exchange-available
   message."
  ([ecs all-messages?]
   (.reqNewsBulletins ecs all-messages?)))


(defn cancel-news-bulletins
  "Call this function to stop receiving news bulletins."
  [ecs]
  (.cancelNewsBulletins ecs))


(defn request-news-providers
  [ecs]
  (.reqNewsProviders ecs))


(defn request-news-article
  [ecs request-id provider-code article-id]
  (.reqNewsArticle ecs request-id provider-code article-id nil))


(defn request-historical-news
  "conId contract id of ticker
  providerCodes - a '+'-separated list of provider codes
  startDateTime - marks the (exclusive) start of the date range. The format is yyyy-MM-dd HH:mm:ss.0
  endDateTime\t- marks the (inclusive) end of the date range. The format is yyyy-MM-dd HH:mm:ss.0
  totalResults\t- the maximum number of headlines to fetch (1 - 300)"
  [ecs request-id conid provider-codes start-date-time end-date-time total-results]
  (.reqHistoricalNews ecs request-id conid provider-codes start-date-time end-date-time total-results nil)
  )


;;;
;;; Financial Advisors
;;;
(defn request-managed-accounts
  "Call this method to request the list of managed accounts. The list will be
  returned by the managedAccounts() method on the EWrapper.

  Note: This request can only be made when connected to a Financial Advisor (FA)
  account"
  [ecs]
  (.reqManagedAccts ecs))


(defn request-financial-advisor-data
  "Call this method to request FA configuration information from TWS. The data
  returns in an XML string via the receiveFA() method.

  data-type should be one of :financial-advisor-groups
                             :financial-advisor-profile
                             :financial-advisor-account-aliases
  "
  [ecs data-type]
  (.requestFA ecs (translate :to-ib :financial-advisor-data-type data-type)))


(defn replace-financial-advisor-data
  "Call this method to replace FA data with new xml content."
  [ecs data-type xml]
  (.replaceFA ecs (translate :to-ib :financial-advisor-data-type data-type) xml))


(defn request-soft-dollar-tiers
  [ecs reqId]
  (.reqSoftDollarTiers ecs reqId))


(defn request-family-codes
  [ecs]
  (.reqFamilyCodes ecs))


;;;
;;; Market Scanners
;;;
(defn request-scanner-parameters
  "Call this method to receive an XML document that describes the valid
  parameters that a scanner subscription can have."
  [ecs]
  (.reqScannerParameters ecs))


(defn request-scanner-subscription
  "Call this method to start receiving market scanner results through the
  scannerData() EWrapper method."
  [ecs ticker-id subscription subscription-options subscription-filter-options]
  (.reqScannerSubscription ecs
                           ticker-id
                           (map-> com.ib.client.ScannerSubscription subscription)
                           subscription-options
                           subscription-filter-options))


(defn cancel-scanner-subscription
  "Call this method to stop receiving market scanner results."
  [ecs ticker-id]
  (.cancelScannerSubscription ecs ticker-id))


;;;
;;; Historical Data
;;;
(defn request-historical-data
  "Start receiving historical price bars stretching back <duration> <duration-unit>s back,
   up till <end> for the specified contract. The messages will have :request-id of <id>.

   end: yyyyMMdd HH:mm:ss {TMZ} for example 20130701 23:59:59 GMT

   duration-unit should be one of :second(s), :day(s), :week(s), or :year(s).

   bar-size-unit should be one of :second(s), :minute(s), :hour(s), or :day(s).

   what-to-show should be one of :trades, :midpoint, :bid, :ask, :bid-ask,
   :historical-volatility, :option-implied-volatility, :yield-ask :yield-bid :yield-bid-ask :yield-last :adjusted-last

   formatDate set to 1 to obtain the bars' time as yyyyMMdd HH:mm:ss, set to 2 to obtain it like system time format in seconds
   Note that formatData parameter affects intra-day bars only; 1-day bars always return with date in YYYYMMDD format.

   keepUpToDate set to True to received continuous updates on most recent bar data. If True, and endDateTime cannot be specified.
   "
  [ecs request-id contract end duration duration-unit bar-size bar-size-unit
   what-to-show use-regular-trading-hours? format-date keep-up-to-date?]
  (let [[acceptable-duration acceptable-duration-unit]
        (translate :to-ib :acceptable-duration [duration duration-unit])]
    (.reqHistoricalData ecs
                        request-id
                        (map-> com.ib.client.Contract contract)
                        end                                 ;(translate :to-ib :date-time end)
                        (translate :to-ib :duration [acceptable-duration
                                                     acceptable-duration-unit])
                        (translate :to-ib :bar-size [bar-size bar-size-unit])
                        (.name (translate :to-ib :what-to-show what-to-show))
                        (if use-regular-trading-hours? 1 0)
                        format-date
                        keep-up-to-date?
                        nil)))


(defn cancel-historical-data
  "Call this method to stop receiving historical data results."
  [ecs request-id]
  (.cancelHistoricalData ecs request-id))


(defn request-head-time-stamp
  "Returns the timestamp of earliest available historical data for a contract and data type"
  [ecs ticker-id contract what-to-show use-regular-trading-hours? format-date]
  (.reqHeadTimestamp  ecs
                      ticker-id
                      (map-> com.ib.client.Contract contract)
                      (.name (translate :to-ib :what-to-show what-to-show))
                      (if use-regular-trading-hours? 1 0)
                      format-date))


(defn cancel-head-time-stamp
  [ecs ticker-id]
  (.cancelHeadTimestamp ecs ticker-id))


;;;
;;; Real Time Bars
;;;
(defn request-real-time-bars
  "Start receiving real time bar results."
  [ecs request-id contract what-to-show use-regular-trading-hours?]
  (.reqRealTimeBars ecs request-id
                    (map-> com.ib.client.Contract contract)
                    5                                ; currently being ignored
                    (.name (translate :to-ib :what-to-show what-to-show))
                    use-regular-trading-hours?
                    nil))


(defn cancel-real-time-bars
  "Call this function to stop receiving real time bars for the passed in request-id"
  [ecs request-id]
  (.cancelRealTimeBars ecs request-id))


;;;
;;; Tick by tick
;;;
(defn request-tick-by-tick-data
  [ecs reqId contract tickType numberOfTicks ignore-size?]
  (.reqTickByTickData ecs reqId (map-> com.ib.client.Contract contract) tickType numberOfTicks ignore-size?))


(defn cancel-tick-by-tick-data
  [ecs reqId]
  (.cancelTickByTickData ecs reqId))


(defn request-historical-ticks
  [ecs reqId contract startDateTime endDateTime numberOfTicks what-to-show useRth ignoreSize]
  (.reqHistoricalTicks ecs reqId
                       (map-> com.ib.client.Contract contract)
                       startDateTime
                       endDateTime
                       numberOfTicks
                       (.name (translate :to-ib :what-to-show what-to-show))
                       useRth
                       ignoreSize
                       nil))


;;;
;;; Fundamental Data
;;;
(defn request-fundamental-data
  "Call this function to receive Reuters global fundamental data. There must be a
   subscription to Reuters Fundamental set up in Account Management before you
   can receive this data."
  [ecs request-id contract report-type]
  (.reqFundamentalData ecs request-id
                       (map-> com.ib.client.Contract contract)
                       (translate :to-ib :report-type report-type)
                       nil))


(defn cancel-fundamental-data
  "Call this function to stop receiving Reuters global fundamental data."
  [ecs request-id]
  (.cancelFundamentalData ecs request-id))


;;;
;;; Display Groups
;;;
(defn query-display-groups
  [ecs request-id]
  (.queryDisplayGroups ecs request-id))


(defn subscribe-to-group-events
  "group-id The ID of the group, currently it is a number from 1 to 7."
  [ecs request-id group-id]
  (.subscribeToGroupEvents ecs request-id group-id))


(defn update-display-group
  "request-id The requestId specified in subscribeToGroupEvents().

  contract-info The encoded value that uniquely represents the contract in IB.
  Possible values include:

    none = empty selection
    contractID@exchange – any non-combination contract.
                          Examples: 8314@SMART for IBM SMART;
                                    8314@ARCA for IBM @ARCA.
    combo = if any combo is selected.
  "
  [ecs request-id contract-info]
  (.updateDisplayGroup ecs request-id contract-info))


(defn unsubscribe-from-group-events
  [ecs request-id]
  (.unsubscribeFromGroupEvents ecs request-id))
