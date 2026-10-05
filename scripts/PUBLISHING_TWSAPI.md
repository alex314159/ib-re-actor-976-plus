# Publishing TwsApi.jar to Clojars

IB does not publish the TWS API to any Maven repository, so it has to be
repackaged and deployed once per API version. `publish-twsapi.sh` does that:
it pulls `TwsApi.jar` out of IB's zip, stamps IB's `LICENSE`, `NOTICE` and
`THIRD_PARTY_LICENSES/` into `META-INF/`, writes a pom that depends on the
same `protobuf-java` version IB ships in `jars/`, and runs `lein deploy`.

Both the artifact version and the protobuf version are read out of the zip,
so there is nothing to edit between releases.

## Usage

Download `twsapi_macunix.<ver>.zip` from https://interactivebrokers.github.io/
(the Mac/Unix zip — the Windows installer does not contain the jar in a
usable layout), then:

```bash
CLOJARS_USERNAME=alex314159 ./scripts/publish-twsapi.sh ~/Downloads/twsapi_macunix.1050.01.zip
```

That publishes `[net.clojars.alex314159/twsapi "10.50.01"]`.

Clojars credentials come from `~/.lein/credentials.clj.gpg` the same way any
other `lein deploy` does; the generated shim project sets
`:sign-releases false` so no GPG signature of the artifact itself is needed.

### Environment variables

| Var | Default | Purpose |
| --- | --- | --- |
| `GROUP` | `net.clojars.$CLOJARS_USERNAME` (else `$USER`) | Maven group id |
| `CLOJARS_USERNAME` | `$USER` | only used to build the default `GROUP` |
| `REPO` | `clojars` | deploy target; see dry run below |

### Dry run

Deploy into the local Maven repo instead of Clojars to check the jar and pom
before publishing for real:

```bash
REPO="file://$HOME/.m2/repository" CLOJARS_USERNAME=alex314159 ./scripts/publish-twsapi.sh ~/Downloads/twsapi_macunix.1050.01.zip
unzip -l ~/.m2/repository/net/clojars/alex314159/twsapi/10.50.01/twsapi-10.50.01.jar | grep META-INF
```

## Why

As of 10.49 IB's Java sources are GPLv3, so they cannot be vendored into this
EPL-licensed project. Publishing the jar as a separate artifact keeps the two
licenses apart: `ib-re-actor-976-plus` just depends on it, and the license and
notice files travel inside the jar.

## Requirements

`bash`, `unzip`, `jar` (any JDK) and `lein` on `PATH`.
