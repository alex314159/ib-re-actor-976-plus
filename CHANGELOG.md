# Changelog
All notable changes to this project will be documented in this file. Note the underlying changelog at https://ibkrguides.com/releasenotes/prod-2026.htm

## [0.3.10.51.01] - 2026-10-05
### Update tws to 10.51.01
- Per IB's release notes (2026-09-30) the only change is a fix to `reqPositions`, which returned incorrect `exchange` values for some instruments (commodities, CFDs). That fix is server-side: the Java client sources in `com/ib/client` are byte-identical to 10.50.01, `EWrapper` is unchanged, and regenerating the mappings produces no diff.
- Depends on `[net.clojars.alex314159/twsapi "10.51.01"]`, published with `scripts/publish-twsapi.sh` as before.

## [0.3.10.50.01] - 2026-09-01
### twsapi is now a normal dependency - no manual .m2 install
- IB's `TwsApi.jar` is published to Clojars as `[net.clojars.alex314159/twsapi "10.50.01"]` and is a plain `:dependencies` entry, so `lein deps` fetches it. The hand-install into `~/.m2/repository/twsapi/twsapi/<version>/` is no longer needed.
- `scripts/publish-twsapi.sh` builds and deploys that artifact from IB's `twsapi_macunix.<ver>.zip`: it stamps IB's `LICENSE`, `NOTICE` and `THIRD_PARTY_LICENSES/` into `META-INF/` and writes a pom depending on the `protobuf-java` version IB ships alongside. Version and protobuf version are both read out of the zip. See `scripts/PUBLISHING_TWSAPI.md`.
- Dropped the explicit `[com.google.protobuf/protobuf-java "4.29.5"]`; it now comes in transitively from twsapi's pom, at the same version. `protobuf.clj` imports `com.google.protobuf` directly, so this is an undeclared direct dependency on purpose - Maven is nearest-wins, so pinning it here would override twsapi's and could silently hand IB's jar a protobuf it wasn't compiled against.
- Minor version bumped to 0.3 for the changed coordinate. `translation/tws-version` still reads the version off the classpath entry and is unaffected by the new group id.
### Update tws to 10.50.01
- Per IB's release notes (2026-08-28) the only API change is a new `Order.conditionsIncludeOvernight` field, letting conditional orders evaluate their conditions during overnight sessions. `EWrapper` is unchanged from 10.49.01, so no new callbacks and no new read-only mappings.
- Mappings regenerated from the 10.50.01 sources: `Order` gains `:conditions-include-overnight`.

## [0.2.10.49.01] - 2026-08-03
- Update Clojure and tools.logging to latest stable version
- Update tws to 10.49.01. IB published no 10.49 release notes; diffing the jars against 10.46.01 shows the only API changes are the 10.47 fundamentals removal and a new `ContractDetails.settlementMethod` field (itself undocumented).
### Breaking: fundamental data is de-supported by IB as of 10.47
- Removed `request-fundamental-data` / `cancel-fundamental-data` from `gateway.clj` and `client_socket.clj` — the underlying `reqFundamentalData` / `cancelFundamentalData` no longer exist in the TWS API.
- Removed tick type `:fundamental-ratios` (47) and generic tick `:fundamental-ratios` (258).
- Removed the now-unreachable `report-type` and `fundamental-ratio` translation tables.
### `$LEDGER-` prefix support (new API setting in 10.47)
- TWS can now prepend `$LEDGER-` to per-currency account values (on by default for new users) so they can be told apart from account-level values of the same name.
- `(translate :from-ib :account-value-key "$LEDGER-CashBalance")` returns `:ledger/cash-balance`; `numeric-account-value?`, `integer-account-value?` and `boolean-account-value?` accept both plain and `:ledger/` forms.
### Synchronous calls no longer block forever
- `synchronous/await-result` replaces the bare `@result` deref in all 13 synchronous functions: it throws immediately when the socket is down, and after `synchronous/*timeout-ms*` (default 30s, rebindable) when TWS accepts the request but never answers.
- Previously any of these would park the calling thread for good. Loading `demoapps/synchronous_app.clj` without a gateway running hung a REPL - and hung bare `lein midje`, since that loads every namespace on the source path.
### Tests rewritten against the current API
- `test/…/{translation,mapping,wrapper}.clj` dated from the pre-10.x API and no longer compiled or passed: they expected translation tables to yield strings rather than `Types$*` enums, set `m_`-prefixed public fields directly, imported the removed `CommissionReport`, and asserted the old nested `{:type … :value {…}}` wrapper messages.
- They now cover what the library actually does, including the changes above: `$LEDGER-` keys, the absent fundamentals tables, `:settlement-method`, `->map` on the read-only callback classes, and the flat message shape the generated reification produces.
- New `test/…/synchronous.clj` covers the timeout guard. No test needs a running TWS. Run them with `lein midje 'ib-re-actor-976-plus.test.*'` — 168 checks.
### Packaging: IB's Java sources are no longer shipped in the jar
- `resources/com/ib/**` is excluded from the jar via `:jar-exclusions`. `.gitignore` already kept these out of git, but lein builds the jar from `:resource-paths` and never consults `.gitignore`, so all 300 of them were being published - as they were in 0.2.10.46.01-SNAPSHOT. IB relicensed these sources to GPLv3 in 10.49, so shipping them from an EPL jar is now a license incompatibility.
- Nothing needs them at runtime; the `EWrapper_*.java` at the resources root are still included, since `wrapper.clj` reads the matching one off the classpath. The jar goes from 1.4M to 120K.
### Mappings regenerated from the 10.49.01 sources in `resources/com/ib/client/`
- `ContractDetails` gains `:settlement-method`.
- `mapping_generator.clj` now parses at Java 17 language level — 10.49 sources use switch expressions, which silently broke parsing of `EClient.java`.

