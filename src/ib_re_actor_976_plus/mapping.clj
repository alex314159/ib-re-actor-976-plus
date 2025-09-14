(ns ib-re-actor-976-plus.mapping
  "Functions for mapping to and from Interactive Brokers classes. It is much easier to work
with maps in clojure, so we use these functions internally on all the data we exchange
with the Interactive Brokers API.

In addition to just converting to maps, we also use these functions to translate some
primitives: strings with constant values into keywords, booleans in strings into booleans,
date strings into clj-time dates, etc."
  (:require
    [clojure.string :refer [join]]
    [ib-re-actor-976-plus.translation :refer [translate tws-version]])
  (:import (com.google.protobuf Descriptors$FieldDescriptor$JavaType Message)
           (java.util Collections$EmptyList)))

(defprotocol Mappable
  (->map [this]
    "Create a map with the all the non-the non-null properties of object."))

(defmulti map-> (fn [type _] type))

(defn- assoc-if-val-non-nil
  "Chainable, conditional assoc. If v is not nil, assoc it and return the result,
otherwise, don't and return m unchanged."
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v translation]
   (if (nil? v) m (assoc m k (translate :from-ib translation v)))))

(defn- assoc-nested [m k v]
  (if (nil? v) m (assoc m k (->map v))))

(defn emit-map<-field
  "When mapping from an object to a clojure map, this creates a call to assoc in the value.
optional parameters:

   :translation <<translation key from translation.clj>>:
      Specifying this option will add a call to (translate to-from ...) in each field
setter or assoc when mapping to and from objects.

   :nested <<type>>:
      Specifying this will map a nested instance of another class."
  [this [k field & options]]
  (let [{:keys [translation nested]} (apply hash-map options)
        m (gensym "m")]
    (cond
      (not (nil? translation)) `((assoc-if-val-non-nil ~k (. ~this ~field) ~translation))
      (not (nil? nested)) `((assoc-nested ~k (. ~this ~field)))
      :else `((assoc-if-val-non-nil ~k (. ~this ~field))))))

(defn emit-map->field
  "When mapping from a clojure map to an object, this creates a call to set the associated
field on the object."
  [m this [key field & options]]
  (let [{:keys [translation nested]} (apply hash-map options)
        val (gensym "val")]
    `((if (contains? ~m ~key)
        (let [~val (~key ~m)]
          (try
            (. ~this ~field
               ~(cond
                  (not (nil? translation)) `(translate :to-ib ~translation ~val)
                  (not (nil? nested)) `(map-> ~nested ~val)
                  :else `~val))
            ;(set! (. ~this ~field)
            ;      ~(cond
            ;         (not (nil? translation)) `(translate :to-ib ~translation ~val)
            ;         (not (nil? nested)) `(map-> ~nested ~val)
            ;         :else `~val))
            (catch ClassCastException ex#
              (throw (ex-info (str "Failed to map field " ~(str field)
                                   ~(when translation
                                      (str ", using translation " translation))
                                   ", value \"" ~val "\"")
                              {:class (class ~this)
                               :key ~key
                               :field ~(str field)
                               :translation ~translation}
                              ex#)))))))))

(defmacro defmapping
  "This is used to extend an Interactive Brokers API class with a method to convert it into
a clojure map, and using the same information, add a method to the map-> multimethod to
convert maps into instances of the IB class."
  [c & field-keys]
  (let [this (gensym "this")
        field-map (gensym "field-map")
        valid-keys (gensym "valid-keys")]
    `(do
       (extend-type ~c
         Mappable
         (->map [~this]
                (-> {} ~@(mapcat (partial emit-map<-field this) field-keys))))

       (defmethod map-> ~c [_# ~field-map]
         (let [~this (new ~c)]
           ~@(mapcat (partial emit-map->field field-map this) field-keys)
           ~this)))))

(defmacro defmapping-readonly
  "Same as defmapping, but for classes that don't have public constructors. Since we can't
create instances, we will only map from objects to clojure maps."
  [c & field-keys]
  (let [this (gensym "this")]
    `(extend-type ~c
       Mappable
       (->map [~this]
              (-> {} ~@(mapcat (partial emit-map<-field this) field-keys))))))

(defmapping com.ib.client.Contract
            [:conid conid]
            [:currency currency]
            [:exchange exchange]
            [:last-trade-date-or-contract-month lastTradeDateOrContractMonth] ;:translation :expiry
            [:local-symbol localSymbol]
            [:trading-class tradingClass]
            [:multiplier multiplier :translation :double-string]
            [:primary-exch primaryExch]
            [:right right :translation :right]
            [:sec-id secId]
            [:sec-id-type secIdType :translation :security-id-type]                        ;
            [:sec-type secType :translation :security-type]
            [:strike strike]
            [:symbol symbol]
            [:delta-neutral-contract deltaNeutralContract]
            [:include-expired includeExpired]
            [:combo-legs comboLegs]
            [:combo-legs-descrip comboLegsDescrip])


(defmapping com.ib.client.ContractDetails
            [:contract contract]
            [:market-name marketName]
            [:min-tick minTick]
            [:price-magnifier priceMagnifier]
            [:order-types orderTypes :translation :order-types]
            [:valid-exchanges validExchanges :translation :exchanges]
            [:underlying-contract-id underConid]
            [:long-name longName]
            [:contract-month contractMonth]
            [:industry industry]
            [:category category]
            [:subcategory subcategory]
            [:time-zone-id timeZoneId]
            [:trading-hours tradingHours]
            [:liquid-hours liquidHours]
            [:ev-rule evRule]
            [:ev-multiplier evMultiplier]
            [:sec-id-list secIdList]                        ; // CUSIP/ISIN/etc.
            [:agg-group aggGroup]
            [:under-symbol underSymbol]
            [:under-sec-type underSecType]
            [:market-rule-ids marketRuleIds]
            [:real-expiration-date realExpirationDate]
            [:last-trade-time lastTradeTime]
            [:stock-type stockType]                          ; // COMMON, ETF, ADR etc.
            [:min-size minSize]
            [:size-increment sizeIncrement]
            [:suggested-size-increment suggestedSizeIncrement]

            ;bond values
            [:cusip cusip]
            [:ratings ratings]
            [:description-details descAppend]
            [:bond-type bondType]
            [:coupon-type couponType]
            [:callable? callable]
            [:putable? putable]
            [:coupon coupon]
            [:convertible? convertible]
            [:maturity maturity :translation :date]
            [:issue-date issueDate :translation :date]
            [:next-option-date nextOptionDate :translation :date]
            [:next-option-type nextOptionType]
            [:next-option-partial nextOptionPartial]
            [:notes notes]

            ;fund values
            [:fund-name fundName]
            [:fund-family fundFamily]
            [:fund-type fundType]
            [:fund-front-load fundFrontLoad]
            [:fund-back-load fundBackLoad]
            [:fund-back-load-time-interval fundBackLoadTimeInterval]
            [:fund-management-fee fundManagementFee]
            [:fund-closed fundClosed]
            [:fund-closed-for-new-investors fundClosedForNewInvestors]
            [:fund-closed-for-new-money fundClosedForNewMoney]
            [:fund-notify-amount fundNotifyAmount]
            [:fund-minimum-initial-purchase fundMinimumInitialPurchase]
            [:fund-subsequent-minimum-purchase fundSubsequentMinimumPurchase]
            [:fund-blue-sky-states fundBlueSkyStates]
            [:fund-blue-sky-territories fundBlueSkyTerritories]
            [:fund-distribution-policiy-indicator fundDistributionPolicyIndicator]
            [:fund-asset-type fundAssetType]
            [:ineligibility-reason-list ineligibilityReasonList])

(defmapping com.ib.client.ExecutionFilter
            [:client-id clientId]
            [:account-code acctCode]
            [:after-time time ]                                       ;:translation :timestamp
            [:order-symbol symbol]
            [:security-type secType :translation :security-type]
            [:exchange exchange]
            [:side side :translation :order-action])

(defmapping com.ib.client.Execution
            [:account-code acctNumber]
            [:average-price avgPrice]
            [:client-id clientId]
            [:cumulative-quantity cumQty :translation :decimal-to-long]
            [:exchange exchange]
            [:execution-id execId]
            [:liquidate-last liquidation]
            [:order-id orderId]
            [:permanent-id permId]
            [:price price]
            [:shares shares :translation :decimal-to-long]
            [:side side :translation :execution-side]
            [:time time]
            [:order-ref orderRef]
            [:ev-rule evRule]
            [:ev-multiplier evMultiplier]
            [:model-code modelCode]
            [:last-liquidity lastLiquidity]
            [:last-liquidity-str lastLiquidityStr]
            [:pending-price-revision pendingPriceRevision])


(defmapping com.ib.client.Order
            [:account-code account]
            [:order-id orderId]
            [:client-id clientId]
            [:permanent-id permId]
            [:transmit? transmit]
            [:quantity totalQuantity :translation :string-to-decimal]
            [:action action :translation :order-action]
            [:type orderType :translation :order-type]
            [:block-order? blockOrder]
            [:sweep-to-fill? sweepToFill]
            [:time-in-force tif :translation :time-in-force]
            [:good-after-time goodAfterTime]
            [:good-till-date goodTillDate]
            [:outside-regular-trading-hours? outsideRth]
            [:hidden? hidden]
            [:all-or-none? allOrNone]
            [:limit-price lmtPrice]
            [:discretionary-amount discretionaryAmt]
            [:stop-price auxPrice])

(defmapping-readonly com.ib.client.Bar
                     [:time time]
                     [:open open]
                     [:high high]
                     [:low low]
                     [:close close]
                     [:volume volume :translation :decimal-to-long]
                     [:count count]
                     [:wap wap :translation :decimal-to-double])

(if (< (compare tws-version "10.33.01") 0)
  (defmapping-readonly com.ib.client.OrderState
                       [:status status :translation :order-status]
                       [:initial-margin-before initMarginBefore]
                       [:maintenance-margin-before maintMarginBefore]
                       [:equity-with-loan-before equityWithLoanBefore]
                       [:initial-margin-change initMarginChange]
                       [:maintenance-margin-change maintMarginChange]
                       [:equity-with-loan-change equityWithLoanChange]
                       [:initial-margin-after initMarginAfter]
                       [:maintenance-margin-after maintMarginAfter]
                       [:equity-with-loan-after equityWithLoanAfter]
                       [:commission commission]
                       [:minimum-commission minCommission]
                       [:maximum-commission maxCommission]
                       [:commission-currency commissionCurrency]
                       [:warning-text warningText]
                       [:completed-time completedTime]
                       [:completed-status completedStatus])
  (defmapping-readonly com.ib.client.OrderState
                       [:status status :translation :order-status]
                       [:initial-margin-before initMarginBefore]
                       [:maintenance-margin-before maintMarginBefore]
                       [:equity-with-loan-before equityWithLoanBefore]
                       [:initial-margin-change initMarginChange]
                       [:maintenance-margin-change maintMarginChange]
                       [:equity-with-loan-change equityWithLoanChange]
                       [:initial-margin-after initMarginAfter]
                       [:maintenance-margin-after maintMarginAfter]
                       [:equity-with-loan-after equityWithLoanAfter]
                       [:commission-and-fees commissionAndFees]
                       [:minimum-commission-and-fees minCommissionAndFees]
                       [:maximum-commission-and-fees maxCommissionAndFees]
                       [:commission-and-fees-currency commissionAndFeesCurrency]
                       [:margin-currency marginCurrency]
                       [:warning-text warningText]
                       [:completed-time completedTime]
                       [:completed-status completedStatus])

  )

(if (< (compare tws-version "10.33.01") 0)
  (defmapping-readonly (eval 'com.ib.client.CommissionReport)
                       [:commission commission]
                       [:currency currency]
                       [:execution-id execId]
                       [:realized-profit-loss realizedPNL]
                       [:yield yield]
                       [:yield-redemption-date yieldRedemptionDate])
  (defmapping-readonly (eval 'com.ib.client.CommissionAndFeesReport)
                       [:commission-and-fees commissionAndFees]
                       [:currency currency]
                       [:execution-id execId]
                       [:realized-profit-loss realizedPNL]
                       [:yield yield]
                       [:yield-redemption-date yieldRedemptionDate])

  )

;; PROTOBUF MAPPING

(def field-cache (atom {}))

(defn get-cached-fields
  [proto-msg]
  (let [msg-class (class proto-msg)]
    (or (@field-cache msg-class)
        (try
          (let [fields (.getFields (.getDescriptorForType proto-msg))]
            (swap! field-cache assoc msg-class fields)
            fields)
          (catch IllegalArgumentException e
            (do
              (swap! field-cache assoc msg-class nil)
              nil))))))

(defn protobuf->map
  "Simple benchmarking shows little advantage for transients."
  ([proto-msg] (protobuf->map proto-msg false false))
  ([proto-msg include-defaults?] (protobuf->map proto-msg include-defaults? false))
  ([proto-msg include-defaults? use-transients?]
   (letfn [(convert-single-value [value field]
             (condp = (.getJavaType field)
               Descriptors$FieldDescriptor$JavaType/MESSAGE (protobuf->map value include-defaults?)
               Descriptors$FieldDescriptor$JavaType/ENUM (.getName value)
               Descriptors$FieldDescriptor$JavaType/BYTE_STRING (.toByteArray value)
               value))
           (convert-value [value field]
             (if (.isRepeated field)
               (mapv #(convert-single-value % field) value)
               (convert-single-value value field)))]
     (if-let [fields (get-cached-fields proto-msg)]
       (if use-transients?
         (persistent!
           (reduce (fn [acc field]
                     (let [value (.getField proto-msg field)]
                       (if (or include-defaults?
                               (.isRepeated field)
                               (= (.getJavaType field) Descriptors$FieldDescriptor$JavaType/MESSAGE)
                               (not= value (.getDefaultValue field)))
                         (assoc! acc (.getName field) (convert-value value field))
                         acc)))
                   (transient {})
                   fields))
         (reduce (fn [acc field]
                   (let [value (.getField proto-msg field)]
                     (if (or include-defaults?
                             (.isRepeated field)
                             (= (.getJavaType field) Descriptors$FieldDescriptor$JavaType/MESSAGE)
                             (not= value (.getDefaultValue field)))
                       (assoc acc (.getName field) (convert-value value field))
                       acc)))
                 {}
                 fields))
       {}))))  ; Return empty map if no fields cached (failed case)