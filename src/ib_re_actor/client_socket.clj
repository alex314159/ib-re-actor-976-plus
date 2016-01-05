(ns ib-re-actor.client-socket
  (:require [clojure.tools.logging :as log]
            [ib-re-actor.mapping :refer [map->]]
            [ib-re-actor.translation :refer [translate]]))


(defonce default-server-log-level :error)

(defonce last-ticker-id (atom 1))
(defonce next-order-id (atom 0))
(defonce next-request-id (atom 0))


(defn get-order-id []
  (swap! next-order-id inc))


(defn get-request-id []
  (swap! next-request-id inc))


(defn set-server-log-level
  "Call this function to set the log level used on the server."
  [{:keys [ecs]} log-level]
  (.setServerLogLevel ecs (translate :to-ib :log-level log-level)))


(defn connect
  "This function must be called before any other. There is no feedback
   for a successful connection, but a subsequent attempt to connect
   will return the message 'Already connected.'

   wrapper is an implementation of the EWrapper interface.

   host is the hostname running IB Gateway or TWS.

   port is the port IB Gateway / TWS is running on.

   client-id identifies this client. Only one connection to a gateway can
   be made per client-id at a time."
  ([wr] (connect wr "localhost" 7496))
  ([wr client-id] (connect wr "localhost" 7496 client-id))
  ([wr host port client-id]
   (try
     (let [connection (com.ib.client.EClientSocket. wr)]
       (.eConnect connection host port client-id)
       (when-not (= :error default-server-log-level)
         (set-server-log-level connection default-server-log-level))
       connection)
     (catch Exception ex
       (log/error "Error trying to connect to " host ":" port ": " ex)))))


(defn disconnect
  "Call this function to terminate the connections with TWS.
   Calling this function does not cancel orders that have already been sent."
  [{:keys [ecs]}]
  (.eDisconnect ecs))


(defn connection-time [{:keys [ecs]}]
  (translate :from-ib :connection-time
             (.TwsConnectionTime ecs)))


(defn server-version [{:keys [ecs]}]
  (.serverVersion ecs))


(defn request-current-time [{:keys [ecs]}]
  (.reqCurrentTime ecs))


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
     :option-volume                       Option Volume (currently for stocks)
     :option-open-interest                Option Open Interest (currently for stocks)
     :historical-volatility 104           Historical Volatility (currently for stocks)
     :option-implied-volatility 106       Option Implied Volatility (currently for stocks)
     :index-future-premium 162            Index Future Premium
     :miscellaneous-stats 165             Miscellaneous Stats
     :mark-price 221                      Mark Price (used in TWS P&L computations)
     :auction-values 225                  Auction values (volume, price and imbalance)
     :realtime-volume 233                 RTVolume
     :shortable 236                       Shortable
     :inventory 256                       Inventory
     :fundamental-ratios 258              Fundamental Ratios
     :realtime-historical-volatility 411  Realtime Historical Volatility

     if no tick list is specified, a single snapshot of market data will come back
     and have the market data subscription will be immediately canceled."
  ([{:keys [ecs]} contract ticker-id tick-list snapshot?]
   (.reqMktData ecs ticker-id
                (map-> com.ib.client.Contract contract)
                (translate :to-ib :tick-list tick-list)
                snapshot?))
  ([{:keys [ecs]} contract]
   (let [ticker-id (swap! last-ticker-id inc)]
     (request-market-data ecs contract ticker-id "" false)
     ticker-id)))

(defn cancel-market-data [{:keys [ecs]} ticker-id]
  (.cancelMktData ecs ticker-id))

