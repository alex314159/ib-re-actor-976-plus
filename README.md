# ib-re-actor-976-plus

A clojure friendly wrapper around the Interactive Brokers java API.

## NOTHING IS READY YET - JUST A PLACEHOLDER FOR NOW

## Acknowledgements

This is a heavily refactored fork of https://github.com/cbilson/ib-re-actor and https://github.com/jsab/ib-re-actor-976-plus. That wrapper was suitable for the 971 version of the API. Interactive Brokers introduced several breaking changes starting with version 972. This main purpose of this wrapper is to update the code to work with subsequent API version. It also introduces some simplifying changes. At the moment it has been tested with version 976.

## IB API changes from 971 to recent versions

After a long period of stability with version 971, Interactive Brokers introduced several changes to modernize the API starting with version 972. Changes include:
* the introduction of `EReader` and `ESignal` classes to connect to the API, breaking the existing connection mechanism.
* encapsulation everywhere: historical prices are now returned through the `Bar` class; fields that used to be strings, such as security type or order action, are now Java Enums; class fields that used to be public are now private with getters and setters. For instance `m_conId` in the Contract class is replaced by a `conid()` get and `conid(integer)` set.
* more data (fields in `tickPrice`) and more callbacks in `EWrapper` (additional functionality).

## Main changes from ib-re-actor

The smart stuff was done before me. Indeed, a great amount of work was originally done to translate IB outputs into clean Clojure data maps, as well as convert Clojure data maps to Java classes.

However, with the introduction of many more classes, most of which I have no use for and I'm not sure how to test, I've gone back to basics: if IB is sending you an Object as a result, you'll get an Object in the wrapper.  In essence the EWrapper implementation is now auto-generated from the `EWrapper.java` interface, making it largely future proof. For every callback, the code will send a map of the form `{:type :calling-function-name-in-kebab-case :calling-function-argument-name-in-kebab-case calling-function-argument-value}`. This makes it easy to refer to the Interactive Brokers API official documentation.

The code to translate from and to IB classes is still there and has been updated to the best of my abilities, but it is not fully tested. Given IB changes things overtime, I think it is safer to use this code at the edge of your project, instead of inside the `EWrapper` implementation as it was originally done. The `demoapp.clj` namespace has some examples.

Finally I've broken dependencies to clj-time, which itself is a wrapper around Joda time, preferring to keep raw IB results. The rationale for that is two-fold: first, `java.time` supersedes Joda time and is easy to call directly from Clojure. Secondly, interpreting IB results led to some very messy code, partly because IB themselves are not consistent with their use of dates, times, and timezones.

## Installation

At this time, IB does not distribute the TWSAPI on maven central and I am not sure I have the legal right to publish it myself so you have to download it manually from http://interactivebrokers.github.io/# and install it locally. The following instructions have been tested with Leiningen.

From the download folder, go to IBJts/source/JavaClient and find the TwsAPI.jar file. Rename this file twsapi-version.jar (so for version 9.76.01 it is twsapi-9.76.01.jar) and copy it to  `.../.m2/repository/twsapi/twsapi/version/`, assuming your maven folder is `.m2`. So for version 9.76.01 you end up having `.../.m2/repository/twsapi/twsapi/9.76.01/twsapi-9.76.01.jar`.

In `project.clj` add `:plugins [[lein-localrepo "0.5.4"]]` ad the end of the file. Then add `[twsapi "version"]` in your dependencies.

I then recommend cloning this repo and having direct access to the source code instead of using it as a dependency.

## Warning

I've used ib-re-actor in live trading for many years, and I'm using this version now. I think it's stable, but I make no warranties, please test at length using a paper account.

## Usage

What the wrapper does:
* connect to TWS
* implement the EWrapper interface
* provide optional syntaxic sugar to convert IB classes to data maps and data maps to IB classes
* provide some convenience functions.

You need to provide the connection with listeners that will do things based on callbacks. Typically you will only need to listen to a small subset of the events that can be emitted by the wrapper. So if you don't use historical data or options you don't need to listen to these callbacks. Note that if you're going to do things that take time, it's a good idea to start them in separate threads so the listener thread is always free. You can provide the connection with many listeners. Another natural way is to define a multimethod that will filter on event type and print or log by default.

Finally you need to manage your own request-ids and order-ids. Note that IB only accepts order-ids in increasing order - if you are sending orders concurrently, use a locking mechanism.

Check the demo app namespace for example usage. For any help on the underlying events, the official API documentation is at https://interactivebrokers.github.io/tws-api/.

## Examples

### Connecting

ib-react-or maintains a connection to the interactive brokers gateway
that you can share amongst many handler functions. To establish a connection:

```clojure
user> (connect)
#<Agent@3a33517f: nil>
```

In order to get called back when a message is received from the
gateway, use the subscribe function:

