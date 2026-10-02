#!/usr/bin/env python3
"""Flag 1.21.1 idioms that still COMPILE on 1.20.1 but silently behave differently.

    python3 scripts/port/lint_port.py [paths...]     (default: src/main/java; exit 1 on findings)

Run after every sync from main and before every checkpoint. Each rule names the port helper
that reproduces the 1.21.1 behaviour.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

RULES = [
    (re.compile(r"(?<![\w.])(?:sprite|icon|tex|texture|atlasSprite|particleIcon|\w*Sprite)\s*\.\s*get[UV]\s*\("),
     "TextureAtlasSprite#getU/getV take 0..1 in 1.21 but 0..16 in 1.20.1: use PortSprites.getU/getV"),
    (re.compile(r"(?<![\w.])NbtUtils\s*\.\s*(?:write|read)BlockPos\s*\("),
     "NbtUtils block-pos layout differs (1.21 int array): use PortNbtUtils"),
    (re.compile(r"(?<!PortCodecs)\.\s*optionalFieldOf\s*\("),
     "DFU 6 optionalFieldOf drops invalid present fields silently: use PortCodecs.optionalFieldOf "
     "(scripts/port/strict_optional_fields_1201.py)"),
    (re.compile(r"(?<![\w.])Math\s*\.\s*clamp\s*\("),
     "JDK 21 only: use PortJava.clamp"),
    (re.compile(r"(?<![\w.])(?:java\.lang\.)?(?:Strict)?Math\s*\.\s*ceilDiv\s*\("),
     "Java 17 has no ceiling division: use PortJava.ceilDiv"),
    (re.compile(r"^(?!.*PortVertex).*\.\s*addVertex\s*\("),
     "1.21 vertex chain: convert with scripts/port/adapt_vertex_api.py (PortVertex)"),
    (re.compile(r"\.\s*cameraOrientation\s*\(\s*\)|\.\s*getCamera\s*\(\s*\)\s*\.\s*rotation\s*\(\s*\)"),
     "Camera billboard basis flipped in 1.20.5: use PortCamera.cameraOrientation/rotation"),
]
SKIP_DIRS = ("src/port/",)
SKIP_FILES = {"PortSprites.java", "PortVertex.java", "PortNbtUtils.java", "PortCodecs.java", "PortJava.java"}


def main(argv) -> int:
    targets = [Path(a) for a in argv] or [ROOT / "src/main/java"]
    findings = 0
    for target in targets:
        for path in ([target] if target.is_file() else sorted(target.rglob("*.java"))):
            rel = str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)
            if rel.startswith(SKIP_DIRS) or path.name in SKIP_FILES:
                continue
            for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                stripped = line.strip()
                if stripped.startswith(("//", "*", "import ")):
                    continue
                for pattern, message in RULES:
                    if message and pattern.search(line):
                        print(f"{rel}:{lineno}: {message}\n    {stripped[:160]}")
                        findings += 1
    print(f"{findings} finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
