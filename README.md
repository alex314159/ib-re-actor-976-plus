# ib-re-actor-976-plus

A clojure friendly wrapper around the Interactive Brokers java API.

[![Clojars Project](https://img.shields.io/clojars/v/ib-re-actor-976-plus.svg)](https://clojars.org/ib-re-actor-976-plus)


## Installation

As of **10.50.01 there is no manual install** - IB's `TwsApi.jar` is on Clojars and comes
in transitively. Add one dependency:

```clojure
[ib-re-actor-976-plus "0.3.10.51.01-SNAPSHOT"]
```

That pulls in `[net.clojars.alex314159/twsapi "10.51.01"]`, which is IB's own
`TwsApi.jar` repackaged unmodified, with IB's `LICENSE`, `NOTICE` and
`THIRD_PARTY_LICENSES/` inside it under `META-INF/`. Note the jar is GPLv3 as of
10.49; by using it you are agreeing to IB's terms. If you'd rather publish it under
your own group id - or need a version nobody has published yet - use
`scripts/publish-twsapi.sh`, see [scripts/PUBLISHING_TWSAPI.md](scripts/PUBLISHING_TWSAPI.md).

### Older versions (before 0.3.10.50.01)

Those releases expect the jar hand-installed into your local Maven repository.
Download it from http://interactivebrokers.github.io/# after agreeing to IB's licence,
go to `IBJts/source/JavaClient`, rename `TwsApi.jar` to `twsapi-<version>.jar` and copy it
to `.../.m2/repository/twsapi/twsapi/<version>/` - so for 10.49.01 you end up with
`.../.m2/repository/twsapi/twsapi/10.49.01/twsapi-10.49.01.jar`. Then declare both
dependencies:

```clojure
[twsapi "10.49.01"]
[ib-re-actor-976-plus "0.2.10.49.01-SNAPSHOT"]
```

On Mac the default unarchiver refuses to open IB's zip; extract it from the terminal
with `unzip [filename.zip]` or use another unarchiver.

### Version compatibility

This has been tested with most versions between 9.76.01 and 10.51.01. The package version
corresponds to the TWS API version - **as of 10.42.01, the wrapper no longer guarantees
backwards compatibility**. If you are using TWS API 10.39.01, use that version of the wrapper.

Note that IB de-supported fundamental data in 10.47, so `request-fundamental-data` and
`cancel-fundamental-data` are gone as of 0.2.10.49.01. See the CHANGELOG for the full list
of changes.

## Warning

I've used this software in live trading for many years. I think it's stable, but I make no warranties, please test at length using a paper account.

## Usage

What the wrapper does:
* connect to TWS
* implement the EWrapper interface
* provide optional syntaxic sugar to convert IB classes to data maps and data maps to IB classes. My advice is to use IB objects in production as much as possible, and only use maps and protocol buffers at the edge of your project. A badly constructed map can create ill-defined IB objects, for instance a futures contract whose multiplier is not set properly.
* provide some convenience functions.

You need to provide the connection with listeners that will do things based on callbacks. Typically you will only need to listen to a small subset of the events that can be emitted by the wrapper. So if you don't use historical data or options you don't need to listen to these callbacks. Note that if you're going to do things that take time, it's a good idea to start them in separate threads so the listener thread is always free. You can provide the connection with many listeners. Another natural way is to define a multimethod that will dispatch on event type and print or log by default.

Every request will send two callbacks:
* a map of the form `{:type :calling-function-name-in-kebab-case :calling-function-argument-name-in-kebab-case calling-function-argument-value}`. This makes it easy to refer to the Interactive Brokers API official documentation, which is at https://interactivebrokers.github.io/tws-api/
* a protocol buffer implementation with a map of the form `{:type :calling-function-name-in-kebab-case :calling-function-argument-name-in-kebab-case calling-function-argument-value-as-a-protocol-buffer}`. IB has been pushing protocol buffers for a few months. Both callbacks have the same data. Protocol buffers feel very natural in Clojure (they're maps and we have a `decode-protobuf-vals` to decode them), but there is less history dealing with this API.

Finally, you need to manage your own request-ids and order-ids. Note that IB only accepts order-ids in increasing order - if you are sending orders concurrently, use a locking mechanism.

The demo_apps folder has some examples.

## Acknowledgements

This is a heavily refactored fork of https://github.com/cbilson/ib-re-actor and https://github.com/jsab/ib-re-actor, which were suitable up to version 9.71 of the TWS API. Interactive Brokers introduced several breaking changes starting with version 972, and greatly increased the frequency of updates. This wrapper tries to keep up to date by self generating off the Java code.

## License

Copyright (C) 2011-2026 Chris Bilson, Jean-Sebastien A. Beaudry, Alexandre Almosni

Distributed under the Eclipse Public License, the same as Clojure.

[1]: http://www.interactivebrokers.com/en/software/api/api.htm
