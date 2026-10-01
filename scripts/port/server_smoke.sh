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
    grep -qE 'Done \([0-9.]+s\)!|Failed to start|Exception in server tick loop|BUILD FAILED' "$LOG" && break
    sleep 1
  done
  sleep 20   # let a few seconds of ticks run (farm/valley setup, NPC spawning)
  PID=$(pgrep -f "stardewcraft.runTag=$TAG" | head -1)
  [ -z "$PID" ] && { echo "server process not found"; kill $GRADLE; wait $GRADLE; continue; }
  kill -TERM "$PID" 2>/dev/null
  wait $GRADLE
  echo "== run $i: $(grep -cE 'Done \(' "$LOG") Done, saved: $(grep -cE 'All dimensions are saved|Saving worlds' "$LOG")"
  grep -E '/(ERROR|FATAL)\]|Exception' "$LOG" | grep -vE 'server.properties|NoSuchFileException' | cut -c1-240 | sort | uniq -c | sort -rn | head -15
done
