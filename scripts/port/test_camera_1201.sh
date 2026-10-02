#!/usr/bin/env bash
# Source-derived regression checks; needs the port classpath, never starts a client.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ ! -f build/port-classpath.txt ]; then
    echo "Run ./gradlew printCompileClasspath first to prepare the dependency classpath." >&2
    exit 1
fi
JAVAC_CMD=javac
JAVA_CMD=java
TASK_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
if [ -z "$TASK_JDK" ] && [ -d "$HOME/.gradle/jdks" ]; then
    # Gradle's downloaded toolchain can be used even when java is not installed on PATH.
    TASK_JAVAC="$(find "$HOME/.gradle/jdks" -type f -path '*/bin/javac' -print -quit)"
    [ -z "$TASK_JAVAC" ] || TASK_JDK="${TASK_JAVAC%/bin/javac}"
fi
if [ -n "$TASK_JDK" ]; then
    JAVAC_CMD="$TASK_JDK/bin/javac"
    JAVA_CMD="$TASK_JDK/bin/java"
fi
OUT=build/port-camera-test
mkdir -p "$OUT"
CP="$(cat build/port-classpath.txt)"
"$JAVAC_CMD" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" \
    src/port/java/com/stardew/craft/port/PortCamera.java scripts/port/tests/PortCameraTest.java
"$JAVA_CMD" -cp "$OUT:$CP" PortCameraTest
python3 -B scripts/port/tests/test_camera_rewrite.py
