#!/usr/bin/env bash
# Actual Mixin-woven rendering fixtures; in-memory registry bootstrap, no client/server/save/GPU.
set -euo pipefail
cd "$(dirname "$0")/../.."
TASK_RENDER_MAIN_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
if [ ! -f build/port-classpath.txt ] || [ ! -f "$TASK_RENDER_MAIN_CLASSES/com/stardew/craft/StardewCraft.class" ]; then
    echo "Run ./gradlew classes printCompileClasspath first." >&2
    exit 1
fi
TASK_RENDER_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
if [ -z "$TASK_RENDER_JDK" ] && [ -d "$HOME/.gradle/jdks" ]; then
    TASK_RENDER_JAVAC="$(find "$HOME/.gradle/jdks" -type f -path '*/bin/javac' -print -quit)"
    [ -z "$TASK_RENDER_JAVAC" ] || TASK_RENDER_JDK="${TASK_RENDER_JAVAC%/bin/javac}"
fi
JAVAC_CMD=javac
JAVA_CMD=java
if [ -n "$TASK_RENDER_JDK" ]; then
    JAVAC_CMD="$TASK_RENDER_JDK/bin/javac"
    JAVA_CMD="$TASK_RENDER_JDK/bin/java"
fi
OUT="$(mktemp -d build/port-render-geometry.XXXXXX)"
CP="$TASK_RENDER_MAIN_CLASSES:$(<build/port-classpath.txt)"
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" \
    scripts/port/checks/RenderGeometryCheck.java scripts/port/checks/RenderNormalsFixture.java \
    scripts/port/checks/JsonBakingCheck.java
PORT_MAIN_CLASSES="$TASK_RENDER_MAIN_CLASSES" PORT_MIXIN_DUMP_DIR="$OUT/woven" JAVA17_HOME="$TASK_RENDER_JDK" \
    bash scripts/port/test_mixin_application_1201.sh native
"$JAVA_CMD" -cp "$OUT:$CP" RenderGeometryCheck "$OUT/woven" RenderNormalsFixture JsonBakingCheck
echo "Woven runtime fixture output: $OUT"
