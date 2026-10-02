#!/usr/bin/env bash
# Real mapped-bytecode Mixin/Extras application; does not launch Minecraft or build an installation jar.
set -euo pipefail
cd "$(dirname "$0")/../.."
TASK_MIXIN_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
TASK_MIXIN_MODE="${1:-native}"
if [ ! -f build/port-classpath.txt ] || [ ! -f "$TASK_MIXIN_CLASSES/com/stardew/craft/StardewCraft.class" ]; then
    echo "Run ./gradlew classes printCompileClasspath first." >&2
    exit 1
fi
TASK_MIXIN_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
JAVAC_CMD=javac
JAVA_CMD=java
if [ -n "$TASK_MIXIN_JDK" ]; then
    JAVAC_CMD="$TASK_MIXIN_JDK/bin/javac"
    JAVA_CMD="$TASK_MIXIN_JDK/bin/java"
fi
OUT="$(mktemp -d build/port-mixin-application.XXXXXX)"
CP="$TASK_MIXIN_CLASSES:$(<build/port-classpath.txt)"
SIDES="CLIENT SERVER"
DISABLE_REFMAP=true
if [ "$TASK_MIXIN_MODE" = optional ]; then
    # Run test_optional_integrations_1201.sh first to fetch and verify the pinned official dependency jars.
    TASK_MIXIN_SRG=build/fg_cache/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-srg.jar
    if [ ! -f "$TASK_MIXIN_SRG" ] || [ ! -f build/tmp/compileJava/stardewcraft.refmap.json ]; then
        echo "Missing Forge SRG bytecode or fresh compileJava refmap; prepare Forge dependencies/classes first." >&2
        exit 1
    fi
    CP="$TASK_MIXIN_CLASSES:$TASK_MIXIN_SRG:$(<build/port-classpath.txt):build/port-optional-audit/*"
    SIDES=CLIENT
    DISABLE_REFMAP=false
elif [ "$TASK_MIXIN_MODE" = embeddium-runtime ]; then
    if [ ! -f build/createMcpToSrg/output.tsrg ]; then
        echo "Missing generated official/SRG mapping; run classes first." >&2
        exit 1
    fi
    CP="$TASK_MIXIN_CLASSES:$(<build/port-classpath.txt):build/port-optional-audit/*"
    SIDES=CLIENT
elif [ "$TASK_MIXIN_MODE" != native ]; then
    echo "Usage: bash scripts/port/test_mixin_application_1201.sh [native|optional|embeddium-runtime]" >&2
    exit 1
fi
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" scripts/port/checks/OfflineMixinApplicationCheck.java
mkdir -p "$OUT/META-INF/services"
cp scripts/port/checks/mixin-services/org.spongepowered.asm.service.IMixinService "$OUT/META-INF/services/"
cp scripts/port/checks/mixin-services/org.spongepowered.asm.service.IGlobalPropertyService "$OUT/META-INF/services/"
FAILED=0
for SIDE in $SIDES; do
    if "$JAVA_CMD" -Dport.mixin.side="$SIDE" -Dmixin.env.disableRefMap="$DISABLE_REFMAP" -cp "$OUT:$CP" \
            OfflineMixinApplicationCheck src/main/resources/stardewcraft.mixins.json "$OUT" "$TASK_MIXIN_CLASSES" "$TASK_MIXIN_MODE" > "$OUT/$SIDE.log" 2>&1; then
        tail -1 "$OUT/$SIDE.log"
    else
        FAILED=1
        echo "FAIL: $SIDE application; see $OUT/$SIDE.log"
        tail -4 "$OUT/$SIDE.log"
    fi
done
echo "Detailed application logs: $OUT"
exit "$FAILED"
