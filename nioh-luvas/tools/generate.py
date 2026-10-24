#!/usr/bin/env python3
"""Generate combat definitions and a self-contained browser preview from one spec."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def generate():
    spec = json.loads((ROOT / "assets/weapon.json").read_text(encoding="utf-8"))
    assert spec["engine_category_id"] is None and not spec["integration_validated"]
    moves = spec["moves"]
    assert len({m["id"] for m in moves}) == len(moves)
    rows = []
    for m in moves:
        assert m["ki"] > 0 and 0 < m["range"] < 20 and 0 < m["duration"] < 4
        assert 0 < len(m["hits"]) <= 8 and m["hits"] == sorted(set(m["hits"]))
        assert all(0 < t < m["duration"] for t in m["hits"])
        hits = ", ".join(str(t) for t in m["hits"] + [0] * (8 - len(m["hits"])))
        rows.append('{"%s", %s, %s, %s, %s, %s, %s, %s, {%s}, %s}' % (
            m["id"], m["stance"], {"light": 0, "heavy": 1, "caps": 2}[m["input"]],
            m["ki"], m["duration"], m["damage"], m["range"], m.get("cooldown", 0), hits, len(m["hits"])))
    header = ('// Generated from assets/weapon.json. Authoring units, not Nioh engine IDs.\n'
              '#pragma once\n#include <array>\nnamespace moonfist {\n'
              'struct Move { const char* id; int stance, input; double ki, duration, damage, range, cooldown; '
              'std::array<double,8> hits; unsigned hitCount; };\n'
              'inline constexpr std::array<Move, %s> moves{{\n%s\n}};\n}\n' % (len(rows), ',\n'.join(rows)))
    (ROOT / "src/Moves.generated.h").write_text(header, encoding="utf-8")
    template = (ROOT / "preview/template.html").read_text(encoding="utf-8")
    assert template.count("/*WEAPON_SPEC*/") == 1
    (ROOT / "preview/Previa.html").write_text(
        template.replace("/*WEAPON_SPEC*/", json.dumps(spec, ensure_ascii=False)), encoding="utf-8")


if __name__ == "__main__":
    generate()