```clojure
user> (subscribe prn)
#<Agent@76115ae0: (#<core$prn clojure.core$prn@d1af848>)>
```

To see the callback in action, you can call something like
`request-current-time`. This sends a message to the gateway, then to
Interactive Broker servers, and eventually returns the current time to
our callback function via a :current-time message:

```clojure
user=> (request-current-time)
#<Agent@275997e4: #<EClientSocket com.ib.client.EClientSocket@5fd78173>>
user=> {:type :current-time, :value #<DateTime 2012-12-20T12:37:41.000Z>}
```

Note that the messages is rudely placed after your prompt (or if you
are using nrepl or swank in emacs, you won't see any response.) This
is because the message is dispatched on an agent thread. (If you are
using emacs, it's actually less rude, because your message will be
visible in the \*nrepl-server\* buffer, or whatever the equivalent is
in swank.)

To disconnect, simply call `disconnect`:

```clojure
user=> (disconnect)
#<Agent@275997e4: #<EClientSocket com.ib.client.EClientSocket@5fd78173>>
user=>
```

Any commands you issue after that will get back a "Not connected"
error message:

```clojure
user=> (disconnect)
#<Agent@275997e4: #<EClientSocket com.ib.client.EClientSocket@5fd78173>>
user=> (request-current-time)
#<Agent@275997e4: #<EClientSocket com.ib.client.EClientSocket@5fd78173>>
{:type :error, :request-id -1, :code 504, :message "Not connected"}
user=>
```

Note that there can be only one connection to the Interactive Brokers
gateway or TWS for a given client ID, so if you are writing an
application that makes multiple connections (from different
processes), you will want to come up with a way to keep the client IDs unique.

### Errors

Errors generally come back from the gateway in a message. They can be
request specific, in which case they will have a non-negative
`:request-id`, connection wide, in which case the `:request-id` will
be -1, and sometimes they may include an exception.

### Requesting Contract Information

All tradeable instruments are referred to as "contracts" in the
API documentation. Contracts are divided into a few types:

* :equity : stock, common stock, preferred stock
* :option : option contracts on stocks or other instruments
* :future : futures contracts on commodities
* :index : informational symbols, such as the value of the S&P
          500. These are generally not tradeable, but you can use the
          same API functions to get information about them as you
          would for tradeable instruments.
* :future-option : options on futures contracts
* :cash, :bag: ???

When requesting information about the contract, you need to specify a
symbol to lookup. Your options are the `:symbol` key for the general
symbol, or `:local-symbol` for an exchange specific symbol. Generally,
you also want to specify an `:exchange`, and maybe a `:currency` as
well, unless you are not sure and want more results to look for.

```clojure
user> (request-contract-details {:symbol "AAPL" :type :equity})
6
user>
;;; many, many results
...
;;; example: APPL trading in Mexico:

{:type :contract-details, :value {:next-option-partial false,
  :time-zone-id "CTT",
  :long-name "APPLE INC",
  :subcategory "Computers",
  :liquid-hours "20121220:0830-1500;20121221:0830-1500",
  ...
  :order-types [:ACTIVETIM :ADJUST :ALERT :ALLOC ...]
  :valid-exchanges ["MEXI"],
  :summary {:symbol "AAPL", :currency "MXN", :contract-id 38708077,
            :primary-exchange "MEXI",
            :local-symbol "AAPL",
            :type :equity,
            :exchange "MEXI"},
            :market-name "AAPL",
            :category "Computers" ...},
 :request-id 6}
...
{:type :contract-details-end, :request-id 6}

;;; more specifically, if we were interested in trading AAPL on
;;; ISLAND:

user> (request-contract-details {:symbol "AAPL" :type :equity :exchange "ISLAND"})

;;; only gets the one match

```

You can see all the valid exchanges for a security in the results from
a broad search and then be more specific when you actually want to
trade it.

As you can see, the response contains a `:local-symbol` which is the
same as the symbol we requested. Even when the local-symbol and symbol
don't match, you can use `:symbol` and just specify the exchange:

```clojure
user> (request-contract-details {:symbol "BP" :type :equity})

;;; lot's of matches
...
;;; say we only wanted this one:
{:type :contract-details, :request-id 11, :value { ...
   :long-name "BANCO POPOLARE SCARL", ...
   :valid-exchanges ["SMART" "BVME" "FWB" "MIBSX" "SWB"], ...
   :summary {..., :type :equity, :currency "EUR",
             :primary-exchange "BVME",
             :local-symbol "B8Z",
             :exchange "SWB",
             :symbol "BP", ...}, ...}}

;;; be more specific
user> (request-contract-details {:symbol "BP" :exchange "SWB" :type :equity})

;;; only gets the one match
{... :value {... :long-name "BANCO POPLARE SCARL" ...} ...}

;;; or:
user> (request-contract-details {:local-symbol "B8Z" :type :equity})

;;; actually gets 2 matches, because Banco Poplare's local symbol is
;;; the same on both the SWB (Stuttgart) and FWB (Frankfurt)
;;; exchanges.

```

For futures, I usually find it works best to use a local symbol with
a built in expiration:

```clojure
user> (request-contract-details {:local-symbol "ESH3" :type :future})

{... :value {... :long-name "E-mini S&P 500", :contract-month "201303",
             :summary {:multiplier 50.0, :expiry #<DateTime 2013-03-15T00:00:00.000Z>,
             :type :future, :currency "USD", :local-symbol "ESH3",
             :exchange "GLOBEX", :symbol "ES", ..., :contract-id 98770297},
             :market-name "ES", ...}}

user> (request-contract-details {:local-symbol "ZN   DEC 12" :type :future})

{..., :value {... :long-name "10 Year US Treasury Note", :contract-month "201212",
              :summary {:multiplier 1000.0, :expiry #<DateTime 2012-12-19T00:00:00.000Z>,
              :type :future, :currency "USD", :local-symbol "ZN   DEC 12",
              :exchange "ECBOT", :symbol "ZN", :contract-id 94977350},
              :market-name "ZN"}}

;;; alternatively, specify the :symbol and an expiry:
user> (request-contract-details {:symbol "NQ" :expiry (date-time 2012 12) :type :future})

;;; same result

```

You can also use `:contract-id`, which is a unique identifier assigned
by Interactive Brokers to identify securities:

```clojure
user> (request-contract-details {:contract-id 98770297})

{... :value {... :long-name "E-mini S&P 500", :contract-month "201303",
             :summary {:multiplier 50.0, :expiry #<DateTime 2013-03-15T00:00:00.000Z>,
             :type :future, :currency "USD", :local-symbol "ESH3",
             :exchange "GLOBEX", :symbol "ES", ..., :contract-id 98770297},
             :market-name "ES", ...}}

```

### Requesting Historical Data

To get historical bars, use the `request-historical-data` function:

```clojure
;;; '1' is the request-id that will be part of all messages in
;;; response to this request
user> (request-historical-data 1 {:symbol "AAPL" :type :equity :exchange "ISLAND"}
 (date-time 2012 12 18 20) 1 :day 1 :hour)

{:WAP 524.187, :close 521.69, :has-gaps? false, :trade-count 4538, :low 521.27, :type :price-bar, :time #<DateTime 2012-12-18T14:30:00.000Z>, :open 524.88, :high 526.35, :volume 8260, :request-id 1}
...
{:WAP 529.905, :close 530.79, :has-gaps? false, :trade-count 2563, :low 527.79, :type :price-bar, :time #<DateTime 2012-12-18T19:00:00.000Z>, :open 530.27, :high 531.64, :volume 3293, :request-id 1}
{:type :price-bar-complete, :request-id 1}

```

Note, all date-times are in UTC unless otherwise noted.

Interactive Brokers throttles historical data requests. The
restrictions at the time this was written were:
  - bar size <= 30 seconds: 6 months
  - each request must be for less than 2000 bars
  - no identical requests within 15 seconds
  - no making more than 6 requests for the same
    contract+exchange+tick-type within 2 seconds
  - no more than 60 historical data requests in a 10 minute period

In order to remain within these limits we must throttle requests for
large amounts of data. One way to do this is to break up the requests
such that you are requesting less than 2000 bars every 10
seconds. This satisfies the second requirement above and insures that
you will have a maximum of 60 requests in a 10 minute period,
satisfying the last requirement.

For example, let's say we want 1 second bars for an entire day for DOW
minis. 2000 seconds is about 33 minutes. YMZ2 trades almost all day,
but let's say we want to start when it gets liquid all the way until
the next time it stops trading:

```clojure
user> (def ymz2 {:type :future :local-symbol "YM   DEC 12"
                 :exchange "ECBOT"}
#'user/ymz2
user> (-> (get-contract-details ymz2) first :value
          (select-keys [:liquid-hours :trading-hours])
          pprint)
{:trading-hours "20121021:CLOSED;20121023:1700-1515",
 :liquid-hours "20121021:CLOSED;20121023:0830-1515"}
```

So let's request 1 second bars from 8:30 to 15:15 EST (13:30 to 20:15
UTC). That's 9 hours and 15 minutes, or a total of 19 requests.

