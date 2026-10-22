#!/usr/bin/env python3
"""Apply this extension to the exact upstream revision; never patch a game EXE."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REVISION = "92c0208bd09c29c9640e13a2493b8e6bd6edd700"


def replace_once(text, old, new, name):
    if text.count(old) != 1:
        raise ValueError(f"Unexpected upstream contents: {name}; no files changed")
    return text.replace(old, new, 1)


def prepare(target):
    provenance = json.loads((ROOT / "upstream.json").read_text())
    pending = {}
    for name, expected in provenance["source_normalized_sha256"].items():
        file = target / name
        if hashlib.sha256(file.read_bytes().replace(b"\r\n", b"\n")).hexdigest() != expected:
            raise ValueError(f"Wrong revision or modified file: {name}; no files changed")
    for name in ("dllmain/dllmain.cpp", "dllmain/MouseTurning.cpp", "dllmain/dllmain.vcxproj", "dllmain/WndProcHook.cpp"):
        pending[name] = (target / name).read_text(encoding="utf-8-sig")
    name = "dllmain/dllmain.cpp"
    pending[name] = replace_once(pending[name], '#include "Trainer.h"', '#include "Trainer.h"\n#include "ModernControls.h"', name)
    pending[name] = replace_once(pending[name], '\tre4t::init::CameraTweaks();', '\tre4t::init::ModernControls();\n\n\tre4t::init::CameraTweaks();', name)
    name = "dllmain/MouseTurning.cpp"
    pending[name] = replace_once(pending[name], '#include "input.hpp"', '#include "input.hpp"\n#include "ModernControls.h"', name)
    pending[name] = replace_once(pending[name], 'void MouseTurn(float TurnDelayFactor = 10.0f)\n{', 'void MouseTurn(float TurnDelayFactor = 10.0f)\n{\n\tif (ModernControls_OwnsLocomotion()) return;', name)
    name = "dllmain/dllmain.vcxproj"
    pending[name] = replace_once(pending[name], '    <ClCompile Include="MouseTurning.cpp" />', '    <ClCompile Include="ModernControls.cpp" />\n    <ClCompile Include="MouseTurning.cpp" />', name)
    # An experimental fork must never auto-update itself to an unrelated upstream DLL.
    name = "dllmain/dllmain.cpp"
    pending[name] = replace_once(pending[name], '\tCreateThreadAutoClose(0, 0, (LPTHREAD_START_ROUTINE)&updateCheck, NULL, 0, NULL);', '\t// Modern Controls fork: automatic upstream DLL updates are disabled.', name)
    name = "dllmain/WndProcHook.cpp"
    pending[name] = replace_once(pending[name], '\tLAACheck();', '\t// This controls-only fork never offers to rewrite the game executable.', name)
    # Replace the upstream's embedded Pro icon font with OFL-licensed Free Solid.
    font_path = ROOT / "third-party/fa-solid-900.ttf"
    font = font_path.read_bytes()
    font_manifest = json.loads((ROOT / "third-party/sources.json").read_text())
    if hashlib.sha256(font).hexdigest() != font_manifest[font_path.name]["sha256"]:
        raise ValueError("Font checksum mismatch; no files changed")
    rows = [", ".join(str(b) for b in font[i:i + 24]) for i in range(0, len(font), 24)]
    pending["includes/faprolight.hpp"] = (
        "// Altered fork: Font Awesome Free Solid 6.7.2, SIL OFL 1.1.\n"
        "// Original FAprolight identifier retained for source compatibility.\n"
        "#pragma once\nunsigned char FAprolight[] = {\n" + ",\n".join(rows) + "\n};\n")
    for source in (ROOT / "src").glob("*.h"):
        pending["dllmain/" + source.name] = source.read_text()
    pending["dllmain/ModernControls.cpp"] = (ROOT / "src/ModernControls.cpp").read_text()
    for name, text in pending.items():
        with (target / name).open("w", encoding="utf-8", newline="\n") as output:
            output.write(text)
    return sorted(pending)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path)
    args = parser.parse_args()
    print(json.dumps({"upstream": REVISION, "files": prepare(args.upstream.resolve())}, indent=2))
