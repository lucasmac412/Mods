#!/usr/bin/env python3
"""Rebuild pinned MIT library sources with our Fabric 26.2 patches.

This prepares development sources, not the Create mod or an installation package.
Existing sources, including manual edits, are never overwritten.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
MARKER = '.create-render-library.json'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def extract_source(archive, output):
    """Extract regular source files only; reject links and traversal paths."""
    with tarfile.open(archive) as source:
        for member in source:
            name = PurePosixPath(member.name)
            if name.is_absolute() or '..' in name.parts:
                raise ValueError('Unsafe source archive path')
            if member.isdir():
                continue
            if not member.isfile() or len(name.parts) < 2:
                raise ValueError('Non-regular source archive entry')
            relative = Path(*name.parts[1:])
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.extractfile(member).read())


def verify_existing(output, inputs):
    marker = output / MARKER
    if not marker.is_file():
        raise ValueError('Output already exists and is not an owned generated workspace; choose another --output')
    saved = json.loads(marker.read_text())
    if saved['inputs'] != inputs:
        raise ValueError('Sources or patch changed; preserve existing work and choose another --output')
    for relative, expected in saved['files'].items():
        name = PurePosixPath(relative)
        if name.is_absolute() or '..' in name.parts:
            raise ValueError('Unsafe generated workspace manifest path')
        path = output / relative
        if not path.resolve().is_relative_to(output.resolve()) or not path.is_file() or path.is_symlink() or digest(path) != expected:
            raise ValueError('Generated sources have manual changes: ' + relative + '; choose another --output')


def prepare(library, cache, output):
    archive = cache / library['archive']
    cache.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        request = urllib.request.Request(library['url'], headers={'User-Agent': 'lucasmac412-CreatePort/1.0'})
        with urllib.request.urlopen(request, timeout=90) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != library['sha256']:
            raise ValueError('Downloaded library source checksum mismatch')
        archive.write_bytes(data)
    if digest(archive) != library['sha256']:
        raise ValueError('Cached library source checksum mismatch')
    patch = ROOT / 'patches' / (library['id'] + '-26.2.patch')
    templates = ROOT / 'library-builds' / library['id']
    inputs = {'source': library['sha256'], 'patch': digest(patch),
              'build': {p.name: digest(p) for p in sorted(templates.iterdir()) if p.is_file()}}
    if output.exists():
        verify_existing(output, inputs)
        print('Verified existing development sources: ' + str(output))
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    # Prepare in a private temporary directory so a failed patch cannot leave a
    # partially prepared workspace masquerading as a successful migration.
    with tempfile.TemporaryDirectory(prefix='.create-library-', dir=output.parent) as temporary:
        staging = Path(temporary) / 'source'
        staging.mkdir()
        extract_source(archive, staging)
        subprocess.run(['patch', '--batch', '--forward', '-p1', '-i', str(patch)],
                       cwd=staging, check=True, stdout=subprocess.DEVNULL)
        disabled = ['settings.gradle.kts', 'build.gradle.kts']
        if library['id'] == 'ponder':
            disabled.append('fabric/build.gradle.kts')
        for relative in disabled:
            source = staging / relative
            if source.exists():
                source.rename(source.with_name(source.name + '.upstream'))
        for source in templates.iterdir():
            if source.is_file():
                shutil.copyfile(source, staging / source.name)
        files = {str(p.relative_to(staging)): digest(p) for p in staging.rglob('*') if p.is_file()}
        (staging / MARKER).write_text(json.dumps({'inputs': inputs, 'files': files}, indent=2) + '\n')
        staging.rename(output)
    print('Prepared ' + library['id'] + ' Fabric 26.2 development sources: ' + str(output))


def main():
    libraries = {x['id']: x for x in json.loads((ROOT / 'library-sources.json').read_text())}
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('library', choices=sorted(libraries))
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, help='New directory; existing manual work is preserved')
    args = parser.parse_args()
    output = args.output or args.cache / (args.library + '-26.2')
    try:
        prepare(libraries[args.library], args.cache.resolve(), output.resolve())
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error


if __name__ == '__main__':
    main()
