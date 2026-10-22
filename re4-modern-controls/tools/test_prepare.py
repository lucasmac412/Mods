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

    def test_prologue_removal_preserves_other_hooks_and_extracts_only_test_code(self):
        callbacks = []
        for name in ("TurnHookWalk", "TurnHookRun"):
            callbacks.append('\tstruct ' + name + '\n\t{\n'
                '\t\tvoid operator()(injector::reg_pack& regs) {\n'
                '\t\t\tregs.ebp = *(int32_t*)(regs.esp);\n'
                '\t\t\t*(int32_t*)(regs.esp) -= 0x8;\n'
                '\t\t}\n\t}; injector::MakeInline<' + name + '>(begin, end);\n')
        before = '// unrelated hook\n'
        after = '\t// pl_R1_Back\n// retained backward hook\n'
        source = before + '\t// pl_R1_Walk\n' + ''.join(callbacks) + after
        fixed, fixture = prepare.remove_unsafe_prologues(source)
        self.assertTrue(fixed.startswith(before))
        self.assertTrue(fixed.endswith(after))
        self.assertNotIn('MakeInline<TurnHook', fixed)
        self.assertNotIn('regs.esp', fixed)
        self.assertIn('struct TurnHookWalk', fixture)
        self.assertIn('struct TurnHookRun', fixture)
        self.assertNotIn('MakeInline', fixture)

    def test_unknown_prologue_body_is_rejected(self):
        text = '\t// pl_R1_Walk\n\tstruct TurnHookWalk\n{}; injector::MakeInline<TurnHookWalk>();\n' \
               '\tstruct TurnHookRun\n{}; injector::MakeInline<TurnHookRun>();\n\t// pl_R1_Back\n'
        with self.assertRaisesRegex(ValueError, 'Unexpected native prologue'):
            prepare.remove_unsafe_prologues(text)


if __name__ == "__main__":
    unittest.main()
