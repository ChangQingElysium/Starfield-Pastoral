#!/usr/bin/env bash
# Dedicated-server smoke test: fresh directory -> start (world creation) -> stop/save -> restart -> stop.
# Shares the GameTest lock. Logs: build/server-smoke/run{1,2}.log
set -u
cd "$(dirname "$0")/../.."
LOCK=build/gametest.lock
until mkdir "$LOCK" 2>/dev/null; do sleep 5; done
trap 'rmdir "$LOCK"' EXIT
DIR=build/server-smoke/run; rm -rf build/server-smoke; mkdir -p "$DIR"
echo "eula=true" > "$DIR/eula.txt"
printf 'online-mode=false\nmotd=port-smoke\nspawn-protection=0\n' > "$DIR/server.properties"
for i in 1 2; do
  LOG="build/server-smoke/run$i.log"
  TAG="smoke-$$-$i"
  ./gradlew runServer "-PforgeRunDir=$DIR" "-PrunTag=$TAG" --console=plain > "$LOG" 2>&1 &
  GRADLE=$!
  for t in $(seq 1 600); do
    ! kill -0 "$GRADLE" 2>/dev/null && break
    grep -qE 'Done \([0-9.]+s\)!|Failed to start|Exception in server tick loop|BUILD FAILED' "$LOG" && break
    sleep 1
  done
  STARTED=0
  if grep -qE 'Done \([0-9.]+s\)!' "$LOG"; then
    STARTED=1
    sleep 20   # let ticks run (farm/valley setup, NPC spawning)
  fi
  PID=$(pgrep -f "stardewcraft.runTag=$TAG" | head -1)
  [ -z "$PID" ] && { echo "run $i: server process not found; see $LOG"; kill "$GRADLE" 2>/dev/null; wait "$GRADLE"; exit 1; }
  kill -TERM "$PID" 2>/dev/null
  for t in $(seq 1 60); do
    ! kill -0 "$GRADLE" 2>/dev/null && break
    sleep 1
  done
  if kill -0 "$GRADLE" 2>/dev/null; then
    echo "run $i: server shutdown timed out; see $LOG"
    kill -KILL "$PID" "$GRADLE" 2>/dev/null
    wait "$GRADLE"
    exit 1
  fi
  wait "$GRADLE"
  STATUS=$?
  # ForgeGradle reports our intentional SIGTERM as exit 143 even after the server's save hook completes.
  EXIT_OK=0
  [ "$STATUS" -eq 0 ] && EXIT_OK=1
  if [ "$STATUS" -eq 1 ] && grep -q 'finished with non-zero exit value 143' "$LOG"; then
    EXIT_OK=1
  fi
  if [ "$STARTED" -ne 1 ] || [ "$EXIT_OK" -ne 1 ] || ! grep -q 'All dimensions are saved' "$LOG"; then
    echo "run $i: startup/save failed (started=$STARTED, exit=$STATUS); see $LOG"
    exit 1
  fi
  echo "== run $i: $(grep -cE 'Done \(' "$LOG") Done, saved: $(grep -cE 'All dimensions are saved|Saving worlds' "$LOG")"
  grep -E '/(ERROR|FATAL)\]|Exception' "$LOG" | grep -vE 'server.properties|NoSuchFileException' | cut -c1-240 | sort | uniq -c | sort -rn | head -15
done