(defn request-historical-data
  "Start receiving historical price bars stretching back <duration> <duration-unit>s back,
   up till <end> for the specified contract. The messages will have :request-id of <id>.

   duration-unit should be one of :second(s), :day(s), :week(s), or :year(s).

   bar-size-unit should be one of :second(s), :minute(s), :hour(s), or :day(s).

   what-to-show should be one of :trades, :midpoint, :bid, :ask, :bid-ask,
   :historical-volatility, :option-implied-volatility, :option-volume,
   or :option-open-interest."
  ([{:keys [ecs]} id contract end duration duration-unit bar-size bar-size-unit
    what-to-show use-regular-trading-hours?]
   (let [[acceptable-duration acceptable-duration-unit]
         (translate :to-ib :acceptable-duration [duration duration-unit])]
     (.reqHistoricalData ecs
                         id
                         (map-> com.ib.client.Contract contract)
                         (translate :to-ib :date-time end)
                         (translate :to-ib :duration [acceptable-duration
                                                      acceptable-duration-unit])
                         (translate :to-ib :bar-size [bar-size bar-size-unit])
                         (translate :to-ib :what-to-show what-to-show)
                         (if use-regular-trading-hours? 1 0)
                         2)))
  ([id contract end duration duration-unit bar-size bar-size-unit what-to-show]
   (request-historical-data id contract end duration duration-unit
                            bar-size bar-size-unit what-to-show true))
  ([id contract end duration duration-unit bar-size bar-size-unit]
   (request-historical-data id contract end duration duration-unit
                            bar-size bar-size-unit :trades true)))

(defn request-real-time-bars
  "Start receiving real time bar results."
  [{:keys [ecs]} id contract what-to-show use-regular-trading-hours?]
  (.reqRealTimeBars ecs
                    id
                    (map-> com.ib.client.Contract contract)
                    5
                    (translate :to-ib :what-to-show what-to-show)
                    use-regular-trading-hours?))

(defn cancel-real-time-bars
  "Call this function to stop receiving real time bars for the passed in request-id"
  [{:keys [ecs]} id]
  (.cancelRealTimeBars ecs id))

(defn request-news-bulletins
  "Call this function to start receiving news bulletins. Each bulletin will
   be sent in a :news-bulletin, :exchange-unavailable, or :exchange-available
   message."
  ([{:keys [ecs]}]
   (request-news-bulletins ecs true))
  ([{:keys [ecs]} all-messages?]
   (.reqNewsBulletins ecs all-messages?)))

(defn cancel-news-bulletins
  "Call this function to stop receiving news bulletins."
  [{:keys [ecs]}]
  (.cancelNewsBulletins ecs))

(defn request-fundamental-data
  "Call this function to receive Reuters global fundamental data. There must be a
   subscription to Reuters Fundamental set up in Account Management before you
   can receive this data."
  ([{:keys [ecs]} contract report-type]
   (request-fundamental-data ecs (get-request-id) contract report-type))
  ([{:keys [ecs]} request-id contract report-type]
   (.reqFundamentalData ecs request-id
                        (map-> com.ib.client.Contract contract)
                        (translate :to-ib :report-type report-type))))

(defn cancel-fundamental-data
  "Call this function to stop receiving Reuters global fundamental data."
  [{:keys [ecs]} request-id]
  (.cancelFundamentalData ecs request-id))

(defn request-contract-details
  "Call this function to download all details for a particular
  contract. The contract details will be received in a :contract-details
  message"
  ([{:keys [ecs]} contract]
   (request-contract-details ecs (get-request-id) contract))
  ([{:keys [ecs]} request-id contract]
   (log/debug "Requesting contract details #" request-id " for " (pr-str contract))
   (.reqContractDetails ecs request-id
                        (map-> com.ib.client.Contract contract))
   request-id))

(defn place-order
  ([{:keys [ecs]} contract order]
   (let [order-id (get-order-id)]
     (place-order ecs order-id contract (assoc order :order-id order-id))))
  ([{:keys [ecs]} order-id contract order]
   (.placeOrder ecs order-id
                (map-> com.ib.client.Contract contract)
                (map-> com.ib.client.Order order))
   order-id))

(defn cancel-order
  [{:keys [ecs]} order-id]
  (.cancelOrder ecs order-id))

(defn request-open-orders [{:keys [ecs]}]
  (.reqOpenOrders ecs))

(defn request-executions
  ([{:keys [ecs]}]
   (request-executions ecs nil))
  ([{:keys [ecs]} client-id]
   (.reqExecutions ecs client-id)))

(defn request-account-updates
  [{:keys [ecs]} subscribe? account-code]
  (.reqAccountUpdates ecs subscribe? account-code))
