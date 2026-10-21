#!/usr/bin/env python3
"""Fetch pinned Fabric 26.2 dependencies for development, not a Create installer."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('/workspace/.cache/create-port/target-libs'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for library in json.loads((ROOT / 'target-libraries.json').read_text()):
        target = args.output / library['file']
        if not target.exists():
            request = urllib.request.Request(library['url'], headers={'User-Agent': 'lucasmac412-CreatePortResearch/1.0'})
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read()
        else:
            data = target.read_bytes()
        if hashlib.sha512(data).hexdigest() != library['sha512']:
            raise SystemExit('Dependency checksum mismatch: ' + library['file'])
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            metadata = json.loads(archive.read('fabric.mod.json'))
            if metadata['id'] != library['modId'] or archive.testzip() is not None:
                raise SystemExit('Invalid dependency: ' + library['file'])
            target.write_bytes(data)
            for nested in metadata.get('jars', []):
                name = Path(nested['file']).name
                if name not in library['nestedJars']:
                    raise SystemExit('Unpinned nested dependency: ' + name)
                (args.output / name).write_bytes(archive.read(nested['file']))
        print('Verified: ' + library['file'])


if __name__ == '__main__':
    main()
