#!/usr/bin/env bash
# Run GameTests for the given template namespaces with a fresh world, serialised by a lock so
# several agents can share this worktree.  Prints the summary and the failing test lines.
#   scripts/port/run_gametests.sh <ns1,ns2|ALL> <name>      (log: build/gt-<name>.log)
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <ns1,ns2|ALL> <name>" >&2
  exit 2
fi
NS="$1"; NAME="$2"
if [[ ! "$NAME" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "Test run name must contain only letters, digits, underscores or hyphens." >&2
  exit 2
fi
[ "$NS" = "ALL" ] && NS=""
LOCK=build/gametest.lock
mkdir -p build
until mkdir "$LOCK" 2>/dev/null; do sleep 5; done
trap 'rmdir "$LOCK"' EXIT
RUN="$(mktemp -d "build/gt-run-$NAME.XXXXXX")"; LOG="build/gt-$NAME.log"
if ./gradlew runGameTestServer "-PgameTestNamespaces=$NS" "-PgameTestRunDir=$RUN" --console=plain > "$LOG" 2>&1; then
  RUN_STATUS=0
else
  RUN_STATUS=$?
fi
grep -E 'GAME TESTS COMPLETE|required tests (failed|passed)|Failed to load datapacks|BUILD FAILED|error:' "$LOG" | head -20 || true
grep -E 'LogTestReporter.*failed!' "$LOG" | sed -E 's/.*LogTestReporter\]: //' | cut -c1-300 || true
python3 -B scripts/port/verify_gametest_run.py "$LOG" "$RUN_STATUS"
