#!/usr/bin/env python3
"""Package a preview and read-only inspector, explicitly not an installed game mod."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = "Nioh-Luvas-Prototipo-v0.1.0"


def package(exe, output):
    binary = exe.read_bytes()
    if len(binary) < 256 or binary[:2] != b"MZ":
        raise ValueError("Missing compiled Windows inspector")
    pe = struct.unpack_from("<I", binary, 0x3c)[0]
    if binary[pe:pe+4] != b"PE\0\0" or struct.unpack_from("<H", binary, pe+4)[0] != 0x8664:
        raise ValueError("Inspector must be Windows x64")
    files = {"Nioh-Inspecionar.exe": binary, "Previa.html": (ROOT / "preview/Previa.html").read_bytes(),
             "LEIA-ME.txt": (ROOT / "LEIA-ME.txt").read_bytes(), "LICENSE.txt": (ROOT / "LICENSE.txt").read_bytes()}
    output.mkdir(parents=True, exist_ok=True)
    target = output / (NAME + ".zip")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(NAME + "/" + name, data)
    metadata = {"status": "PROTOTYPE_NO_GAME_INTEGRATION", "file": target.name,
                "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                "inspector_sha256": hashlib.sha256(binary).hexdigest(),
                "gameplay_tested": False, "game_category_created": False,
                "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
    (output / "build-status.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("exe", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    package(args.exe, args.output)