This example breaks the requested period up into retrievable chunks.

```clojure
user> (def prices (atom []))
#'user/prices

;; we can subscribe this function with request-id curried in
user> (defn store-price [request-id msg]
        (when (and (= request-id (:request-id msg))
                   (= :price-bar (:type msg)))
          (swap! prices conj msg)))
#'user/store-price
user> (def end-times (->> (iterate #(plus % (secs 2000)) (date-time 2012 12 18 11 30))
                     (take 19)))
#'user/end-times
user> (prn (first end-times) (last end-times))
#<DateTime 2012-12-18T11:30:00.000Z> #<DateTime 2012-12-18T21:30:00.000Z>
nil
user>
```

In order to avoid pacing violations, we will make each request, then
sleep for 10 seconds:

```clojure
;;; Get a unique request id
user> (def my-request-id (get-request-id))
#'user/my-request-id
user> (subscribe (partial store-price my-request-id))
#<Agent@76115ae0: ()>
user> (doseq [t end-times]
        (request-historical-data my-request-id ymz2 t 2000 :seconds 1 :seconds)
        (Thread/sleep 10000))
```

### Requesting Real Time Data

FIXME: write this

### Market Scanners

FIXME: write this

### Orders

To see all the active orders on a connection, use the
`request-account-updates` function. If `subscribe?` is true, a message
will be sent each time any attribute of the account changes. An
`:account-download-end` message will be sent when all the changes are
made.

