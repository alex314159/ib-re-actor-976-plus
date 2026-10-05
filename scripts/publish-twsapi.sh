#!/usr/bin/env bash
# Repackage IB's TwsApi.jar out of twsapi_macunix.<ver>.zip and deploy it to Clojars.
#   ./publish-twsapi.sh twsapi_macunix.1050.01.zip
#   GROUP=net.clojars.you ./publish-twsapi.sh ...   # override group id
set -euo pipefail

ZIP="${1:?usage: $0 twsapi_macunix.XXXX.XX.zip}"
ZIP="$(cd "$(dirname "$ZIP")" && pwd)/$(basename "$ZIP")"
GROUP="${GROUP:-net.clojars.${CLOJARS_USERNAME:-$USER}}"
ARTIFACT=twsapi
REPO="${REPO:-clojars}"    # or a URL, e.g. file://$HOME/.m2/repository for a dry run

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -q "$ZIP" -d "$WORK" \
  'IBJts/API_VersionNum.txt' 'IBJts/LICENSE' 'IBJts/NOTICE' \
  'IBJts/THIRD_PARTY_LICENSES/*' 'IBJts/source/JavaClient/TwsApi.jar'
SRC="$WORK/IBJts"

VERSION="$(sed -n 's/^API_Version=//p' "$SRC/API_VersionNum.txt" | tr -d '[:space:]')"
# protobuf-java version IB ships in jars/ — TwsApi.jar does not bundle it.
PROTOBUF="$(unzip -l "$ZIP" | sed -n 's|.*jars/protobuf-java-\(.*\)\.jar$|\1|p' | head -1)"
[ -n "$VERSION" ] && [ -n "$PROTOBUF" ] || { echo "could not read version(s)"; exit 1; }
echo "publishing [$GROUP/$ARTIFACT \"$VERSION\"] (protobuf-java $PROTOBUF)"

# Stamp the legal files into the jar under META-INF/.
STAGE="$WORK/stage"; mkdir -p "$STAGE/META-INF"
cp "$SRC/LICENSE" "$SRC/NOTICE" "$STAGE/META-INF/"
cp -R "$SRC/THIRD_PARTY_LICENSES" "$STAGE/META-INF/"
JAR="$WORK/$ARTIFACT-$VERSION.jar"
cp "$SRC/source/JavaClient/TwsApi.jar" "$JAR"
(cd "$STAGE" && jar uf "$JAR" META-INF)

cat > "$WORK/pom.xml" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$GROUP</groupId>
  <artifactId>$ARTIFACT</artifactId>
  <version>$VERSION</version>
  <packaging>jar</packaging>
  <name>$ARTIFACT</name>
  <description>Interactive Brokers TWS API Java client (TwsApi.jar), repackaged for Clojars.</description>
  <url>https://interactivebrokers.github.io/tws-api/</url>
  <licenses>
    <license>
      <name>Interactive Brokers TWS API License</name>
      <url>https://raw.githubusercontent.com/InteractiveBrokers/tws-api/master/LICENSE</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <dependencies>
    <dependency>
      <groupId>com.google.protobuf</groupId>
      <artifactId>protobuf-java</artifactId>
      <version>$PROTOBUF</version>
    </dependency>
  </dependencies>
</project>
POM

# lein deploy needs a project to resolve the "clojars" repo; :sign-releases false skips gpg.
cat > "$WORK/project.clj" <<'PRJ'
(defproject deploy-shim "0.0.0"
  :repositories [["clojars" {:url "https://repo.clojars.org/" :sign-releases false}]])
PRJ

cd "$WORK"
lein deploy "$REPO" "$GROUP/$ARTIFACT" "$VERSION" "$JAR" pom.xml
