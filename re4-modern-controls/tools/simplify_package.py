#!/usr/bin/env python3
"""Repackage the verified 0.1.3 build with a short guide and consolidated notices."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = "RE4-Movimento-Livre-v0.1.3-Simples"
RUNTIME = ("dinput8.dll", "dinput8.ini", "re4-modern-controls.ini")


def simplify(source, output):
    report = json.loads((ROOT / "reports/windows-build.json").read_text())
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    if source_hash != report["sha256"]:
        raise ValueError("Source ZIP does not match the verified Windows build report")
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("Duplicate ZIP members or invalid CRC")
        prefixes = {name.split("/")[0] for name in names}
        if len(prefixes) != 1:
            raise ValueError("Expected one package root")
        prefix = prefixes.pop() + "/"
        runtime = {name: archive.read(prefix + "Bin32/" + name) for name in RUNTIME}
        if hashlib.sha256(runtime["dinput8.dll"]).hexdigest() != report["dll_sha256"]:
            raise ValueError("DLL does not match the successful Windows build")
        notices = []
        for name in names:
            relative = name[len(prefix):]
            if relative in ("LICENSE.txt", "LICENSE-re4_tweaks.txt") or (
                relative.startswith(("third-party/", "third-party-notices/")) and
                Path(relative).name != "sources.json"
            ):
                notices.append((relative, archive.read(name)))
        if not notices:
            raise ValueError("Missing bundled license notices")
    license_parts = [
        b"RE4 FREE MOVEMENT - CONSOLIDATED LICENSES AND NOTICES\n"
        b"All license texts below are preserved from the full 0.1.3 package.\n"
        b"Original filenames referenced in these texts identify sections below.\n"
        b"Keep this file with redistributed copies of the mod.\n\n"
    ]
    for name, data in sorted(notices):
        license_parts.extend([("\n===== " + name + " =====\n\n").encode(), data, b"\n"])
    licenses = b"".join(license_parts)
    output.mkdir(parents=True, exist_ok=True)
    target = output / (NAME + ".zip")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in runtime.items():
            archive.writestr(NAME + "/Bin32/" + name, data)
        archive.writestr(NAME + "/README.txt", (ROOT / "INSTALL-EN.txt").read_bytes())
        archive.writestr(NAME + "/LICENSES.txt", licenses)
    metadata = {
        "file": target.name,
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "bytes": target.stat().st_size,
        "source_zip_sha256": source_hash,
        "dll_sha256": report["dll_sha256"],
        "workflow_run": report["workflow_run"],
        "runtime_files_unchanged": list(runtime),
        "omitted_optional_settings": ["re4_tweaks/trainer.ini", "re4_tweaks/setting_overrides/overrides_info.txt"],
        "all_original_license_texts_preserved": len(notices),
        "gameplay_tested": False,
    }
    return target, metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("output_directory", type=Path)
    args = parser.parse_args()
    target, metadata = simplify(args.source_zip, args.output_directory)
    print(json.dumps(metadata, indent=2))