## [0.2.10.46.01] - 2026-05-09
### Refactored mapping namespaces:
- `mapping_auto` renamed back to `mapping` — simpler and consistent with prior versions. Users only need `[ib-re-actor-976-plus.mapping :refer [->map map->]]`.
- Protobuf functions extracted to dedicated `protobuf` namespace.
- Legacy mapping moved to `mapping-legacy` namespace; all active code now uses `mapping` and `generated-mappings`. *This means some of the old mappings may fail* — field names are closer to the Java API now, e.g. `:quantity` → `:total-quantity`, `:limit-price` → `:lmt-price`.
- Fixed missing `->map` support for read-only callback-delivered classes (`Bar`, `HistoricalTick`, `HistoricalTickBidAsk`, `HistoricalTickLast`, `HistoricalSession`, `SoftDollarTier`). These have no public setters so the generator skips them; mappings are hand-written in `mapping.clj` and are safe from regeneration.
- Fixed `advanced_app.clj` demo: `(map-> bar)` → `(->map bar)`.
- Added `MAPPING_GENERATOR.md` documenting the regeneration process and the requirement to maintain read-only callback class mappings manually.
### translation.clj improvements:
- Fixed `:day-till-cancelled` incorrectly mapping to FOK; now correctly maps to DTC.
- Added missing `what-to-show` values: `:schedule`, `:agg-trades`.
- Added missing `time-in-force` value: `:minutes`.
- Added missing `report-type` value: `:ownership`.
- Added missing `market-data-type` value: `:unknown`.
- Added missing `order-type` values: `:midprice`, `:pegged-to-best`.
- New translation tables: `exercise-action`, `tick-by-tick-type`, `volatility-type`, `trigger-method`, `oca-type`, `hedge-type`, `reference-price-type`.
- `mapping_generator.clj` updated to auto-detect the new enum types; regenerate with `write-generated-mappings!`.
- Fixed `exercise-options` in `client_socket.clj` which was calling a non-existent translation (would have thrown at runtime).
- Fixed `request-tick-by-tick-data` and `request-sec-def-option-parameters` to translate their enum parameters rather than passing raw values.
### Added ibflex namespace to make IB Flex queries. This is separate to the TWS API but often used in parallel, hence inclusion.

## [0.1.10.46.01] - 2026-04-24
### Added support for twsapi 10.46.01.
### New Order field: hedgeMaxSize.
### New tick types: oddLotBid (105), oddLotAsk (106), oddLotBidSize (107), oddLotAskSize (108), oddLotBidExch (109), oddLotAskExch (110).
### New generic tick 787 (odd lots).
### Add scanner subscription support #8 

## [0.1.10.44.01] - 2026-03-01
### Added support for twsapi 10.44.01.
### Repaired some synchronous functions, see the synchronous_app.clj file in the demoapps folder.

## [0.1.10.42.01] - 2026-01-02
### Added support for twsapi 10.42.01. This includes updating the protobuf-java version to 4.29.5.
### Breaking backwards support - use the wrapper version that matches the TWS API version. This greatly simplifies code.
### Experimental: generating mappings off underlying classes with javaparser. Can be used by requiring generated_mappings instead of mapping. Has much better coverage but use at your own risk.

## [0.1.10.40.01] - 2025-10-25
### Added support for twsapi 10.40.01
### Moved reification from being text-based to javaparser. Old reification kept in case.
- this should make future updates simpler.
### Added `protobuf->map` function in mapping.clj. This should be used at the edge of your project when using protocol buffer methods.
- protocol buffers are new. Whilst they feel very natural to use in Clojure, use with caution in live trading.
- note that at the moment the IB API fires two events on every request, one old style and one through a protocol buffer. In live trading silence either.
- added a small example in the demo basic_app

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