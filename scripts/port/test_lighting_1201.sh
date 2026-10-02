#!/usr/bin/env bash
# Headless production geometry and AO-target checks. No client or game server is started.
set -euo pipefail
cd "$(dirname "$0")/../.."
TASK_LIGHTING_MAIN_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
if [ ! -f build/port-classpath.txt ] || [ ! -f "$TASK_LIGHTING_MAIN_CLASSES/com/stardew/craft/StardewCraft.class" ]; then
    echo "Run ./gradlew classes printCompileClasspath first to prepare tracked sources and dependency classes." >&2
    exit 1
fi
JAVAC_CMD=javac
JAVA_CMD=java
TASK_LIGHTING_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
if [ -z "$TASK_LIGHTING_JDK" ] && [ -d "$HOME/.gradle/jdks" ]; then
    TASK_LIGHTING_JAVAC="$(find "$HOME/.gradle/jdks" -type f -path '*/bin/javac' -print -quit)"
    [ -z "$TASK_LIGHTING_JAVAC" ] || TASK_LIGHTING_JDK="${TASK_LIGHTING_JAVAC%/bin/javac}"
fi
if [ -n "$TASK_LIGHTING_JDK" ]; then
    JAVAC_CMD="$TASK_LIGHTING_JDK/bin/javac"
    JAVA_CMD="$TASK_LIGHTING_JDK/bin/java"
fi
OUT="$(mktemp -d build/port-lighting-test.XXXXXX)"
CP="$TASK_LIGHTING_MAIN_CLASSES:$(<build/port-classpath.txt)"
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" \
    src/main/java/com/stardew/craft/client/render/SpatialBlockModelRenderer.java \
    src/main/java/com/stardew/craft/mixin/PortAmbientOcclusionSampleMixin.java \
    src/main/java/com/stardew/craft/mixin/PortMapDecorSpatialLightingMixin.java \
    scripts/port/checks/AmbientOcclusionSampleCheck.java scripts/port/checks/SpatialLightingCheck.java
"$JAVA_CMD" -cp "$OUT:$CP" AmbientOcclusionSampleCheck
"$JAVA_CMD" -cp "$OUT:$CP" com.stardew.craft.client.render.SpatialLightingCheck
