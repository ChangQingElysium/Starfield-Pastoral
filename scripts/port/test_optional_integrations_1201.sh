#!/usr/bin/env bash
# Official release binaries are downloaded only into build, never a client's mods directory.
set -euo pipefail
cd "$(dirname "$0")/../.."
TASK_OPTIONAL_MAIN_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
if [ ! -f build/port-classpath.txt ] || [ ! -f "$TASK_OPTIONAL_MAIN_CLASSES/com/stardew/craft/StardewCraft.class" ]; then
    echo "Run ./gradlew classes printCompileClasspath first." >&2
    exit 1
fi
TASK_OPTIONAL_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
if [ -z "$TASK_OPTIONAL_JDK" ] && [ -d "$HOME/.gradle/jdks" ]; then
    TASK_OPTIONAL_JAVAC="$(find "$HOME/.gradle/jdks" -type f -path '*/bin/javac' -print -quit)"
    [ -z "$TASK_OPTIONAL_JAVAC" ] || TASK_OPTIONAL_JDK="${TASK_OPTIONAL_JAVAC%/bin/javac}"
fi
JAVAC_CMD=javac
JAVA_CMD=java
if [ -n "$TASK_OPTIONAL_JDK" ]; then
    JAVAC_CMD="$TASK_OPTIONAL_JDK/bin/javac"
    JAVA_CMD="$TASK_OPTIONAL_JDK/bin/java"
fi
CACHE=build/port-optional-audit
mkdir -p "$CACHE"
fetch() {
    local name="$1" checksum="$2" url="$3"
    if [ ! -f "$CACHE/$name" ]; then
        curl --fail --location --retry 2 "$url" --output "$CACHE/$name"
    fi
    local actual
    actual="$(shasum -a 256 "$CACHE/$name")"
    if [ "${actual%% *}" != "$checksum" ]; then
        echo "Checksum mismatch: $name (remove only this cached jar and retry)." >&2
        exit 1
    fi
}
fetch 'embeddium-0.3.31+mc1.20.1.jar' eed3d1325f2acc2fd4e69bb495e5ccb91d962126ac5330f0582ebc2a3daf47fb 'https://cdn.modrinth.com/data/sk9rgfiA/versions/UTbfe5d1/embeddium-0.3.31%2Bmc1.20.1.jar'
fetch oculus-mc1.20.1-1.8.0.jar 0945df0cba0f62b3901dd80c3268e5311b770ece78c78037a45db12ac0425fef 'https://cdn.modrinth.com/data/GchcoXML/versions/iQ1SwGc3/oculus-mc1.20.1-1.8.0.jar'
fetch xaerominimap-forge-1.20.1-25.3.10.jar dd118045c4daa8e27614f576d515ba5b07e4ccae060b6b1a536437f155879c7b 'https://cdn.modrinth.com/data/1bokaNcj/versions/iMXAV165/xaerominimap-forge-1.20.1-25.3.10.jar'
fetch xaeroworldmap-forge-1.20.1-1.40.11.jar d41ea1366e36f79f1b38efb44920fa9354a929eee070c493bda60b0c5cc2b16a 'https://cdn.modrinth.com/data/NcUtCpym/versions/vUbAYOnm/xaeroworldmap-forge-1.20.1-1.40.11.jar'
fetch appliedenergistics2-forge-15.4.10.jar fbfee05c6674cb6b00fe425e945ca4818d41eb34069b60dcb4a36221a0390fcc 'https://cdn.modrinth.com/data/XxWD5pD3/versions/7KVs6HMQ/appliedenergistics2-forge-15.4.10.jar'
fetch CTM-1.20.1-1.1.10.jar 3d7ec24d1cf7f9ea4ef0714a4ebee30d7bb1268e46510d22055033cf6454f7bc 'https://cursemaven.com/curse/maven/ctm-267602/5983309/ctm-267602-5983309.jar'
OUT="$(mktemp -d build/port-optional-test.XXXXXX)"
CP="$TASK_OPTIONAL_MAIN_CLASSES:$(<build/port-classpath.txt):$CACHE/*"
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" scripts/port/checks/OptionalIntegrationCheck.java
"$JAVA_CMD" -cp "$OUT:$CP" OptionalIntegrationCheck
