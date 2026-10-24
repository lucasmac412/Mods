#!/usr/bin/env python3
"""Verify generated assets and inspect optional native self-test output."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def verify(inspector=None, report=None):
    spec = json.loads((ROOT / "assets/weapon.json").read_text(encoding="utf-8"))
    assert not spec["integration_validated"] and spec["engine_category_id"] is None
    preview = (ROOT / "preview/Previa.html").read_text(encoding="utf-8")
    assert json.dumps(spec, ensure_ascii=False) in preview
    assert "/*WEAPON_SPEC*/" not in preview
    glb = (ROOT / "assets/Punhos-Lua-Azul-authoring.glb").read_bytes()
    magic, version, size = struct.unpack_from("<III", glb)
    assert magic == 0x46546C67 and version == 2 and size == len(glb)
    json_size, kind = struct.unpack_from("<II", glb, 12)
    assert kind == 0x4E4F534A
    model = json.loads(glb[20:20 + json_size])
    assert len(model["animations"]) == len(spec["moves"]) == 7
    assert {a["name"] for a in model["animations"]} == {m["id"] for m in spec["moves"]}
    assert model["extras"]["native_game_skeleton"] is False
    for view in model["bufferViews"]:
        assert view["byteOffset"] + view["byteLength"] <= model["buffers"][0]["byteLength"]
    source = (ROOT / "src/Inspect.cpp").read_text(encoding="utf-8")
    for forbidden in ("WriteProcessMemory", "CreateRemoteThread", "VirtualAllocEx", "PROCESS_ALL_ACCESS", "WinHttp", "WinInet"):
        assert forbidden not in source
    assert "PROCESS_QUERY_LIMITED_INFORMATION|PROCESS_VM_READ" in source
    if inspector and report:
        data = json.loads(Path(report).read_text(encoding="utf-8"))
        assert data["exe_sha256"] == hashlib.sha256(Path(inspector).read_bytes()).hexdigest()
        assert data["pe_machine"] == 0x8664
        assert data["runtime"]["read_access"] and len(data["runtime"]["text_sha256"]) == 64
        assert not data["game_writes"] and not data["network_used"] and not data["absolute_paths_included"]
        assert data["engine_bindings"] == "unresolved"
    print("PASS: seven original authoring clips, generated preview/spec consistency, bounded GLB buffers, read-only inspector policy" +
          (", native SHA-256/runtime self-test." if inspector else "."))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inspector")
    parser.add_argument("--report")
    args = parser.parse_args()
    verify(args.inspector, args.report)
