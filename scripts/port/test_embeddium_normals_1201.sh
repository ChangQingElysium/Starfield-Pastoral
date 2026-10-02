#!/usr/bin/env bash
# Execute the locked renderer's actually woven native-memory writers; no client/GL/installation.
set -euo pipefail
cd "$(dirname "$0")/../.."
TASK_EMB_MAIN_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
TASK_EMB_MAPPING=build/createMcpToSrg/output.tsrg
if [ ! -f build/port-classpath.txt ] || [ ! -f "$TASK_EMB_MAPPING" ] || [ ! -f "$TASK_EMB_MAIN_CLASSES/com/stardew/craft/StardewCraft.class" ]; then
    echo "Run ./gradlew classes printCompileClasspath first." >&2
    exit 1
fi
TASK_EMB_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
if [ -z "$TASK_EMB_JDK" ] && [ -d "$HOME/.gradle/jdks" ]; then
    TASK_EMB_JAVAC="$(find "$HOME/.gradle/jdks" -type f -path '*/bin/javac' -print -quit)"
    [ -z "$TASK_EMB_JAVAC" ] || TASK_EMB_JDK="${TASK_EMB_JAVAC%/bin/javac}"
fi
JAVAC_CMD=javac
JAVA_CMD=java
if [ -n "$TASK_EMB_JDK" ]; then
    JAVAC_CMD="$TASK_EMB_JDK/bin/javac"
    JAVA_CMD="$TASK_EMB_JDK/bin/java"
fi
# Maintained downloader verifies exact official release hashes without installing any mod.
PORT_MAIN_CLASSES="$TASK_EMB_MAIN_CLASSES" JAVA17_HOME="$TASK_EMB_JDK" bash scripts/port/test_optional_integrations_1201.sh
OUT="$(mktemp -d build/port-embeddium-normals.XXXXXX)"
CP="$TASK_EMB_MAIN_CLASSES:$(<build/port-classpath.txt):build/port-optional-audit/*"
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" \
    scripts/port/checks/RenderGeometryCheck.java scripts/port/checks/EmbeddiumNormalsFixture.java \
    scripts/port/checks/OfflineMixinApplicationCheck.java
mkdir -p "$OUT/META-INF/services"
cp scripts/port/checks/mixin-services/org.spongepowered.asm.service.IMixinService "$OUT/META-INF/services/"
cp scripts/port/checks/mixin-services/org.spongepowered.asm.service.IGlobalPropertyService "$OUT/META-INF/services/"
PORT_MAIN_CLASSES="$TASK_EMB_MAIN_CLASSES" PORT_MIXIN_DUMP_DIR="$OUT/woven" JAVA17_HOME="$TASK_EMB_JDK" \
    bash scripts/port/test_mixin_application_1201.sh embeddium-runtime
"$JAVA_CMD" -cp "$OUT:$CP" RenderGeometryCheck "$OUT/woven" --embeddium "$TASK_EMB_MAPPING" EmbeddiumNormalsFixture
echo "Woven renderer payload fixture output: $OUT"
