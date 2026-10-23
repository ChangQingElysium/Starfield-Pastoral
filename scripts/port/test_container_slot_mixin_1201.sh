#!/usr/bin/env bash
# Real accessor application plus production JVM super dispatch; no client, server, or installation JAR.
set -euo pipefail
cd "$(dirname "$0")/../.."
TASK_SLOT_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
TASK_SLOT_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
TASK_SLOT_SRG=$(python3 - <<'PYTHON'
import os
from pathlib import Path
for entry in Path('build/port-classpath.txt').read_text().split(os.pathsep):
    jar = Path(entry)
    if jar.name.startswith('forge-') and '_mapped_' in jar.parent.name:
        version = jar.parent.name.split('_mapped_')[0]
        candidate = jar.parent.parent / version / (jar.stem.split('_mapped_')[0] + '-srg.jar')
        if candidate.is_file():
            print(candidate)
            break
else:
    raise SystemExit('Missing Forge SRG jar; run classes printCompileClasspath first.')
PYTHON
)
for INPUT in build/port-classpath.txt build/createSrgToMcp/output.srg build/tmp/compileJava/stardewcraft.refmap.json "$TASK_SLOT_SRG"; do
    [ -f "$INPUT" ] || { echo "Missing $INPUT; run classes printCompileClasspath first." >&2; exit 1; }
done
for RELATIVE in com/stardew/craft/mixin/PortContainerScreenAccessor com/stardew/craft/port/PortScreens com/stardew/craft/port/PortContainerScreen com/stardew/craft/port/PortScreen; do
    SOURCE="src/main/java/$RELATIVE.java"
    [ -f "$SOURCE" ] || SOURCE="src/port/java/$RELATIVE.java"
    COMPILED="$TASK_SLOT_CLASSES/$RELATIVE.class"
    [ -f "$COMPILED" ] && [ ! "$SOURCE" -nt "$COMPILED" ] || { echo "Run classes first: missing/stale $RELATIVE" >&2; exit 1; }
done
OUT="$(mktemp -d build/container-slot-mixin.XXXXXX)"
CP="$TASK_SLOT_CLASSES:$(<build/port-classpath.txt)"
"${TASK_SLOT_JDK:+$TASK_SLOT_JDK/bin/}javac" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" \
    scripts/port/checks/OfflineMixinApplicationCheck.java scripts/port/checks/ContainerSlotMixinCheck.java
mkdir -p "$OUT/META-INF/services"
echo 'ContainerSlotMixinCheck$Service' > "$OUT/META-INF/services/org.spongepowered.asm.service.IMixinService"
cp scripts/port/checks/mixin-services/org.spongepowered.asm.service.IGlobalPropertyService "$OUT/META-INF/services/"
for MODE in mapped srg; do
    MODE_OUT="$OUT/$MODE"; mkdir -p "$MODE_OUT"
    MODE_CP="$CP"
    if [ "$MODE" = srg ]; then MODE_CP="$TASK_SLOT_CLASSES:$TASK_SLOT_SRG:$(<build/port-classpath.txt)"; fi
    "${TASK_SLOT_JDK:+$TASK_SLOT_JDK/bin/}java" -Dport.mixin.side=CLIENT -Dmixin.env.disableRefMap="$([ "$MODE" = mapped ] && echo true || echo false)" \
        -cp "$MODE_OUT:$OUT:$MODE_CP" ContainerSlotMixinCheck "$MODE" "$MODE_OUT" "$TASK_SLOT_CLASSES" > "$MODE_OUT/check.log" 2>&1 || \
        { tail -n 20 "$MODE_OUT/check.log"; exit 1; }
    tail -n 1 "$MODE_OUT/check.log"
done
echo "Detailed logs: $OUT"