```clojure

```

### Account Management

FIXME: write this

### Synchronous Wrappers

There are several wrapper functions in the `ib-re-actor-976-plus.synchronous`
namespace that make the process of interacting with the gateway
synchronous and more interactive. This is useful when doing
exploratory coding in the REPL, where you don't want the hassle of
creating functions and managing their subscriptions for simple requests.

```clojure
;;; wrap request-historical-data
user> (get-historical-data {:symbol "AAPL" :type :equity :exchange "ISLAND"}
                           (date-time 2012 12 20 20) 1 :day 1 :hour :trades true)
({:has-gaps? false, :volume 3507, :trade-count 2687, :close 524.77,
:low 521.14, :high 525.41, :open 522.58, :time #<DateTime
2012-12-20T19:00:00.000Z>} {:has-gaps? false, :volume 4545
...

;;; wrap request-current-time
user> (get-time)
#<DateTime 2012-12-21T01:42:58.000Z>

;;; wrap request-contract-details
user> (get-contract-details {:symbol "AAPL" :type :equity :exchange "ISLAND"})
({:type :contract-details, :request-id 38, :value {:next-option-partial false,
:time-zone-id "EST", :underlying-contract-id 0, :price-magnifier 1,
:industry "Technology", :trading-hours "20121220:0700-2000;20121221:0700-2000",
:long-name "APPLE INC", :convertible? false, ...

;;; wrap getting the current price for a security (request-market-data)
user> (get-current-price {:symbol "AAPL" :type :equity :exchange "ISLAND"})
{:open-tick 530.15, :bid-price -1.0, :close-price 526.31, :last-size 3, :low 518.88, :ask-size 0, :bid-size 0, :last-timestamp #<DateTime 2012-12-21T00:59:54.000Z>, :last-price 520.17, :ask-price -1.0, :high 530.2, :volume 170569}

;;; wrap executing an order and blocking until it's filled
user> (execute-order {:symbol "AAPL" :type :equity :exchange "ISLAND"}
                     {:transmit? true :action :buy :type :market :quantity 100
                      :outside-regular-traiding-hours? true})

; FIXME: make an example while the market is open

;;; wrap getting all open orders (request-open-orders)
user> (get-open-orders)
({:order-state {:maximum-commission 1.7976931348623157E308, :minimum-commission 1.7976931348623157E308, :commission 1.7976931348623157E308, :equity-with-loan "1.7976931348623157E308", :maintenance-margin "1.7976931348623157E308", :initial-margin "1.7976931348623157E308", :status :pre-submitted}, :order {:time-in-force :day, :order-id 3, :client-id 101, :discretionary-amount 0.0, :action :buy, :quantity 100, :sweep-to-fill? false, :limit-price 0.0, :outside-regular-trading-hours? false, :transmit? true, :stop-price 0.0, :hidden? false, :type :market, :all-or-none? false, :block-order? false, :permanent-id 1644329200}, :contract {:put-call-right :unknown, :include-expired? false, :type :equity, :currency "USD", :local-symbol "AAPL", :exchange "ISLAND", :symbol "AAPL", :contract-id 265598}, :order-id 3})
```

### Other Things

#### Implementation notes

The IB API appears to differentiate between types of ids such as ticker id,
order id and request id. At some point, this library kept them independent but a
problem occurs when an error is received. Indeed, errors specify an id but not
the type of id. It is thus impossible to know the error concerns which of the
requests. In order to alleviate this problem ids are not reused.

## License

Copyright (C) 2011-2020 Chris Bilson, Jean-Sebastien A. Beaudry, Alexandre Almosni

Distributed under the Eclipse Public License, the same as Clojure.

[1]: http://www.interactivebrokers.com/en/software/api/api.htm
