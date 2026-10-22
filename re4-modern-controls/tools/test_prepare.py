import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("prepare", ROOT / "tools/prepare.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class PreparationTests(unittest.TestCase):
    def test_wrong_source_does_not_change_any_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = json.loads((ROOT / "upstream.json").read_text())
            for name in manifest["source_sha256"]:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("changed source")
            before = {p.relative_to(root): p.read_bytes() for p in root.rglob("*") if p.is_file()}
            with self.assertRaisesRegex(ValueError, "Wrong revision"):
                prepare.prepare(root)
            after = {p.relative_to(root): p.read_bytes() for p in root.rglob("*") if p.is_file()}
            self.assertEqual(before, after)

    def test_patch_anchor_must_be_unique(self):
        for text in ("missing", "anchor anchor"):
            with self.assertRaises(ValueError):
                prepare.replace_once(text, "anchor", "new", "fixture")


if __name__ == "__main__":
    unittest.main()
