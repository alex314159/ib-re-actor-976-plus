(ns ib-re-actor-976-plus.translation
  (:require
    ;[clj-time.coerce :as tc]
    ;[clj-time.core :as time]
    ;[clj-time.format :as tf]
    [clojure.string :as str]))

;Unfortunately it is important to know the TWS version here, because of the Decimal issue

(def tws-version
  (let [separator (if (= (subs (System/getProperty "os.name") 0 3) "Win") #"\\" #"/")]
    (last
      (drop-last
        (clojure.string/split
          (first
            (filter
              #(clojure.string/includes? % "twsapi")
              (clojure.string/split
                (System/getProperty "java.class.path") #":")))
          separator)))))

(def use-decimal?
  (let [v (map #(Long/parseLong %) (clojure.string/split tws-version #"\."))]
    (and (>= (first v) 10) (>= (second v) 10))))

(defmulti ^:dynamic translate
          "Translate to or from a value from the Interactive Brokers API.

        Examples:
        user> (translate :to-ib :duration-unit :seconds)
        \"S\"
        user> (translate :from-ib :duration-unit \"S\")
        :second"
          (fn [direction table-name _] [direction table-name]))

(defmulti ^:dynamic valid?
          "Check to see if a given value is an entry in the translation table.

        Examples:
        user> (valid? :to-ib :duration-unit :s)
        false
        user> (valid? :to-ib :duration-unit :seconds)
        true"
          (fn [direction table-name _] [direction table-name]))

(defmacro translation-table
  "Creates a table for translating to and from string values from the Interactive
Brokers API, as well as translate methods to and from the IB value and a method
to check if if a given value is valid (known)."

  ([name to-table]
   `(let [to-table# ~to-table
          from-table# (zipmap (vals to-table#)
                              (keys to-table#))]
      (translation-table ~name to-table# from-table#)))
  ([name to-table from-table]
   (let [table-name (keyword name)]
     `(let [to-table# ~to-table
            from-table# ~from-table]

        (def ~name to-table#)

        (defmethod valid? [:to-ib ~table-name] [_# _# val#]
          (contains? to-table# val#))

        (defmethod translate [:to-ib ~table-name] [_# _# val#]
          (when val#
            (cond
              (valid? :to-ib ~table-name val#)
              (to-table# val#)

              (string? val#)
              val#

              :else
              (throw (ex-info (str "Can't translate to IB " ~table-name " " val#)
                              {:value val#
                               :table ~table-name
                               :valid-values (keys to-table#)})))))

        (defmethod valid? [:from-ib ~table-name] [_# _# val#]
          (contains? from-table# val#))

        (defmethod translate [:from-ib ~(keyword name)] [_# _# val#]
          (when val#
            (cond
              (valid? :from-ib ~table-name val#)
              (from-table# val#)

              (string? val#)
              val#

              :else
              (throw (ex-info (str "Can't translate from IB " ~table-name " " val#)
                              {:value val#
                               :table ~table-name
                               :valid-values (vals to-table#)})))))))))

(translation-table duration-unit
                   {:second  "S"
                    :seconds "S"
                    :day     "D"
                    :days    "D"
                    :week    "W"
                    :weeks   "W"
                    :month   "M"
                    :months  "M"
                    :year    "Y"
                    :years   "Y"})

(defmethod translate [:to-ib :acceptable-duration] [_ _ [val unit]]
  (case unit
    :second [val :second]
    :seconds [val :seconds]
    :minute [(* 60 val) :seconds]
    :minutes [(* 60 val) :seconds]
    :hour [(* 60 60 val) :seconds]
    :hours [(* 60 60 val) :seconds]
    :day [val :day]
    :days [val :days]
    :week [val :week]
    :weeks [val :weeks]
    :month [val :month]
    :months [val :months]
    :year [val :year]
    :years [val :years]))

(translation-table security-type
                   (merge
                     {
                      :none              com.ib.client.Types$SecType/None
                      :equity            com.ib.client.Types$SecType/STK
                      :option            com.ib.client.Types$SecType/OPT
                      :future            com.ib.client.Types$SecType/FUT
                      :continuous-future com.ib.client.Types$SecType/CONTFUT
                      :cash              com.ib.client.Types$SecType/CASH
                      :bond              com.ib.client.Types$SecType/BOND
                      :cfd               com.ib.client.Types$SecType/CFD
                      :future-option     com.ib.client.Types$SecType/FOP
                      :warrant           com.ib.client.Types$SecType/WAR
                      :iopt              com.ib.client.Types$SecType/IOPT
                      :fwd               com.ib.client.Types$SecType/FWD
                      :bag               com.ib.client.Types$SecType/BAG
                      :index             com.ib.client.Types$SecType/IND
                      :bill              com.ib.client.Types$SecType/BILL
                      :fund              com.ib.client.Types$SecType/FUND
                      :fixed             com.ib.client.Types$SecType/FIXED
                      :slb               com.ib.client.Types$SecType/SLB
                      :news              com.ib.client.Types$SecType/NEWS
                      :commodity         com.ib.client.Types$SecType/CMDTY
                      :bsk               com.ib.client.Types$SecType/BSK
                      :icu               com.ib.client.Types$SecType/ICU
                      :ics               com.ib.client.Types$SecType/ICS}
                     (if (>= (read-string (clojure.string/replace (subs tws-version 0 5) "." "")) 1010)
                       {:crypto            (eval (symbol "com.ib.client.Types$SecType/ICS"))}
                       {})
                     ))

(defmethod translate [:to-ib :bar-size-unit] [_ _ unit]
  (case unit
    :second "secs"
    :seconds "secs"
    :minute "min"
    :minutes "mins"
    :hour "hour"
    :hours "hours"
    :day "day"
    :days "days"
    :week "W"
    :month "M"
    ))

(defmethod translate [:from-ib :bar-size-unit] [_ _ unit]
  (case unit
    "sec" :second
    "secs" :seconds
    "min" :minute
    "mins" :minutes
    "hour" :hour
    "hours" :hours
    "day" :day
    "days" :days))

(translation-table what-to-show
                   ;in practice we have to stringify these - odd behaviour
                   {:trades                     com.ib.client.Types$WhatToShow/TRADES
                    :midpoint                   com.ib.client.Types$WhatToShow/MIDPOINT
                    :bid                        com.ib.client.Types$WhatToShow/BID
                    :ask                        com.ib.client.Types$WhatToShow/ASK
                    ;below doesn't work for real-time bars
                    :bid-ask                    com.ib.client.Types$WhatToShow/BID_ASK
                    :historical-volatility      com.ib.client.Types$WhatToShow/HISTORICAL_VOLATILITY
                    :option-implied-volatility  com.ib.client.Types$WhatToShow/OPTION_IMPLIED_VOLATILITY
                    :yield-ask                  com.ib.client.Types$WhatToShow/YIELD_ASK
                    :yield-bid                  com.ib.client.Types$WhatToShow/YIELD_BID
                    :yield-bid-ask              com.ib.client.Types$WhatToShow/YIELD_BID_ASK
                    :yield-last                 com.ib.client.Types$WhatToShow/YIELD_LAST
                    :adjusted-last              com.ib.client.Types$WhatToShow/ADJUSTED_LAST
                    })


(translation-table time-in-force
                   {:day                  com.ib.client.Types$TimeInForce/DAY
                    :good-to-close        com.ib.client.Types$TimeInForce/GTC
                    :immediate-or-cancel  com.ib.client.Types$TimeInForce/IOC
                    :good-till-date       com.ib.client.Types$TimeInForce/GTD
                    :on-open              com.ib.client.Types$TimeInForce/OPG
                    :fill-or-kill         com.ib.client.Types$TimeInForce/FOK
                    :day-till-cancelled   com.ib.client.Types$TimeInForce/FOK
                    :gtt                  com.ib.client.Types$TimeInForce/GTT
                    :auc                  com.ib.client.Types$TimeInForce/AUC
                    :gtx                  com.ib.client.Types$TimeInForce/GTX
                    :dtc                  com.ib.client.Types$TimeInForce/DTC
                    })

(translation-table order-action
                   {:buy        com.ib.client.Types$Action/BUY
                    :sell       com.ib.client.Types$Action/SELL
                    :sell-short com.ib.client.Types$Action/SSHORT})




; POSSIBLE THAT THIS IF MESS-UP AND STRINGS WAS ENOUGH
(translation-table order-type
                   {:none                             com.ib.client.OrderType/None
                    :box-top                          com.ib.client.OrderType/BOX_TOP
                    :limit                            com.ib.client.OrderType/LMT
                    :limit-on-close                   com.ib.client.OrderType/LOC
                    :limit-if-touched                 com.ib.client.OrderType/LIT
                    :market                           com.ib.client.OrderType/MKT
                    :market-on-close                  com.ib.client.OrderType/MOC
                    :market-to-limit                  com.ib.client.OrderType/MTL
                    :market-with-protection           com.ib.client.OrderType/MKT_PRT
                    :market-if-touched                com.ib.client.OrderType/MIT
                    :pegged-to-market                 com.ib.client.OrderType/PEG_MKT
                    :pegged-to-midpoint               com.ib.client.OrderType/PEG_MID
                    :relative                         com.ib.client.OrderType/REL
                    :stop                             com.ib.client.OrderType/STP
                    :stop-limit                       com.ib.client.OrderType/STP_LMT
                    :trail                            com.ib.client.OrderType/TRAIL
                    :trail-limit                      com.ib.client.OrderType/TRAIL_LIMIT
                    :trailing-limit-if-touched        com.ib.client.OrderType/TRAIL_LIT
                    :trailing-market-if-touched       com.ib.client.OrderType/TRAIL_MIT
                    :vwap                             com.ib.client.OrderType/VWAP
                    :volatility                       com.ib.client.OrderType/VOL
                    :fix-pegged                       com.ib.client.OrderType/FIX_PEGGED
                    :limit-plus-market                com.ib.client.OrderType/LMT_PLUS_MKT
                    :passive-relative                 com.ib.client.OrderType/PASSV_REL
                    :pegged-to-benchmark              com.ib.client.OrderType/PEG_BENCH
                    :pegged-to-primary                com.ib.client.OrderType/PEG_PRIM
                    :pegged-to-stock                  com.ib.client.OrderType/PEG_STK
                    :relative-plus-limit              com.ib.client.OrderType/REL_PLUS_LMT
                    :relative-plus-market             com.ib.client.OrderType/REL_PLUS_MKT
                    :snap-to-midpoint                 com.ib.client.OrderType/SNAP_MID
                    :snap-to-market                   com.ib.client.OrderType/SNAP_MKT
                    :snap-to-primary                  com.ib.client.OrderType/SNAP_PRIM
                    :stop-with-protection             com.ib.client.OrderType/STP_PRT
                    :trail-limit-plus-market          com.ib.client.OrderType/TRAIL_LMT_PLUS_MKT
                    :trail-relative-plus-market       com.ib.client.OrderType/TRAIL_REL_PLUS_MKT
                    :quote                            com.ib.client.OrderType/QUOTE
                    :pegged-to-primary-vol            com.ib.client.OrderType/PEG_PRIM_VOL
                    :pegged-to-mid-vol                com.ib.client.OrderType/PEG_MID_VOL
                    :pegged-to-market-vol             com.ib.client.OrderType/PEG_MKT_VOL
                    :pegged-to-srf-vol                com.ib.client.OrderType/PEG_SRF_VOL

                    ;:ACTIVETIM "ACTIVETIM"
                    ;:ADJUST "ADJUST"
                    ;:ALERT "ALERT"
                    ;:ALGO "ALGO"
                    ;:ALGOLTH "ALGOLTH"
                    ;:ALLOC "ALLOC"
                    ;:AON "AON"
                    ;:AUC "AUC"
                    ;:average-cost "AVGCOST"
                    ;:basket "BASKET"
                    ;:BOARDLOT "BOARDLOT"
                    ;:COND "COND"
                    ;:CONDORDER "CONDORDER"
                    ;:CONSCOST "CONSCOST"
                    ;:DARKPOLL "DARKPOLL"
                    ;:DAY "DAY"
                    ;:DEACT "DEACT"
                    ;:DEACTDIS "DEACTDIS"
                    ;:DEACTEOD "DEACTEOD"
                    ;:DIS "DIS"
                    ;:EVRULE "EVRULE"
                    ;:FOK "FOK"
                    ;:good-after-time "GAT"
                    ;:good-till-date "GTD"
                    ;:good-till-canceled "GTC"
                    ;:GTT "GTT"
                    ;:HID "HID"
                    ;:ICE "ICE"
                    ;:IMB "IMB"
                    ;:immediate-or-cancel "IOC"
                    ;:limit-close "LMTCLS"
                    ;:limit-on-open "LOO"
                    ;:LTH "LTH"
                    ;:market-close "MKTCLS"
                    ;:market-on-open "MOO"
                    ;:NONALGO "NONALGO"
                    ;:one-cancels-all "OCA"
                    ;:OPG "OPG"
                    ;:OPGREROUT "OPGREROUT"
                    ;:POSTONLY "POSTONLY"
                    ;:PREOPGRTH "PREOPGRTH"
                    ;:request-for-quote "QUOTE"
                    ;:RTH "RTH"
                    ;:RTHIGNOPG "RTHIGNOPG"
                    ;:scale "SCALE"
                    ;:SCALERST "SCALERST"
                    ;:SWEEP "SWEEP"
                    ;:TIMEPRIO "TIMEPRIO"
                    ;:trailing-stop "TRAIL"
                    ;:trailing-stop-limit "TRAILLMT"
                    ;:what-if "WHATIF"
                    })

;
;(translation-table order-status
;                   {
;                    :pending-submit "PendingSubmit"
;                    :pending-cancel "PendingCancel"
;                    :pre-submitted "PreSubmitted"
;                    :submitted "Submitted"
;                    :cancelled "Cancelled"
;                    :filled "Filled"
;                    :inactive "Inactive"
;                    :api-pending "ApiPending"
;                    :api-cancelled "ApiCancelled"
;                    :unknown "Unknown"
;                    })

; ORDER STATUS IS STILL SENT AS A STRING IN EWRAPPER - NO NEED FOR THIS FOR NOW
(translation-table order-status
                   {
                    :pending-submit     com.ib.client.OrderStatus/PendingSubmit
                    :pending-cancel     com.ib.client.OrderStatus/PendingCancel
                    :pre-submitted      com.ib.client.OrderStatus/PreSubmitted
                    :submitted          com.ib.client.OrderStatus/Submitted
                    :cancelled          com.ib.client.OrderStatus/Cancelled
                    :filled             com.ib.client.OrderStatus/Filled
                    :inactive           com.ib.client.OrderStatus/Inactive
                    :api-pending        com.ib.client.OrderStatus/ApiPending
                    :api-cancelled      com.ib.client.OrderStatus/ApiCancelled
                    :unknown            com.ib.client.OrderStatus/Unknown
                    })


(translation-table security-id-type
                   {:none   com.ib.client.Types$SecIdType/None
                    :isin   com.ib.client.Types$SecIdType/ISIN
                    :cusip  com.ib.client.Types$SecIdType/CUSIP
                    :sedol  com.ib.client.Types$SecIdType/SEDOL
                    :ric    com.ib.client.Types$SecIdType/RIC})

(translation-table tick-field-code
                   {:bid-size                     0
                    :bid-price                    1
                    :ask-price                    2
                    :ask-size                     3
                    :last-price                   4
                    :last-size                    5
                    :high                         6
                    :low                          7
                    :volume                       8
                    :close                        9
                    :bid-option-computation       10
                    :ask-option-computation       11
                    :last-option-computation      12
                    :model-option-computation     13
                    :open                         14
                    :low-13-week                  15
                    :high-13-week                 16
                    :low-26-week                  17
                    :high-26-week                 18
                    :low-52-week                  19
                    :high-52-week                 20
                    :avg-volume                   21
                    :open-interest                22
                    :option-historical-volatility 23
                    :option-implied-volatility    24
                    :option-bid-exchange          25
                    :option-ask-exchange          26
                    :option-call-open-interest    27
                    :option-put-open-interest     28
                    :option-call-volume           29
                    :option-put-volume            30
                    :index-future-premium         31
                    :bid-exchange                 32
                    :ask-exchange                 33
                    :auction-volume               34
                    :auction-price                35
                    :auction-imbalance            36
                    :mark-price                   37
                    :bid-efp-computation          38
                    :ask-efp-computation          39
                    :last-efp-computation         40
                    :open-efp-computation         41
                    :high-efp-computation         42
                    :low-efp-computation          43
                    :close-efp-computation        44
                    :last-timestamp               45
                    :shortable                    46
                    :fundamental-ratios           47
                    :realtime-volume              48
                    :halted                       49
                    :bid-yield                    50
                    :ask-yield                    51
                    :last-yield                   52
                    :cust-option-computation      53
                    :trade-count                  54
                    :trade-rate                   55
                    :volume-rate                  56
                    :last-rth-trade               57
                    :rt-historical-vol            58
                    :ib-dividends                 59
                    :bond-factor-multiplier       60
                    :regulatory-imbalance         61
                    :news-tick                    62
                    :short-term-volume-3-min      63
                    :short-term-volume-5-min      64
                    :short-term-volume-10-min     65
                    :delayed-bid                  66
                    :delayed-ask                  67
                    :delayed-last                 68
                    :delayed-bid-size             69
                    :delayed-ask-size             70
                    :delayed-last-size            71
                    :delayed-high                 72
                    :delayed-low                  73
                    :delayed-volume               74
                    :delayed-close                75
                    :delayed-open                 76
                    :rt-trd-volume                77
                    :creditman-mark-price         78
                    :creditman-slow-mark-price    79
                    :delayed-bid-option           80
                    :delayed-ask-option           81
                    :delayed-last-option          82
                    :delayed-model-option         83
                    :last-exch                    84
                    :last-reg-time                85
                    :futures-open-interest        86
                    :avg-opt-volume               87
                    :delayed-last-timestamp       88
                    :shortable-shares             89
                    :delayed-halted               90
                    :reuters-2-mutual-funds       91
                    :etf-nav-close                92
                    :etf-nav-bid                  94
                    :etf-nav-ask                  95
                    :etf-nav-last                 96
                    :etf-frozen-nav-last          97
                    :etf-nav-high                 98
                    :etf-nav-low                  99
                    }
                   )

(translation-table generic-tick-type
                   {
                    :option-volume                  100     ; :option-call-volume, :option-put-volume
                    :option-open-interest           101     ; :option-call-open-interest, :option-put-open-interest
                    :historical-volatility          104     ; :option-historical-volatility
                    :option-implied-volatility      106     ; :option-implied-volatility
                    :index-future-premium           162     ; :index-future-premium
                    :miscellaneous-stats            165     ; :low-13-week, :high-13-week, :low-26-week, :high-26-week, :low-52-week, :high-52-week, :avg-volume 21
                    :mark-price                     221     ; :mark-price
                    :auction-values                 225     ; :auction-volume, :auction-price, :auction-imbalance
                    :realtime-volume                233     ; :realtime-volume
                    :shortable                      236     ; :shortable
                    :inventory                      256     ;
                    :fundamental-ratios             258     ; :fundamental-ratios
                    :realtime-historical-volatility 411     ; 58?
                    :short-term-volume              595
                    })

(translation-table log-level
                   {:system        1
                    :error         2
                    :warning       3
                    :informational 4
                    :detail        5})

(defmethod translate [:to-ib :tick-list] [_ _ val]
  (->> val
       (map #(cond
               (valid? :to-ib :tick-field-code %)
               (translate :to-ib :tick-field-code %)

               (valid? :to-ib :generic-tick-type %)
               (translate :to-ib :generic-tick-type %)

               :else %))
       (map str)
       (clojure.string/join ",")))

(translation-table fundamental-ratio
                   {
                    :closing-price                      "NPRICE"
                    :3-year-ttm-growth                  "Three_Year_TTM_Growth"
                    :ttm-over-ttm                       "TTM_over_TTM"
                    :12-month-high                      "NHIG"
                    :12-month-low                       "NLOW"
                    :pricing-date                       "PDATE"
                    :10-day-average-volume              "VOL10DAVG"
                    :market-cap                         "MKTCAP"
                    :eps-exclusing-extraordinary-items  "TTMEPSXCLX"
                    :eps-normalized                     "AEPSNORM"
                    :revenue-per-share                  "TTMREVPS"
                    :common-equity-book-value-per-share "QBVPS"
                    :tangible-book-value-per-share      "QTANBVPS"
                    :cash-per-share                     "QCSHPS"
                    :cash-flow-per-share                "TTMCFSHR"
                    :dividends-per-share                "TTMDIVSHR"
                    :dividend-rate                      "IAD"
                    :pe-excluding-extraordinary-items   "PEEXCLXOR"
                    :pe-normalized                      "APENORM"
                    :price-to-sales                     "TMPR2REV"
                    :price-to-tangible-book             "PR2TANBK"
                    :price-to-cash-flow-per-share       "TTMPRCFPS"
                    :price-to-book                      "PRICE2BK"
                    :current-ration                     "QCURRATIO"
                    :quick-ratio                        "QQUICKRATI"
                    :long-term-debt-to-equity           "QLTD2EQ"
                    :total-debt-to-equity               "QTOTD2EQ"
                    :payout-ratio                       "TTMPAYRAT"
                    :revenue                            "TTMREV"
                    :ebita                              "TTMEBITD"
                    :ebt                                "TTMEBT"
                    :niac                               "TTMNIAC"
                    :ebt-normalized                     "AEBTNORM"
                    :niac-normalized                    "ANIACNORM"
                    :gross-margin                       "TTMGROSMGN"
                    :net-profit-margin                  "TTMNPMGN"
                    :operating-margin                   "TTMOPMGN"
                    :pretax-margin                      "APTMGNPCT"
                    :return-on-average-assets           "TTMROAPCT"
                    :return-on-average-equity           "TTMROEPCT"
                    :roi                                "TTMROIPCT"
                    :revenue-change                     "REVCHNGYR"
                    :revenue-change-ttm                 "TTMREVCHG"
                    :revenue-growth                     "REVTRENDGR"
                    :eps-change                         "EPSCHNGYR"
                    :eps-change-ttm                     "TTMEPSCHG"
                    :eps-growth                         "EPSTRENDGR"
                    :dividend-growth                    "DIVGRPCT"})

(translation-table account-value-key
                   {
                    :account-code                                          "AccountCode"
                    :account-ready                                         "AccountReady"
                    :account-type                                          "AccountType"
                    :account-or-group                                      "AccountOrGroup"
                    :accrued-cash                                          "AccruedCash"
                    :accrued-cash-commodities                              "AccruedCash-C"
                    :accrued-cash-stock                                    "AccruedCash-S"
                    :accrued-cash-regulated                                "AccruedCash-F"
                    :accrued-dividend                                      "AccruedDividend"
                    :accrued-dividend-commodities                          "AccruedDividend-C"
                    :accrued-dividend-stock                                "AccruedDividend-S"
                    :accrued-dividend-regulated                            "AccruedDividend-F"
                    :available-funds                                       "AvailableFunds"
                    :available-funds-commodities                           "AvailableFunds-C"
                    :available-funds-stock                                 "AvailableFunds-S"
                    :available-funds-regulated                             "AvailableFunds-F"
                    :billable                                              "Billable"
                    :billable-commodities                                  "Billable-C"
                    :billable-stock                                        "Billable-S"
                    :billable-regulated                                    "Billable-F"
                    :buying-power                                          "BuyingPower"
                    :cash-balance                                          "CashBalance"
                    :column-prio-commodities                               "ColumnPrio-C"
                    :column-prio-stock                                     "ColumnPrio-S"
                    :column-prio-regulated                                 "ColumnPrio-F"
                    :corporate-bond-value                                  "CorporateBondValue"
                    :currency                                              "Currency"
                    :cushion                                               "Cushion"
                    :day-trades-remaining                                  "DayTradesRemaining"
                    :day-trades-remaining-T+1                              "DayTradesRemainingT+1"
                    :day-trades-remaining-T+2                              "DayTradesRemainingT+2"
                    :day-trades-remaining-T+3                              "DayTradesRemainingT+3"
                    :day-trades-remaining-T+4                              "DayTradesRemainingT+4"
                    :equity-with-loan-value                                "EquityWithLoanValue"
                    :equity-with-loan-value-commodities                    "EquityWithLoanValue-C"
                    :equity-with-loan-value-stock                          "EquityWithLoanValue-S"
                    :equity-with-loan-value-regulated                      "EquityWithLoanValue-F"
                    :excess-liquidity                                      "ExcessLiquidity"
                    :excess-liquidity-commodities                          "ExcessLiquidity-C"
                    :excess-liquidity-stock                                "ExcessLiquidity-S"
                    :excess-liquidity-regulated                            "ExcessLiquidity-F"
                    :exchange-rate                                         "ExchangeRate"
                    :full-available-funds                                  "FullAvailableFunds"
                    :full-available-funds-commodities                      "FullAvailableFunds-C"
                    :full-available-funds-stock                            "FullAvailableFunds-S"
                    :full-available-funds-regulated                        "FullAvailableFunds-F"
                    :full-excess-liquidity                                 "FullExcessLiquidity"
                    :full-excess-liquidity-commodities                     "FullExcessLiquidity-C"
                    :full-excess-liquidity-stock                           "FullExcessLiquidity-S"
                    :full-excess-liquidity-regulated                       "FullExcessLiquidity-F"
                    :full-initial-margin-requirement                       "FullInitMarginReq"
                    :full-initial-margin-requirement-commodities           "FullInitMarginReq-C"
                    :full-initial-margin-requirement-stock                 "FullInitMarginReq-S"
                    :full-initial-margin-requirement-regulated             "FullInitMarginReq-F"
                    :full-maintenance-margin-requirement                   "FullMaintMarginReq"
                    :full-maintenance-margin-requirement-commodities       "FullMaintMarginReq-C"
                    :full-maintenance-margin-requirement-stock             "FullMaintMarginReq-S"
                    :full-maintenance-margin-requirement-regulated         "FullMaintMarginReq-F"
                    :fund-value                                            "FundValue"
                    :future-option-value                                   "FutureOptionValue"
                    :futures-profit-loss                                   "FuturesPNL"
                    :fx-cash-balance                                       "FxCashBalance"
                    :gross-position-value                                  "GrossPositionValue"
                    :gross-position-value-stock                            "GrossPositionValue-S"
                    :gross-position-value-regulated                        "GrossPositionValue-F"
                    :guarantee                                             "Guarantee"
                    :guarantee-commodities                                 "Guarantee-C"
                    :guarantee-stock                                       "Guarantee-S"
                    :guarantee-regulated                                   "Guarantee-F"
                    :highest-severity                                      "HighestSeverity"
                    :indian-stock-haircut                                  "IndianStockHaircut"
                    :indian-stock-haircut-commodities                      "IndianStockHaircut-C"
                    :indian-stock-haircut-stock                            "IndianStockHaircut-S"
                    :indian-stock-haircut-regulated                        "IndianStockHaircut-F"
                    :initial-margin-requirement                            "InitMarginReq"
                    :initial-margin-requirement-commodities                "InitMarginReq-C"
                    :initial-margin-requirement-stock                      "InitMarginReq-S"
                    :initial-margin-requirement-regulated                  "InitMarginReq-F"
                    :issuer-option-value                                   "IssuerOptionValue"
                    :leverage                                              "Leverage"
                    :leverage-stock                                        "Leverage-S"
                    :leverage-regulated                                    "Leverage-F"
                    :look-ahead-available-funds                            "LookAheadAvailableFunds"
                    :look-ahead-available-funds-commodities                "LookAheadAvailableFunds-C"
                    :look-ahead-available-funds-stock                      "LookAheadAvailableFunds-S"
                    :look-ahead-available-funds-regulated                  "LookAheadAvailableFunds-F"
                    :look-ahead-excess-liquidity                           "LookAheadExcessLiquidity"
                    :look-ahead-excess-liquidity-commodities               "LookAheadExcessLiquidity-C"
                    :look-ahead-excess-liquidity-stock                     "LookAheadExcessLiquidity-S"
                    :look-ahead-excess-liquidity-regulated                 "LookAheadExcessLiquidity-F"
                    :look-ahead-initial-margin-requirement                 "LookAheadInitMarginReq"
                    :look-ahead-initial-margin-requirement-commodities     "LookAheadInitMarginReq-C"
                    :look-ahead-initial-margin-requirement-stock           "LookAheadInitMarginReq-S"
                    :look-ahead-initial-margin-requirement-regulated       "LookAheadInitMarginReq-F"
                    :look-ahead-maintenance-margin-requirement             "LookAheadMaintMarginReq"
                    :look-ahead-maintenance-margin-requirement-commodities "LookAheadMaintMarginReq-C"
                    :look-ahead-maintenance-margin-requirement-stock       "LookAheadMaintMarginReq-S"
                    :look-ahead-maintenance-margin-requirement-regulated   "LookAheadMaintMarginReq-F"
                    :look-ahead-next-change                                "LookAheadNextChange"
                    :maintenance-margin-requirement                        "MaintMarginReq"
                    :maintenance-margin-requirement-commodities            "MaintMarginReq-C"
                    :maintenance-margin-requirement-stock                  "MaintMarginReq-S"
                    :maintenance-margin-requirement-regulated              "MaintMarginReq-F"
                    :money-market-fund-value                               "MoneyMarketFundValue"
                    :mutual-fund-value                                     "MutualFundValue"
                    :net-dividend                                          "NetDividend"
                    :net-liquidation                                       "NetLiquidation"
                    :net-liquidation-commodities                           "NetLiquidation-C"
                    :net-liquidation-stock                                 "NetLiquidation-S"
                    :net-liquidation-regulated                             "NetLiquidation-F"
                    :net-liquidation-by-currency                           "NetLiquidationByCurrency"
                    :net-liquidation-uncertainty                           "NetLiquidationUncertainty"
                    :net-liquidation-value-and-margin-in-review            "NLVAndMarginInReview"
                    :option-market-value                                   "OptionMarketValue"
                    :pa-shares-value                                       "PASharesValue"
                    :pa-shares-value-commodities                           "PASharesValue-C"
                    :pa-shares-value-stock                                 "PASharesValue-S"
                    :pa-shares-value-regulated                             "PASharesValue-F"
                    :physical-certificate-value                            "PhysicalCertificateValue"
                    :physical-certificate-value-commodities                "PhysicalCertificateValue-C"
                    :physical-certificate-value-stock                      "PhysicalCertificateValue-S"
                    :physical-certificate-value-regulated                  "PhysicalCertificateValue-F"
                    :post-expiration-excess                                "PostExpirationExcess"
                    :post-expiration-excess-commodities                    "PostExpirationExcess-C"
                    :post-expiration-excess-stock                          "PostExpirationExcess-S"
                    :post-expiration-excess-regulated                      "PostExpirationExcess-F"
                    :post-expiration-margin                                "PostExpirationMargin"
                    :post-expiration-margin-commodities                    "PostExpirationMargin-C"
                    :post-expiration-margin-stock                          "PostExpirationMargin-S"
                    :post-expiration-margin-regulated                      "PostExpirationMargin-F"
                    :profit-loss                                           "PNL"
                    :previous-day-equity-with-loan-value                   "PreviousDayEquityWithLoanValue"
                    :previous-day-equity-with-loan-value-stock             "PreviousDayEquityWithLoanValue-S"
                    :real-currency                                         "RealCurrency"
                    :realized-profit-loss                                  "RealizedPnL"
                    :regulation-T-equity                                   "RegTEquity"
                    :regulation-T-equity-stock                             "RegTEquity-S"
                    :regulation-T-margin                                   "RegTMargin"
                    :regulation-T-margin-stock                             "RegTMargin-S"
                    :segment-title-commodities                             "SegmentTitle-C"
                    :segment-title-stock                                   "SegmentTitle-S"
                    :segment-title-regulated                               "SegmentTitle-F"
                    :settled-cash                                          "SettledCash"
                    :sma                                                   "SMA"
                    :sma-stock                                             "SMA-S"
                    :stock-market-value                                    "StockMarketValue"
                    :t-bill-value                                          "TBillValue"
                    :t-bond-value                                          "TBondValue"
                    :total-cash-balance                                    "TotalCashBalance"
                    :total-cash-value                                      "TotalCashValue"
                    :total-cash-value-commodities                          "TotalCashValue-C"
                    :total-cash-value-stock                                "TotalCashValue-S"
                    :total-cash-value-regulated                            "TotalCashValue-F"
                    :total-debit-card-pending-charges                      "TotalDebitCardPendingCharges"
                    :total-debit-card-pending-charges-commodities          "TotalDebitCardPendingCharges-C"
                    :total-debit-card-pending-charges-stock                "TotalDebitCardPendingCharges-S"
                    :total-debit-card-pending-charges-regulated            "TotalDebitCardPendingCharges-F"
                    :trading-type-stock                                    "TradingType-S"
                    :trading-type-regulated                                "TradingType-F"
                    :unaltered-initial-margin-requirement                  "UnalteredInitMarginReq"
                    :unaltered-maintenance-margin-requirement              "UnalteredMaintMarginReq"
                    :unrealized-profit-loss                                "UnrealizedPnL"
                    :warrants-value                                        "WarrantValue"
                    :what-if-portfolio-margin-enabled                      "WhatIfPMEnabled"
                    })

(defn numeric-account-value? [key]
  (contains? #{:accrued-cash :accrued-cash-commodities :accrued-cash-stock :accrued-cash-regulated
               :accrued-dividend :accrued-dividend-commodities :accrued-dividend-stock :accrued-dividend-regulated
               :available-funds :available-funds-commodities :available-funds-stock :available-funds-regulated
               :billable :billable-commodities :billable-stock :billable-regulated
               :buying-power :cash-balance :corporate-bond-value :cushion
               :equity-with-loan-value :equity-with-loan-value-commodities :equity-with-loan-value-stock :equity-with-loan-value-regulated
               :excess-liquidity :excess-liquidity-commodities :excess-liquidity-stock :excess-liquidity-regulated
               :exchange-rate
               :full-available-funds :full-available-funds-commodities :full-available-funds-stock :full-available-funds-regulated
               :full-excess-liquidity :full-excess-liquidity-commodities :full-excess-liquidity-stock :full-excess-liquidity-regulated
               :full-initial-margin-requirement :full-initial-margin-requirement-commodities :full-initial-margin-requirement-stock :full-initial-margin-requirement-regulated
               :full-maintenance-margin-requirement :full-maintenance-margin-requirement-commodities :full-maintenance-margin-requirement-stock :full-maintenance-margin-requirement-regulated
               :fund-value :future-option-value :futures-profit-loss :fx-cash-balance
               :gross-position-value :gross-position-values-commodities :gross-position-value-stock :gross-position-value-regulated
               :guarantee :guarantee-commodities :guarantee-stock :guarantee-regulated
               :indian-stock-haircut :indian-stock-haircut-commodities :indian-stock-haircut-stock :indian-stock-haircut-regulated
               :initial-margin-requirement :initial-margin-requirement-commodities :initial-margin-requirement-stock :initial-margin-requirement-regulated
               :leverage :leverage-commodities :leverage-stock :leverage-regulated
               :look-ahead-available-funds :look-ahead-available-funds-commodities :look-ahead-available-funds-stock :look-ahead-available-funds-regulated
               :look-ahead-excess-liquidity :look-ahead-excess-liquidity-commodities :look-ahead-excess-liquidity-stock :look-ahead-excess-liquidity-regulated
               :look-ahead-initial-margin-requirement :look-ahead-initial-margin-requirement-commodities :look-ahead-initial-margin-requirement-stock :look-ahead-initial-margin-requirement-regulated
               :look-ahead-maintenance-margin-requirement :look-ahead-maintenance-margin-requirement-commodities :look-ahead-maintenance-margin-requirement-stock :look-ahead-maintenance-margin-requirement-regulated
               :look-ahead-next-change
               :maintenance-margin-requirement :maintenance-margin-requirement-commodities :maintenance-margin-requirement-stock :maintenance-margin-requirement-regulated
               :money-market-fund-value :mutual-fund-value :net-dividend
               :net-liquidation :net-liquidation-commodities :net-liquidation-stock :net-liquidation-regulated
               :net-liquidation-by-currency :option-market-value
               :pa-shares-value :pa-shares-value-commodities :pa-shares-value-stock :pa-shares-value-regulated
               :physical-certificate-value :physical-certificate-value-commodities :physical-certificate-value-stock :physical-certificate-value-regulated
               :post-expiration-margin
               :post-expiration-margin-commodities :post-expiration-margin-stock :post-expiration-margin-regulated
               :post-expiration-excess
               :post-expiration-excess-commodities :post-expiration-excess-stock :post-expiration-excess-regulated
               :previous-day-equity-with-loan-value :previous-day-equity-with-loan-value-commodities :previous-day-equity-with-loan-value-stock
               :realized-profit-loss
               :regulation-T-equity :regulation-T-equity-commodities :regulation-T-equity-stock
               :regulation-T-margin :regulation-T-margin-commodities :regulation-T-margin-stock
               :sma :sma-commodities :sma-stock
               :stock-market-value :t-bill-value :t-bond-value :total-cash-balance
               :total-cash-value :total-cash-value-commodities :total-cash-value-stock :total-cash-value-regulated
               :total-debit-card-pending-charges :total-debit-card-pending-charges-commodities :total-debit-card-pending-charges-stock :total-debit-card-pending-charges-regulated
               :unaltered-initial-margin-requirement :unaltered-maintenance-margin-requirement
               :unrealized-profit-loss :warrants-value
               } key))

(defn integer-account-value? [key]
  (contains? #{:day-trades-remaining :day-trades-remaining-T+1 :day-trades-remaining-T+2
               :day-trades-remaining-T+3 :day-trades-remaining-T+4
               } key))

(defn boolean-account-value? [key]
  (contains? #{:account-ready :profit-loss :what-if-portfolio-margin-enabled} key))

(translation-table market-depth-row-operation
                   {
                    :insert 0
                    :update 1
                    :delete 2
                    })

(translation-table market-depth-side
                   {
                    :ask 0
                    :bid 1
                    })

(translation-table report-type
                   {:company-overview     "ReportSnapshot"
                    :financial-summary    "ReportsFinSummary"
                    :financial-ratios     "ReportRatios"
                    :financial-statements "ReportsFinStatements"
                    :analyst-estimates    "RESC"
                    :company-calendar     "CalendarReport"
                    })

(translation-table rule-80A
                   {:individual              "I"
                    :agency                  "A"
                    :agent-other-member      "W"
                    :individual-PTIA         "J"
                    :agency-PTIA             "U"
                    :agent-other-member-PTIA "M"
                    :individual-PT           "K"
                    :agency-PT               "Y"
                    :agent-other-member-PT   "N"})

(translation-table market-data-type
                   {:real-time-streaming 1
                    :frozen              2
                    :delayed             3
                    :delayed-frozen      4})

(translation-table boolean-int
                   {true 1
                    false 0})

(translation-table execution-side
                   {:buy "BOT"
                    :sell "SLD"})

(translation-table financial-advisor-data-type
                   {:financial-advisor-groups 1
                    :financial-advisor-profile 2
                    :financial-advisor-account-aliases 3})

(translation-table right
                   {:put  com.ib.client.Types$Right/Put
                    :call com.ib.client.Types$Right/Call
                    :none com.ib.client.Types$Right/None})

;(translation-table right
;                   {:put "PUT",
;                    :call "CALL",
;                    :none "0",
;                    :unknown "?"}
;                   {"PUT" :put
;                    "P" :put
;                    "CALL" :call
;                    "C" :call
;                    "0" :none
;                    "?" :unknown})



(defmethod translate [:to-ib :duration] [_ _ [val unit]]
  (str val " " (translate :to-ib :duration-unit unit)))

(defmethod translate [:from-ib :duration] [_ _ val]
  (when val
    (let [[amount unit] (.split val " ")]
      (vector (Integer/parseInt amount)
              (translate :from-ib :duration-unit unit)))))

;(defmethod translate [:from-ib :date-time] [_ _ val]
;  (condp instance? val
;    java.util.Date (tc/from-date val)
;    String (translate :from-ib :date-time (Long/parseLong val))
;    Long (tc/from-long (* 1000 val))))
;
;(defmethod translate [:to-ib :date-time] [_ _ value]
;  (when val
;    (-> (tf/formatter "yyyyMMdd HH:mm:ss")
;        (tf/unparse value)
;        (str " UTC"))))
;
;(defmethod translate [:to-ib :timestamp] [_ _ val]
;  (condp instance? val
;    java.util.Date (tc/from-date val)
;    org.joda.time.DateTime (translate :to-ib :timestamp
;                                      (-> (tf/formatter "yyyyMMdd-HH:mm:ss")
;                                          (tf/unparse val)
;                                          (str " UTC")))
;    String val))
;
;(defmethod translate [:from-ib :timestamp] [_ _ val]
;
;  (cond
;   (nil? val) nil
;
;   (= (.length val) 8)
;   (tf/parse (tf/formatter "yyyyMMdd") val)
;
;   (every? #(Character/isDigit %) val)
;   (tc/from-long (* (Long/parseLong val) 1000))
;
;   (= (.length val) 17)
;   (tf/parse (tf/formatter "yyyyMMdd-HH:mm:ss") val)
;
;   :else val))
;
;(defmethod translate [:from-ib :time-zone] [_ _ val]
;  (case val
;    "GMT" "+0000"
;    "EST" "-0500"
;    "MST" "-0700"
;    "PST" "-0800"
;    "AST" "-0400"
;    "JST" "+0900"
;    "AET" "+1000"))
;
;(defmethod translate [:from-ib :connection-time] [_ _ val]
;  (when val
;    (let [tokens (vec (.split val " "))
;          timezone-token (get tokens 2)]
;      (when timezone-token
;        (let [timezone-offset (translate :from-ib :time-zone timezone-token)
;              tokens-with-adjusted-timezone (concat (take 2 tokens) [timezone-offset])
;              adjusted-date-time-string (clojure.string/join " " tokens-with-adjusted-timezone)]
;          (tf/parse (tf/formatter "yyyyMMdd HH:mm:ss Z")
;                    adjusted-date-time-string))))))
;
;(defmethod translate [:to-ib :connection-time] [_ _ val]
;  (when val
;    (tf/unparse (tf/formatter "yyyyMMdd HH:mm:ss z") val)))
;
;(defmethod translate [:to-ib :date] [_ _ val]
;  (tf/unparse (tf/formatter "MM/dd/yyyy") val))
;
;(defmethod translate [:from-ib :date] [_ _ val]
;  (when val
;    (try
;      (tf/parse (tf/formatter "MM/dd/yyyy") val)
;      (catch Exception e
;        (throw (ex-info "Failed to translate from IB date value."
;                        {:value val
;                         :expected-form "MM/dd/yyyy"}))))))
;
;;;; FIXME: We should turn time of day into some kind of data structure that does
;;;; no have a date component.
;(defmethod translate [:from-ib :time-of-day] [_ _ val]
;  (when val
;    (try
;      (tf/parse (tf/formatter "HH:mm") val)
;      (catch Exception e
;        (throw (ex-info "Failed to translate from IB time-of-day value."
;                        {:value val
;                         :expected-form "HH:mm"}))))))
;
;(defmethod translate [:to-ib :time-of-day] [_ _ val]
;  (when val
;    (try
;      (tf/unparse (tf/formatter "HH:mm") val)
;      (catch Exception e
;        (throw (ex-info "Failed to translate from IB time-of-day value."
;                        {:value val
;                         :expected-form "HH:mm"}))))))
;
;
;(defmulti expiry-to-ib class)
;
;(defmethod expiry-to-ib org.joda.time.DateTime [time]
;  (tf/unparse (tf/formatter "yyyyMMdd") time))
;
;
;(defmethod expiry-to-ib org.joda.time.LocalDate [date]
;  (tf/unparse-local (tf/formatter-local "yyyyMMdd") date))
;
;
;(defmethod expiry-to-ib org.joda.time.YearMonth [ym]
;  (tf/unparse-local (tf/formatter-local "yyyyMM") ym))
;
;
;(defmethod translate [:to-ib :expiry] [_ _ val]
;  (when val
;    (expiry-to-ib val)))
;
;(defmethod translate [:from-ib :expiry] [_ _ val]
;  (condp = (.length val)
;    6 (org.joda.time.YearMonth.
;       (tf/parse-local-date (tf/formatter "yyyyMM") val))
;    8 (tf/parse-local-date (tf/formatter "yyyyMMdd") val)))

(defmethod translate [:to-ib :bar-size] [_ _ [val unit]]
  (str val " " (translate :to-ib :bar-size-unit unit)))

(defmethod translate [:to-ib :double-string] [_ _ val]
  (str val))

(defmethod translate [:from-ib :double-string] [_ _ val]
  (Double/parseDouble val))

(defmethod translate [:from-ib :order-types] [_ _ val]
  (->> (.split val ",")
       (map (partial translate :from-ib :order-type))))

(defmethod translate [:to-ib :order-types] [_ _ val]
  (str/join "," (map (partial translate :to-ib :order-type) val)))

(defmethod translate [:from-ib :exchanges] [_ _ val]
  (str/split val #","))

(defmethod translate [:to-ib :exchanges] [_ _ val]
  (str/join "," val))

;;This is necessary because the com.ib.client.Decimal doesn't exist in previous versions of twsapi
;;Solution below works. Other solution marginally faster and feels more clojuresque
;(def ^java.lang.reflect.Method val->decimal
;  (when use-decimal?
;    (.getMethod (Class/forName "com.ib.client.Decimal")
;                "parse"
;                (into-array [java.lang.String]))))
;
;;only for 10.10 onwards
;(defmethod translate [:to-ib :string-to-decimal] [_ _ val]
;  ;we coerce val to str, just in case
;  (if val->decimal
;    (.invoke val->decimal nil (into-array [(str val)]))
;    val))

(defmacro if-decimal?
  [if-form else-form]
  (if use-decimal?
    `(do ~if-form)
    `(do ~else-form)))

;only for 10.10 onwards
(defmethod translate [:to-ib :string-to-decimal] [_ _ val]
  ;we coerce val to str, just in case
  (if-decimal? (com.ib.client.Decimal/parse (str val)) val))

(defmethod translate [:from-ib :string-to-decimal] [_ _ val]
  ;we coerce val to str, just in case
  (if-decimal?
    (if (or (number? val) (string? val)) (com.ib.client.Decimal/parse (str val)) val)
    val))

(defmethod translate [:from-ib :decimal-to-long] [_ _ val]
  (if use-decimal? (.longValue val) val))


;(defmethod translate [:from-ib :yield-redemption-date] [_ _ val]
;  (let [year (int (Math/floor (/ val 10000)))
;        month (int (Math/floor (/ (mod val 10000) 100)))
;        day (int (Math/floor (mod 19720427 100)))]
;    (time/date-time year month day)))


;;; -----
;;; ## Deals with the trading hours reporting.  This is really ugly.
;;; IB uses their timezone definitions incorrectly. Correct them here. No, no,
;;; they really do.
;(def ib-timezone-map
;  {"EST" "America/New_York"
;   "CST" "America/Chicago"
;   "CTT" "America/Chicago"
;   "JST" "Asia/Tokyo"
;   "PST" "America/Los_Angeles"})
;
;(defn- to-utc
;  "Returns a full date-time in UTC, referring to a particular time at a
;  particular place. Place must be a TZ string such as America/Chicago. Date will
;  only use the year-month-day fields, the min and second come from the parms."
;  ([place date-time]
;     (to-utc place date-time
;             (time/hour date-time) (time/minute date-time) (time/second date-time)))
;  ([place date hour minute]
;     (to-utc place date hour minute 0))
;  ([place date hour minute second]
;     (let [zone (time/time-zone-for-id (or (ib-timezone-map place) place))]
;       (time/to-time-zone
;        (time/from-time-zone
;         (time/date-time (time/year date) (time/month date) (time/day date) hour minute second)
;         zone)
;        (time/time-zone-for-id "UTC")))))
;
;(defn- th-days
;  "Returns a seq of the days in an interval"
;  [s]
;  (str/split s #";"))
;
;(defn- th-components [s]
;  (str/split s #":"))
;
;;; NB: Closed days are represented as 0 length intervals on that day.
;(defn- th-intervals [[day s]]
;  (if (= s "CLOSED")
;    [[(str day "0000") (str day "0000")]]
;    (map #(mapv (partial str day) (str/split % #"-")) (str/split s #","))))
;
;;; Convert to Joda intervals
;(defn- joda-interval [tz [start end]]
;  (let [start-dt  (to-utc tz (tf/parse (tf/formatter "yyyyMMddHHmm") start))
;        end-dt    (to-utc tz (tf/parse (tf/formatter "yyyyMMddHHmm") end))
;        mod-start (if (time/after? start-dt end-dt)
;                    (time/minus start-dt (time/days 1))
;                    start-dt)]
;    (time/interval mod-start end-dt)))
;
;;;added changed ->> to some->> which apparently is necessary for this to work with
;;;include-expired? contracts, as trading-hours seems to be nil/empty for that
;(defmethod translate [:from-ib :trading-hours] [_ _ [tz t-string]]
;  (some->> t-string
;       th-days
;       (map th-components)
;       (mapcat th-intervals)
;       (map (partial joda-interval tz))))
