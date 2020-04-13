(ns ib-re-actor-976-plus.test.translation
  (:require
   [clj-time.coerce :as c]
   [clj-time.core :refer [date-time interval local-date year-month]]
   [ib-re-actor-976-plus.translation :refer [translate]]
   [midje.sweet :refer [fact tabular throws]]))

(fact "unknown string codes just translate into themselves"
      (fact "coming from IB"
            (translate :from-ib :security-type "some weird value")
            => "some weird value")
      (fact "going out to IB"
            (translate :to-ib :security-type "some weird value")
            => "some weird value"))

(fact "unknown keyword codes throw"
      (translate :to-ib :security-type :I-misspelled-something)
      => (throws #"^Can't translate to IB"))

(tabular
 (fact "it can translate to IB durations"
       (translate :to-ib :duration [?value ?unit]) => ?expected)
  ?value  ?unit     ?expected
  1       :second   "1 S"
  5       :seconds  "5 S"
  1       :day      "1 D"
  5       :days     "5 D"
  1       :week     "1 W"
  5       :weeks    "5 W"
  1       :year     "1 Y"
  5       :years    "5 Y")

(tabular
 (fact "it can translate to IB security codes"
       (translate :to-ib :security-type ?value) => ?expected)
 ?value             ?expected
 :equity            "STK"
 :option            "OPT"
 :future            "FUT"
 :index             "IND"
 :future-option     "FOP"
 :cash              "CASH"
 :bag               "BAG")

(tabular
 (fact "it can translate from IB right"
       (translate :from-ib :right ?value) => ?expected)
 ?value ?expected
 "PUT"  :put
 "P"    :put
 "CALL" :call
 "C"    :call
 "0"    :none
 "?"    :unknown)

(tabular
 (fact "it can translate to IB right"
       (translate :to-ib :right ?value) => ?expected)
 ?value   ?expected
 :put     "PUT"
 :call    "CALL"
 :none    "0"
 :unknown "?")

(tabular
 (fact "it can translate bar sizes"
       (translate :to-ib :bar-size [?value ?unit]) => ?expected)
 ?value  ?unit     ?expected
 1       :second   "1 secs"
 5       :seconds  "5 secs"
 1       :minute   "1 min"
 3       :minutes  "3 mins"
 1       :hour     "1 hour"
 4       :hours    "4 hours"
 1       :day      "1 day"
 2       :days     "2 days"
 1       :week      "1 W"
 1       :month     "1 M")

(tabular
 (fact "it can translate what to show strings"
       (translate :to-ib :what-to-show ?value) => ?expected)
 ?value                      ?expected
 :trades                     "TRADES"
 :midpoint                   "MIDPOINT"
 :bid                        "BID"
 :ask                        "ASK"
 :bid-ask                    "BID_ASK"
 :historical-volatility      "HISTORICAL_VOLATILITY"
 :option-implied-volatility  "OPTION_IMPLIED_VOLATILITY"
 :option-volume              "OPTION_VOLUME"
 :option-open-interest       "OPTION_OPEN_INTEREST")

(fact "it can translate from IB date-time values"
      (translate :from-ib :date-time (long 1000000000)) => (date-time 2001 9 9 1 46 40)
      (translate :from-ib :date-time "1000000000") => (date-time 2001 9 9 1 46 40))

(fact "it can translate date-times to IB expiry strings"
      (translate :to-ib :expiry (year-month 2011 9)) => "201109"
      (translate :to-ib :expiry (date-time 2012 10 20)) => "20121020")

(fact "it can translate from IB expiry strings to joda time classes"
      (translate :from-ib :expiry "201509") => (year-month 2015 9)
      (translate :from-ib :expiry "20140203") => (local-date 2014 02 03))

(tabular
 (fact "it can translate time in force values"
       (translate :to-ib :time-in-force ?value) => ?expected)
 ?value                ?expected
 :day                  "DAY"
 :good-to-close        "GTC"
 :immediate-or-cancel  "IOC"
 :good-till-date       "GTD")

(fact "it can translate date-times to the IB format"
      (translate :to-ib :date-time (date-time 2011)) => "20110101 00:00:00 UTC"
      (translate :to-ib :date-time (date-time 2001 4 1 13 30 29)) => "20010401 13:30:29 UTC")

(tabular
 (fact "it can translate order actions"
       (translate :to-ib :order-action ?action) => ?expected)
 ?action      ?expected
 :buy         "BUY"
 :sell        "SELL"
 :sell-short  "SSHORT")

(tabular
 (fact "it can translate to IB order types"
       (translate :to-ib :order-type ?type) => ?expected)
 ?type   ?expected
 :limit  "LMT")

(tabular
 (fact "it can translate security id types"
              (translate :from-ib :security-id-type ?ib-type) => ?re-actor-type
              (translate :to-ib :security-id-type ?re-actor-type) => ?ib-type)
 ?re-actor-type  ?ib-type
 :isin           "ISIN"
 :cusip          "CUSIP"
 :sedol          "SEDOL"
 :ric            "RIC")

(tabular
 (fact "it can translate tick field codes"
              (translate :from-ib :tick-field-code ?ib-code) => ?re-actor-code
              (translate :to-ib :tick-field-code ?re-actor-code) => ?ib-code)
 ?re-actor-code  ?ib-code
 :bid-size       0
 :bid-price      1
 :ask-price      2
 :ask-size       3)


(tabular
 (fact "It can translate IB trading and liquid hours to joda intervals"
       (translate :from-ib :trading-hours [?tz ?ib-string]) => ?intervals)
 ?tz     ?ib-string  ?intervals
 "America/Belize"    "20130115:1700-1515,1530-1615;20130116:1700-1515,1530-1615"
 [(interval (c/to-date-time "2013-01-14T23:00:00.000") (c/to-date-time "2013-01-15T21:15:00.000"))
  (interval (c/to-date-time "2013-01-15T21:30:00.000") (c/to-date-time "2013-01-15T22:15:00.000"))
  (interval (c/to-date-time "2013-01-15T23:00:00.000") (c/to-date-time "2013-01-16T21:15:00.000"))
  (interval (c/to-date-time "2013-01-16T21:30:00.000") (c/to-date-time "2013-01-16T22:15:00.000"))]

 "JST" "20130116:1630-2330,0900-1135,1145-1515;20130117:1630-2330,0900-1135,1145-1515"
 [(interval (c/to-date-time "2013-01-16T07:30:00.000") (c/to-date-time "2013-01-16T14:30:00.000"))
  (interval (c/to-date-time "2013-01-16T00:00:00.000") (c/to-date-time "2013-01-16T02:35:00.000"))
  (interval (c/to-date-time "2013-01-16T02:45:00.000") (c/to-date-time "2013-01-16T06:15:00.000"))
  (interval (c/to-date-time "2013-01-17T07:30:00.000") (c/to-date-time "2013-01-17T14:30:00.000"))
  (interval (c/to-date-time "2013-01-17T00:00:00.000") (c/to-date-time "2013-01-17T02:35:00.000"))
  (interval (c/to-date-time "2013-01-17T02:45:00.000") (c/to-date-time "2013-01-17T06:15:00.000"))]

 "EST"  "20130115:1715-1700;20130116:1715-1700"
 [(interval (c/to-date-time "2013-01-14T22:15:00.000") (c/to-date-time "2013-01-15T22:00:00.000"))
  (interval (c/to-date-time "2013-01-15T22:15:00.000") (c/to-date-time "2013-01-16T22:00:00.000"))]

 "EST"  "20130115:CLOSED;20130116:1715-1700"
 [(interval (c/to-date-time "2013-01-15T05:00:00.000") (c/to-date-time "2013-01-15T05:00:00.000"))
  (interval (c/to-date-time "2013-01-15T22:15:00.000") (c/to-date-time "2013-01-16T22:00:00.000"))])
