#!/usr/bin/env python3
"""Package a successful Win32 build without the game's executable or assets."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = "RE4-Movimento-Livre-v0.1.1-EXPERIMENTAL"


def package(upstream, output):
    dll = upstream / "Release/dinput8.dll"
    data = dll.read_bytes()
    if data[:2] != b"MZ":
        raise ValueError("Not a Windows DLL")
    pe = struct.unpack_from("<I", data, 0x3C)[0]
    if data[pe:pe + 4] != b"PE\0\0" or struct.unpack_from("<H", data, pe + 4)[0] != 0x14C:
        raise ValueError("RE4 requires a Win32/x86 DLL")
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        stage = Path(directory) / NAME
        binary = stage / "Bin32"
        binary.mkdir(parents=True)
        shutil.copy2(dll, binary / "dinput8.dll")
        config = (upstream / "settings/settings.ini").read_text(encoding="utf-8-sig")
        for old, new in [
            ("UseMouseTurning = true", "UseMouseTurning = false"),
            ("ResetCameraAfterUsingWeapons = true", "ResetCameraAfterUsingWeapons = false"),
            ("ResetCameraAfterUsingKnife = true", "ResetCameraAfterUsingKnife = false"),
            ("ResetCameraWhenRunning = true", "ResetCameraWhenRunning = false"),
            ("NeverCheckForUpdates = false", "NeverCheckForUpdates = true"),
        ]:
            if config.count(old) != 1:
                raise ValueError("Unexpected configuration: " + old)
            config = config.replace(old, new)
        (binary / "dinput8.ini").write_text(config, encoding="utf-8")
        (binary / "re4_tweaks").mkdir()
        shutil.copy2(upstream / "settings/trainer_settings.ini", binary / "re4_tweaks/trainer.ini")
        shutil.copytree(upstream / "dist/Bin32/re4_tweaks", binary / "re4_tweaks", dirs_exist_ok=True)
        shutil.copy2(ROOT / "re4-modern-controls.ini", binary / "re4-modern-controls.ini")
        for name in ("README.md", "LICENSE.txt", "LICENSE-re4_tweaks.txt", "upstream.json"):
            shutil.copy2(ROOT / name, stage / ("LEIA-ME.txt" if name == "README.md" else name))
        shutil.copytree(ROOT / "third-party", stage / "third-party", ignore=shutil.ignore_patterns("*.ttf"))
        # Preserve the notices available in the pinned dependency checkouts.
        notices = stage / "third-party-notices"
        for source in (upstream / "external").rglob("*"):
            if source.is_file() and source.name.lower().split(".")[0] in {
                "license", "licence", "copying", "notice", "ftl", "ofl"}:
                dest = notices / source.relative_to(upstream / "external")
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, dest)
        artifact = output / (NAME + ".zip")
        with zipfile.ZipFile(artifact, "w", zipfile.ZIP_DEFLATED) as archive:
            for source in sorted(stage.rglob("*")):
                if source.is_file(): archive.write(source, source.relative_to(stage.parent))
        metadata = {"status": "EXPERIMENTAL_NOT_VALIDATED_IN_GAME", "file": artifact.name,
                    "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                    "dll_sha256": hashlib.sha256(data).hexdigest(), "machine": "x86",
                    "upstream_revision": json.loads((ROOT / "upstream.json").read_text())["revision"],
                    "gameplay_tested": False}
        (output / "build-status.json").write_text(json.dumps(metadata, indent=2) + "\n")
        print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    package(args.upstream, args.output)
