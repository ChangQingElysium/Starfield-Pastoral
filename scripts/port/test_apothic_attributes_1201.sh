#!/usr/bin/env bash
# Weave the production Player mixin with the pinned official Apothic release; no game window or jar build.
set -euo pipefail
cd "$(dirname "$0")/../.."
TASK_APOTHIC_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
TASK_APOTHIC_ROOT="${PORT_APOTHIC_OUTPUT:-build/port-apothic-compat}"
TASK_APOTHIC_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
TASK_APOTHIC_MODE="${1:-fixed}"
if [ ! -f build/port-classpath.txt ] || [ ! -f build/createSrgToMcp/output.srg ] \
        || [ ! -f "$TASK_APOTHIC_CLASSES/com/stardew/craft/mixin/PlayerSweepAttackMixin.class" ]; then
    echo 'Run ./gradlew classes printCompileClasspath first.' >&2
    exit 1
fi
if [ src/main/java/com/stardew/craft/mixin/PlayerSweepAttackMixin.java -nt "$TASK_APOTHIC_CLASSES/com/stardew/craft/mixin/PlayerSweepAttackMixin.class" ]; then
    echo 'Production PlayerSweepAttackMixin is stale; run classes first.' >&2
    exit 1
fi
mkdir -p "$TASK_APOTHIC_ROOT/dependencies"
TASK_APOTHIC_JAR="$TASK_APOTHIC_ROOT/dependencies/ApothicAttributes-1.20.1-1.3.7.jar"
if [ ! -f "$TASK_APOTHIC_JAR" ]; then
    curl --fail --location --retry 2 \
        'https://cdn.modrinth.com/data/DGaH8Rh0/versions/fwL9CWGd/ApothicAttributes-1.20.1-1.3.7.jar' \
        --output "$TASK_APOTHIC_JAR"
fi
TASK_APOTHIC_HASH="$(shasum -a 512 "$TASK_APOTHIC_JAR")"
if [ "${TASK_APOTHIC_HASH%% *}" != d0b7a9dac9e0d34cdec49afd9a3c9f5382dcfac69b18a7c7a600d4537cf2653368a00333389c8e7c9fdacf952e0e4f921464309e257a3dd992f1ba26f4ce6836 ]; then
    echo 'Official Apothic Attributes binary checksum mismatch.' >&2
    exit 1
fi
JAVAC_CMD=javac
JAVA_CMD=java
if [ -n "$TASK_APOTHIC_JDK" ]; then
    JAVAC_CMD="$TASK_APOTHIC_JDK/bin/javac"
    JAVA_CMD="$TASK_APOTHIC_JDK/bin/java"
fi
TASK_APOTHIC_OUT="$(mktemp -d "$TASK_APOTHIC_ROOT/check.XXXXXX")"
TASK_APOTHIC_CP="$TASK_APOTHIC_CLASSES:$(<build/port-classpath.txt):$TASK_APOTHIC_JAR"
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$TASK_APOTHIC_CP" -d "$TASK_APOTHIC_OUT" \
    scripts/port/checks/OfflineMixinApplicationCheck.java scripts/port/checks/ApothicSweepMixinCheck.java
mkdir -p "$TASK_APOTHIC_OUT/META-INF/services"
echo 'ApothicSweepMixinCheck$Service' > "$TASK_APOTHIC_OUT/META-INF/services/org.spongepowered.asm.service.IMixinService"
cp scripts/port/checks/mixin-services/org.spongepowered.asm.service.IGlobalPropertyService "$TASK_APOTHIC_OUT/META-INF/services/"
TASK_APOTHIC_ORDERS='none apoth-first stardew-first'
TASK_APOTHIC_EXPECT=fixed
if [ "$TASK_APOTHIC_MODE" = expect-conflict ]; then
    TASK_APOTHIC_ORDERS='apoth-first stardew-first'
    TASK_APOTHIC_EXPECT=expect-conflict
elif [ "$TASK_APOTHIC_MODE" != fixed ]; then
    echo 'Usage: test_apothic_attributes_1201.sh [fixed|expect-conflict]' >&2
    exit 1
fi
for TASK_APOTHIC_ORDER in $TASK_APOTHIC_ORDERS; do
    if "$JAVA_CMD" -Dport.mixin.side=CLIENT -Dmixin.env.disableRefMap=true -cp "$TASK_APOTHIC_OUT:$TASK_APOTHIC_CP" \
            ApothicSweepMixinCheck "$TASK_APOTHIC_ORDER" "$TASK_APOTHIC_OUT" "$TASK_APOTHIC_EXPECT" \
            > "$TASK_APOTHIC_OUT/$TASK_APOTHIC_ORDER.log" 2>&1; then
        tail -1 "$TASK_APOTHIC_OUT/$TASK_APOTHIC_ORDER.log"
    else
        tail -12 "$TASK_APOTHIC_OUT/$TASK_APOTHIC_ORDER.log" >&2
        echo "Detailed compatibility log: $TASK_APOTHIC_OUT/$TASK_APOTHIC_ORDER.log" >&2
        exit 1
    fi
done
echo "Detailed compatibility logs: $TASK_APOTHIC_OUT"
