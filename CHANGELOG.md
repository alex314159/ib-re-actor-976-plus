# Changelog
All notable changes to this project will be documented in this file.

## [0.1.84] - 2023-06-25
### Make 10.22.01 the default implementation
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