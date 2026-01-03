# ib-re-actor-976-plus

A clojure friendly wrapper around the Interactive Brokers java API.

[![Clojars Project](https://img.shields.io/clojars/v/ib-re-actor-976-plus.svg)](https://clojars.org/ib-re-actor-976-plus)

## Acknowledgements

This is a heavily refactored fork of https://github.com/cbilson/ib-re-actor and https://github.com/jsab/ib-re-actor. That wrapper was suitable for the 971 version of the API. Interactive Brokers introduced several breaking changes starting with version 972. The main purpose of this wrapper is to support recent API versions. It also introduces some simplifying changes. See addendums for a list of changes from the original.

## Installation

IB does not distribute the TWSAPI on central repositories so you have to download it manually from http://interactivebrokers.github.io/# and install it locally. Note that downloading the API from that link is implicitly consenting to IB's license. The following instructions have been tested with Leiningen.

From the download folder, go to IBJts/source/JavaClient and find the TwsApi.jar file. Rename this file twsapi-version.jar (so for version 10.42.01 it is twsapi-10.42.01.jar) and copy it to  `.../.m2/repository/twsapi/twsapi/version/`, assuming your maven folder is `.m2`. So for version 10.42.01 you end up having `.../.m2/repository/twsapi/twsapi/10.42.01/twsapi-10.42.01.jar`. Note that on Mac the default unarchiver will refuse to open the zip file, extract it in the terminal by typing `unzip [filename.zip]` or use another unarchiver.  

In `project.clj` add `[twsapi "version"]` as well as `[ib-re-actor-976-plus "0.1.10.42.01-SNAPSHOT"]` in your dependencies.

At the moment this has been tested with most versions between 9.76.01 and 10.42.01. The package version corresponds to the TWS API version - **as of 10.42.01, the wrapper no longer guarantees backwards compatibility**. If you are using TWS API 10.39.01, use that version of the wrapper.

## Warning

I've used ib-re-actor in live trading for many years, and I'm using this version now. I think it's stable, but I make no warranties, please test at length using a paper account.

## Usage

What the wrapper does:
* connect to TWS
* implement the EWrapper interface
* provide optional syntaxic sugar to convert IB classes to data maps and data maps to IB classes
* provide some convenience functions.

You need to provide the connection with listeners that will do things based on callbacks. Typically you will only need to listen to a small subset of the events that can be emitted by the wrapper. So if you don't use historical data or options you don't need to listen to these callbacks. Note that if you're going to do things that take time, it's a good idea to start them in separate threads so the listener thread is always free. You can provide the connection with many listeners. Another natural way is to define a multimethod that will dispatch on event type and print or log by default.

Every callback will send a map of the form `{:type :calling-function-name-in-kebab-case :calling-function-argument-name-in-kebab-case calling-function-argument-value}`. This makes it easy to refer to the Interactive Brokers API official documentation, which is at https://interactivebrokers.github.io/tws-api/

Finally, you need to manage your own request-ids and order-ids. Note that IB only accepts order-ids in increasing order - if you are sending orders concurrently, use a locking mechanism.

The demo_apps folder has some examples.

## Addendum 1: IB API changes from 971 to recent versions

After a long period of stability with version 971, Interactive Brokers introduced several changes to modernize the API starting with version 972. Changes include:
* the introduction of `EReader` and `ESignal` classes to connect to the API, breaking the existing connection mechanism.
* encapsulation everywhere: historical prices are now returned through the `Bar` class; fields that used to be strings, such as security type or order action, are now Java Enums; class fields that used to be public are now private with getters and setters. For instance `m_conId` in the Contract class is replaced by a `conid()` get and `conid(integer)` set.
* more data (fields in `tickPrice`) and more callbacks in `EWrapper` (additional functionality).

## Addendum 2: Main changes from ib-re-actor

The smart stuff was done before me. Indeed, a great amount of work was originally done to translate IB outputs into clean Clojure data maps, as well as convert Clojure data maps to Java classes.

However, with the introduction of many more classes, most of which I have no use for, and I'm not sure how to test, I've gone back to basics: if IB is sending you an Object as a result, you'll get an Object in the wrapper.  In essence the EWrapper implementation is now auto-generated from the `EWrapper.java` interface, making it largely future-proof.

The code to translate from and to IB classes is still there and has been updated to the best of my abilities, but it is not fully tested. Given IB changes things overtime, I think it is safer to use this code at the edge of your project, instead of inside the `EWrapper` implementation as it was originally done. Please refer to the demo_apps folder for examples.

Finally, I've broken dependencies to clj-time (Joda), preferring to keep raw IB results. The rationale for that is two-fold: first, `java.time` supersedes Joda time and is easy to call directly from Clojure. Secondly, interpreting IB results led to some very messy code, partly because IB themselves are not consistent with their use of dates, times, and timezones.


## License

Copyright (C) 2011-2025 Chris Bilson, Jean-Sebastien A. Beaudry, Alexandre Almosni

Distributed under the Eclipse Public License, the same as Clojure.

[1]: http://www.interactivebrokers.com/en/software/api/api.htm
