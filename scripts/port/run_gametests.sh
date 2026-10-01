#!/usr/bin/env bash
# Run GameTests for the given template namespaces with a fresh world, serialised by a lock so
# several agents can share this worktree.  Prints the summary and the failing test lines.
#   scripts/port/run_gametests.sh <ns1,ns2|ALL> <name>      (log: build/gt-<name>.log)
set -u
cd "$(dirname "$0")/../.."
NS="$1"; NAME="$2"
[ "$NS" = "ALL" ] && NS=""
LOCK=build/gametest.lock
mkdir -p build
until mkdir "$LOCK" 2>/dev/null; do sleep 5; done
trap 'rmdir "$LOCK"' EXIT
RUN="build/gt-run-$NAME"; LOG="build/gt-$NAME.log"
rm -rf "$RUN"; mkdir -p "$RUN"
./gradlew runGameTestServer "-PgameTestNamespaces=$NS" "-PgameTestRunDir=$RUN" --console=plain > "$LOG" 2>&1
grep -E 'GAME TESTS COMPLETE|required tests (failed|passed)|Failed to load datapacks|BUILD FAILED|error:' "$LOG" | head -20
grep -E 'LogTestReporter.*failed!' "$LOG" | sed -E 's/.*LogTestReporter\]: //' | cut -c1-300
