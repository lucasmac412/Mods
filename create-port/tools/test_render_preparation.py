"""Safety checks for third-party source extraction and preservation of manual work."""
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

from prepare_render_libraries import digest, extract_source, verify_existing, MARKER


class RenderPreparationSafetyTests(unittest.TestCase):
    def test_rejects_archive_traversal_and_links(self):
        for name, kind in [('repo/../../escape.java', tarfile.REGTYPE),
                           ('repo/link', tarfile.SYMTYPE)]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive = root / 'source.tar.gz'
                with tarfile.open(archive, 'w:gz') as out:
                    member = tarfile.TarInfo(name)
                    member.type = kind
                    if kind == tarfile.SYMTYPE:
                        member.linkname = '../../outside'
                        out.addfile(member)
                    else:
                        member.size = 1
                        out.addfile(member, io.BytesIO(b'x'))
                target = root / 'target'
                target.mkdir()
                with self.assertRaises(ValueError):
                    extract_source(archive, target)
                self.assertFalse((root / 'escape.java').exists())

    def test_modified_generated_source_is_preserved_and_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / 'Machine.java'
            source.write_text('original')
            inputs = {'source': 'pinned'}
            (root / MARKER).write_text(json.dumps({'inputs': inputs, 'files': {'Machine.java': digest(source)}}))
            verify_existing(root, inputs)
            source.write_text('manual work')
            with self.assertRaises(ValueError):
                verify_existing(root, inputs)
            self.assertEqual(source.read_text(), 'manual work')


if __name__ == '__main__':
    unittest.main()
