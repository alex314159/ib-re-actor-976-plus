(ns ib-re-actor.client-socket
  "This namespace is a wrapper of the EClientSocket interface of the
  InteractiveBrokers (IB) API.

  It marshalls data from clojure to what is expected on the IB side.

  Note that the IB API is asynchronous for the most part and that responses will
  be received through the EWrapper. Please refer to the link below to know what
  is expected.

  https://www.interactivebrokers.com/en/software/api/api.htm
  "
  (:require
   [ib-re-actor.mapping :refer [map->]]
   [ib-re-actor.translation :refer [translate]])
  (:import
   (com.ib.client EClientSocket)))

;;;
;;; Connection and Server
;;;
(defn connect
  "This function must be called before any other. There is no feedback
   for a successful connection, but a subsequent attempt to connect
   will return the message 'Already connected.'

   wrapper is an implementation of the EWrapper interface.

   host is the hostname running IB Gateway or TWS.

   port is the port IB Gateway / TWS is running on.

   client-id identifies this client. Only one connection to a gateway can
   be made per client-id at a time."
  ([wr host port client-id]
   (let [ecs (com.ib.client.EClientSocket. wr)]
     (.eConnect ecs host port client-id)
     ecs)))


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
  (translate :from-ib :connection-time (.TwsConnectionTime ecs)))


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
     and have the market data subscription will be immediately canceled."
  ([ecs ticker-id contract tick-list snapshot?]
   (.reqMktData ecs ticker-id
                (map-> com.ib.client.Contract contract)
                (translate :to-ib :tick-list tick-list)
                snapshot? nil)))


(defn cancel-market-data
  "After calling this method, market data for the specified Id will stop
  flowing."
  [ecs ticker-id]
  (.cancelMktData ecs ticker-id))


(defn calculate-implied-volatility
  "Call this function to calculate volatility for a supplied option price and
  underlying price."
  [ecs ticker-id option-contract option-price underlying-price]
  (.calculateImpliedVolatility ecs ticker-id
                               (map-> com.ib.client.Contract option-contract)
                               option-price underlying-price))


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
                         volatility underlying-price))


(defn cancel-calculate-option-price
  "Call this function to cancel a request to calculate the option price and
  greek values for a supplied volatility and underlying price."
  [ecs ticker-id]
  (.cancelCalculateOptionPrice ecs ticker-id))


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
  (.cancelOrder ecs order-id))


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
                    (if override? 1 0)))


(defn request-global-cancel
  "Use this method to cancel all open orders globally. It cancels both API and
  TWS open orders.

  If the order was created in TWS, it also gets canceled. If the order was
  initiated in the API, it also gets canceled."
  [ecs]
  (.reqGlobalCancel ecs))


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
  (.cancelAccountSummary ecs 63 1 request-id))


(defn request-positions
  "Requests real-time position data for all accounts."
  [ecs]
  (.reqPositions ecs))


(defn cancel-positions
  "Cancels real-time position updates."
  [ecs]
  (.cancelPositions ecs))


;;;
;;; Executions
;;;
(defn request-executions
  "When this method is called, the execution reports from the last 24 hours that
  meet the filter criteria are downloaded to the client via the execDetails()
  method. To view executions beyond the past 24 hours, open the Trade Log in TWS
  and, while the Trade Log is displayed, request the executions again from the
  API."
  [ecs execution-filter]
  (.reqExecutions ecs (map-> com.ib.client.ExecutionFilter execution-filter)))


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
  updateMktDepthL2() methods."
  [ecs ticker-id contract rows]
  (.reqMktDepth ecs ticker-id (map-> com.ib.client.Contract contract) rows nil))


(defn cancel-market-depth
  "After calling this method, market depth data for the specified Id will stop
  flowing."
  [ecs ticker-id]
  (.cancelMktDepth ecs ticker-id))


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
  [ecs ticker-id subscription]
  (.reqScannerSubscription ecs
                           (map-> com.ib.client.ScannerSubscription subscription)
                           nil))


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

   duration-unit should be one of :second(s), :day(s), :week(s), or :year(s).

   bar-size-unit should be one of :second(s), :minute(s), :hour(s), or :day(s).

   what-to-show should be one of :trades, :midpoint, :bid, :ask, :bid-ask,
   :historical-volatility, :option-implied-volatility, :option-volume,
   or :option-open-interest."
  [ecs request-id contract end duration duration-unit bar-size bar-size-unit
   what-to-show use-regular-trading-hours?]
  (let [[acceptable-duration acceptable-duration-unit]
        (translate :to-ib :acceptable-duration [duration duration-unit])]
    (.reqHistoricalData ecs
                        request-id
                        (map-> com.ib.client.Contract contract)
                        (translate :to-ib :date-time end)
                        (translate :to-ib :duration [acceptable-duration
                                                     acceptable-duration-unit])
                        (translate :to-ib :bar-size [bar-size bar-size-unit])
                        (translate :to-ib :what-to-show what-to-show)
                        (if use-regular-trading-hours? 1 0)
                        2 nil)))


(defn cancel-historical-data
  "Call this method to stop receiving historical data results."
  [ecs request-id]
  (.cancelHistoricalData ecs request-id))


;;;
;;; Real Time Bars
;;;
(defn request-real-time-bars
  "Start receiving real time bar results."
  [ecs request-id contract what-to-show use-regular-trading-hours?]
  (.reqRealTimeBars ecs request-id
                    (map-> com.ib.client.Contract contract)
                    5
                    (translate :to-ib :what-to-show what-to-show)
                    use-regular-trading-hours?))


(defn cancel-real-time-bars
  "Call this function to stop receiving real time bars for the passed in request-id"
  [ecs request-id]
  (.cancelRealTimeBars ecs request-id))


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
                       (translate :to-ib :report-type report-type)))


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
