#!/usr/bin/env bash
# Focused real Mixin application in official-name addon development and SRG runtime contexts.
# Uses fresh classes, generated mappings/refmap and Forge dependency bytecode; no game/JAR build.
# CI also checks the already-built default install JAR with --check-install-jar.
set -euo pipefail
cd "$(dirname "$0")/../.."
case "${1:-}" in
    '') ;;
    --check-install-jar)
        python3 - <<'PYTHON'
from pathlib import Path
import json
import zipfile

properties = dict(line.split('=', 1) for line in Path('gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
mod_id = properties['mod_id'].strip()
version = properties['mod_version'].strip()
refmap_name = mod_id + '.refmap.json'
generated = Path('build/tmp/compileJava') / refmap_name
install_jar = Path('build/libs') / (mod_id + '-' + version + '.jar')
if not generated.is_file() or not install_jar.is_file():
    raise SystemExit('Missing generated refmap or default install JAR; run build check first.')
with zipfile.ZipFile(install_jar) as jar:
    config = json.loads(jar.read(mod_id + '.mixins.json'))
    if config.get('refmap') != refmap_name or jar.namelist().count(refmap_name) != 1:
        raise SystemExit('Default install JAR does not contain its declared Mixin refmap exactly once.')
    if jar.read(refmap_name) != generated.read_bytes():
        raise SystemExit('Default install JAR refmap differs from the current compileJava output.')
print('PASS default install JAR: declared refmap matches current compileJava output')
PYTHON
        ;;
    *) echo "Usage: bash $0 [--check-install-jar]" >&2; exit 1 ;;
esac
TASK_GUI_CLASSES="${PORT_MAIN_CLASSES:-build/classes/java/main}"
TASK_GUI_JDK="${JAVA17_HOME:-${JAVA_HOME:-}}"
# Resolve the generated Forge dependency from the current classpath, including clean CI caches.
TASK_GUI_SRG=$(python3 - <<'PYTHON'
import os
from pathlib import Path
for entry in Path('build/port-classpath.txt').read_text().split(os.pathsep):
    jar = Path(entry)
    if jar.name.startswith('forge-') and '_mapped_' in jar.parent.name:
        version = jar.parent.name.split('_mapped_')[0]
        filename = jar.stem.split('_mapped_')[0] + '-srg.jar'
        candidate = jar.parent.parent / version / filename
        if candidate.is_file():
            print(candidate)
            break
else:
    raise SystemExit('Missing generated Forge SRG jar; run classes printCompileClasspath first.')
PYTHON
)
for INPUT in build/port-classpath.txt build/createSrgToMcp/output.srg build/tmp/compileJava/stardewcraft.refmap.json "$TASK_GUI_SRG"; do
    [ -f "$INPUT" ] || { echo "Missing $INPUT; run classes printCompileClasspath first." >&2; exit 1; }
done
TASK_GUI_SOURCE=src/main/java/com/stardew/craft/mixin/GuiGraphicsStackCountScaleMixin.java
TASK_GUI_CLASS="$TASK_GUI_CLASSES/com/stardew/craft/mixin/GuiGraphicsStackCountScaleMixin.class"
[ -f "$TASK_GUI_CLASS" ] && [ ! "$TASK_GUI_SOURCE" -nt "$TASK_GUI_CLASS" ] || { echo "Run classes first: stack-count Mixin is missing/stale." >&2; exit 1; }
OUT="$(mktemp -d build/gui-stack-count-mixin.XXXXXX)"
CP="$TASK_GUI_CLASSES:$(<build/port-classpath.txt)"
"${TASK_GUI_JDK:+$TASK_GUI_JDK/bin/}javac" --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" \
    scripts/port/checks/OfflineMixinApplicationCheck.java scripts/port/checks/GuiStackCountMixinCheck.java
mkdir -p "$OUT/META-INF/services"
echo 'GuiStackCountMixinCheck$Service' > "$OUT/META-INF/services/org.spongepowered.asm.service.IMixinService"
cp scripts/port/checks/mixin-services/org.spongepowered.asm.service.IGlobalPropertyService "$OUT/META-INF/services/"
for MODE in mapped srg; do
    MODE_OUT="$OUT/$MODE"; mkdir -p "$MODE_OUT"
    MODE_CP="$CP"
    if [ "$MODE" = srg ]; then MODE_CP="$TASK_GUI_CLASSES:$TASK_GUI_SRG:$(<build/port-classpath.txt)"; fi
    "${TASK_GUI_JDK:+$TASK_GUI_JDK/bin/}java" -Dport.mixin.side=CLIENT -Dmixin.env.disableRefMap="$([ "$MODE" = mapped ] && echo true || echo false)" \
        -cp "$MODE_OUT:$OUT:$MODE_CP" GuiStackCountMixinCheck "$MODE" "$MODE_OUT" > "$MODE_OUT/check.log" 2>&1 || \
        { tail -n 15 "$MODE_OUT/check.log"; exit 1; }
    tail -n 1 "$MODE_OUT/check.log"
done
echo "Detailed logs: $OUT"
