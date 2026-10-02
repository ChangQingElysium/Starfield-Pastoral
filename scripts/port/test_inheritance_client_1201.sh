#!/usr/bin/env bash
# Production input/body and native-bytecode checks, without starting a Minecraft client.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ ! -f build/port-classpath.txt ] || [ ! -f build/classes/java/main/com/stardew/craft/StardewCraft.class ]; then
    echo "Run ./gradlew classes printCompileClasspath first to prepare production sources and dependencies." >&2
    exit 1
fi
TASK_INPUT_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
JAVAC_CMD=javac
JAVA_CMD=java
if [ -n "$TASK_INPUT_JDK" ]; then
    JAVAC_CMD="$TASK_INPUT_JDK/bin/javac"
    JAVA_CMD="$TASK_INPUT_JDK/bin/java"
fi
OUT="$(mktemp -d build/port-inheritance-client-test.XXXXXX)"
CP="build/classes/java/main:$(<build/port-classpath.txt)"
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" \
    src/main/java/com/stardew/craft/client/gui/common/GuiLayoutMath.java \
    src/port/java/com/stardew/craft/port/PortMouseMovement.java \
    src/main/java/com/stardew/craft/mixin/PortContainerEventHandlerMixin.java \
    scripts/port/checks/InheritanceClientInputCheck.java
"$JAVA_CMD" -cp "$OUT:$CP" com.stardew.craft.port.InheritanceClientInputCheck
