#!/usr/bin/env bash
# Fast full-tree compile check (~10s) for the port. Writes errors to build/port-javac.log.
# Parallel runs: set PORT_JAVAC_OUT and PORT_JAVAC_LOG to private paths.
# Refresh the classpath after dependency changes: ./gradlew printCompileClasspath
set -u
cd "$(dirname "$0")/../.."
J17="${JAVA17_HOME:-$(ls -d ~/.gradle/jdks/eclipse_adoptium-17-*/jdk-17*/Contents/Home | head -1)}"
OUT="${PORT_JAVAC_OUT:-build/port-javac}"
LOG="${PORT_JAVAC_LOG:-build/port-javac.log}"
SRCS="$OUT.sources.txt"
rm -rf "$OUT"; mkdir -p "$OUT"
find src/main/java src/port/java -name '*.java' > "$SRCS"
"$J17/bin/javac" -proc:none -nowarn -encoding UTF-8 -Xmaxerrs 100000 -d "$OUT" \
  -cp "$(cat build/port-classpath.txt)" @"$SRCS" > "$LOG" 2>&1
status=$?
grep -c ': error:' "$LOG" | sed 's/^/errors: /'
exit $status
