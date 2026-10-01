#!/usr/bin/env python3
"""Idempotent: DFU 8 strict `codec.optionalFieldOf(name[, default])` -> PortCodecs.optionalFieldOf(codec, ...).

    python3 scripts/port/strict_optional_fields_1201.py <file-or-dir> ...

DFU 6 (1.20.1) optionalFieldOf silently drops a present field that fails to parse; DFU 8 (1.21) reports the
error (`lenientOptionalFieldOf` is the old behaviour; the mod does not use it, the script stops if it appears).
Receivers may span lines (`Codec.STRING\n    .listOf()\n    .optionalFieldOf("x", List.of())`).
"""
from __future__ import annotations

import importlib.util
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("fix_errors_1201", HERE / "fix_errors_1201.py")
_fx = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_fx)

HELPER = "com.stardew.craft.port.PortCodecs.optionalFieldOf"
CALL = re.compile(r"\.\s*optionalFieldOf\s*\(")
LENIENT = re.compile(r"\.(\s*)lenientOptionalFieldOf(\s*)\(")


def adapt(text: str) -> tuple[str, int]:
    count = 0
    while True:
        # rightmost first keeps earlier offsets valid
        matches = [m for m in CALL.finditer(text) if not text[:m.start()].endswith("PortCodecs")]
        if not matches:
            break
        m = matches[-1]
        start = _fx.receiver_start(text, m.start())
        recv = text[start:m.start()].rstrip()
        if not recv:
            raise SystemExit(f"no receiver at offset {m.start()}")
        close = _fx.match_forward(text, m.end() - 1)
        args = text[m.end():close].strip()
        text = text[:start] + HELPER + "(" + recv + ", " + args + ")" + text[close + 1:]
        count += 1
    if LENIENT.search(text):
        raise SystemExit("lenientOptionalFieldOf: map to DFU 6 optionalFieldOf by hand (not handled here)")
    return text, count


def main(argv) -> int:
    changed = total = 0
    for target in map(Path, argv):
        for path in ([target] if target.is_file() else sorted(target.rglob("*.java"))):
            old = path.read_text(encoding="utf-8")
            if "optionalFieldOf" not in old:
                continue
            new, n = adapt(old)
            if new != old:
                path.write_text(new, encoding="utf-8")
                changed += 1
                total += n
    print(f"rewrote {total} optional fields in {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
