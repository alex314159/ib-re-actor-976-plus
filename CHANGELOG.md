# Changelog
All notable changes to this project will be documented in this file.

## [0.1.8] - 2022-07-24
### Bug fixes
- Compatibility with twsapi 10.16.01
- Added connect-simple in the gateway

## [0.1.7] - 2022-05-14
### Bug fixes
- Compatibility with twsapi 10.15.02: different signature to error class in the wrapper
- Improve compatibility with older version: remove crypto type for old versions of the wrapper and use reflector for Decimal in translation.clj