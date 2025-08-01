# Changelog
All notable changes to this project will be documented in this file.

## [0.1.10.39.01] - 2025-08-01
### Added support for twsapi 10.39.01

## [0.1.10.37.02] - 2025-06-23
### Added support for twsapi 10.37.02
### New versioning scheme to match TWS API version
- API now supports protocol buffers, leading to a new dependency on `com.google.protobuf/protobuf-java`
- EWrapper.java has a comment to indicate the start of protocol buffer methods, this required a manual fix to the EWrapper interface
- Started work on a less brittle reification to support more frequent changes in the EWrapper interface like the one above. Hence new dependency `com.github.javaparser/javaparser-core`

## [0.1.87] - 2025-01-05
### Added support for twsapi 10.33.01 which is now the default version
- New signature for error class in the wrapper
- New OrderCancel class
- CommissionAndFeesReport replaces CommissionReport, also in order-state

## [0.1.86] - 2024-08-04
### Added support for twsapi 10.30.01

## [0.1.85] - 2024-01-07
### Added support for twsapi 10.26.03

## [0.1.84] - 2023-06-25
### Made 10.22.01 the default implementation at the time
- Better support for uberjar

## [0.1.83] - 2023-06-18
### Added support for twsapi 10.22.01

## [0.1.82] - 2022-12-31
### Bug fixes
- Added translation for more account keys

## [0.1.8] - 2022-07-24
### Bug fixes
- Compatibility with twsapi 10.16.01
- Added connect-simple in the gateway

## [0.1.7] - 2022-05-14
### Bug fixes
- Compatibility with twsapi 10.15.02: different signature to error class in the wrapper
- Improve compatibility with older version: remove crypto type for old versions of the wrapper and use reflector for Decimal in translation.clj