#!/usr/bin/env python3
"""Reuse the tracked 32x12x32 pure-air fixture in the enabled Ginger Island namespace."""

import gzip
import io
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "src/main/resources/data/stardewcraft_dog_house/structures/dog_house_test.nbt"
TARGET = ROOT / "src/main/resources/data/stardewcraft_ginger_assets/structures/asset_test.nbt"


def main():
    # Preserve the existing structure schema/palette; only its resource namespace differs.
    payload = gzip.decompress(SOURCE.read_bytes())
    compressed = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=compressed, mtime=0) as output:
        output.write(payload)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(compressed.getvalue())
    print(f"Generated {TARGET.relative_to(ROOT)} from {SOURCE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
