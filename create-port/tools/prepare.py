#!/usr/bin/env python3
"""Prepare a code-only Create migration workspace, never an installable mod.

Original Create code: MIT, see ../LICENSE-UPSTREAM.txt. No assets are extracted.
The verified Fabric reference is unfinished Create 6.0.0 for Minecraft 1.21.1;
it is not equivalent to the user's NeoForge 6.0.10 release.
"""
import argparse
import difflib
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE_URL = ('https://api.github.com/repos/Fabricators-of-Create/Create/tarball/'
              '7f1f67b6ecbbde512e8d35404f3621a6875ce838')
SOURCE_SHA = 'c7a7178e4aef879379aebd91bb00bd878b698e2ecb70bd6d8ae2c4cf9f716f5e'
PROTECTED = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')


def rewrite(text, mappings):
    """Rewrite Java code tokens while retaining comments, literals and text blocks."""
    pattern = re.compile(r'(?<![\w$])(?:' + '|'.join(
        re.escape(s) for s in sorted(mappings, key=len, reverse=True)) + r')(?![\w$])')
    pieces = []
    end = 0
    for protected in PROTECTED.finditer(text):
        pieces.append(pattern.sub(lambda m: mappings[m[0]], text[end:protected.start()]))
        pieces.append(protected[0])
        end = protected.end()
    pieces.append(pattern.sub(lambda m: mappings[m[0]], text[end:]))
    return ''.join(pieces)


def class_exists(name, entries):
    """Java spells nested types with dots; the JAR spells them with '$'."""
    parts = name.split('.')
    for boundary in range(2, len(parts)):
        binary = '/'.join(parts[:boundary]) + '/' + '$'.join(parts[boundary:]) + '.class'
        if binary in entries:
            return True
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--minecraft-jar', type=Path, required=True,
                        help='Actual Minecraft 26.2 merged development JAR from Loom')
    parser.add_argument('--library-jar', type=Path, action='append', default=[],
                        help='Locally ported 26.2 Flywheel, Catnip or Ponder JAR; repeat per library')
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    archive = args.cache / 'create-fabric-1.21.1-source.tar.gz'
    if not archive.exists():
        request = urllib.request.Request(SOURCE_URL, headers={'User-Agent': 'lucasmac412-CreatePortResearch/1.0'})
        with urllib.request.urlopen(request, timeout=90) as response:
            archive.write_bytes(response.read())
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SOURCE_SHA:
        raise SystemExit('Pinned source archive checksum mismatch; no source was modified.')
    with zipfile.ZipFile(args.minecraft_jar) as game:
        if json.loads(game.read('version.json'))['id'] != '26.2':
            raise SystemExit('Minecraft version must be exactly 26.2.')
        entries = set(game.namelist())
        classes = {n.removesuffix('.class').replace('/', '.') for n in entries
                   if n.startswith('net/minecraft/') and n.endswith('.class') and '$' not in n}
    if 'net.minecraft.resources.Identifier' not in classes or 'net.minecraft.resources.ResourceLocation' in classes:
        raise SystemExit('This migration requires the actual Minecraft 26.2 development API.')

    library_sources = []
    for library in args.library_jar:
        with zipfile.ZipFile(library) as jar:
            metadata = json.loads(jar.read('fabric.mod.json'))
            if metadata['id'] not in {'flywheel', 'catnip', 'ponder'} or metadata['depends']['minecraft'] != '26.2':
                raise SystemExit('Library must be a local Flywheel/Catnip/Ponder port for exactly 26.2')
            classes.update(n.removesuffix('.class').replace('/', '.') for n in jar.namelist()
                           if n.endswith('.class') and '$' not in n and n.startswith(
                               ('net/createmod/', 'dev/engine_room/flywheel/')))
            entries.update(jar.namelist())
            library_sources.append({'id': metadata['id'], 'version': metadata['version'],
                                    'sha256': hashlib.sha256(library.read_bytes()).hexdigest()})

    by_simple_name = {}
    for name in classes:
        by_simple_name.setdefault(name.rsplit('.', 1)[-1], []).append(name)
    original = {}
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            path = PurePosixPath(member.name)
            if member.isfile() and len(path.parts) > 1:
                relative = PurePosixPath(*path.parts[1:])
                if '..' in relative.parts or relative.is_absolute():
                    raise SystemExit('Unsafe archive path')
                if str(relative).startswith('src/main/java/') and relative.suffix == '.java':
                    original[str(relative)] = source.extractfile(member).read().decode('utf-8')
    mappings = {'net.minecraft.resources.ResourceLocation': 'net.minecraft.resources.Identifier',
                'ResourceLocation': 'Identifier'}
    for text in original.values():
        for imported in re.findall(r'^import (?:static )?((?:net\.minecraft|net\.createmod|dev\.engine_room\.flywheel)\.[\w.]+);', text, re.M):
            old = imported
            namespace = next((root for root in ('net.minecraft.', 'net.createmod.catnip.',
                              'net.createmod.ponder.', 'dev.engine_room.flywheel.') if old.startswith(root)), None)
            if namespace is None:
                continue
            while old.startswith(namespace):
                if class_exists(old, entries):
                    break
                simple = old.rsplit('.', 1)[-1]
                candidates = [name for name in by_simple_name.get(simple, []) if name.startswith(namespace)]
                if len(candidates) == 1:
                    mappings[old] = candidates[0]
                    break
                old = old.rsplit('.', 1)[0]

    output = args.cache / 'adapted-fabric-reference'
    output.mkdir(exist_ok=True)
    marker = output / '.create-migration-workspace'
    if any(output.iterdir()) and not marker.is_file():
        raise SystemExit('Refusing to overwrite a directory not created by this migration tool.')
    marker.write_text('Code-only migration probe; not a playable Create port.\n')
    patch = []
    modified = []
    for path, before in sorted(original.items()):
        after = rewrite(before, mappings)
        target = output / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(after)
        if before != after:
            modified.append(path)
            patch.extend(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                            fromfile='a/' + path, tofile='b/' + path))
    for filename in ('build.gradle', 'settings.gradle'):
        (output / filename).write_text((ROOT / 'probe' / filename).read_text())
    adapter = ROOT / 'integration-probe/src/main/java/io/github/tropheusj/milk/Milk.java'
    adapter_target = output / 'src/main/java/io/github/tropheusj/milk/Milk.java'
    adapter_target.parent.mkdir(parents=True, exist_ok=True)
    adapter_target.write_text(adapter.read_text())
    (output / 'LICENSE-UPSTREAM.txt').write_text((ROOT / 'LICENSE-UPSTREAM.txt').read_text())
    patch_name = 'minecraft-and-render-library-names.patch' if library_sources else 'minecraft-26.2-names.patch'
    report_name = 'names-with-render-libraries.json' if library_sources else 'names-migration.json'
    (args.cache / patch_name).write_text(''.join(patch))
    report = {'status': 'NOT_PLAYABLE', 'sourceCommit': '7f1f67b6ecbbde512e8d35404f3621a6875ce838',
              'sourceCreateVersion': '6.0.0.0-dev', 'requestedCreateVersion': '6.0.10',
              'minecraftTarget': '26.2', 'sourceJavaFiles': len(original),
              'changedFiles': len(modified), 'renames': mappings,
              'output': str(output), 'assetsExtracted': False,
              'milkAdapterIncluded': True,
              'localRenderLibraries': library_sources,
              'limitations': ['Package/name migration only; method contracts are unverified.',
                              'Registrate and Porting Lib still need migration; local render ports do not constitute Create.',
                              'Merge of 6.0.10 changes is still required.',
                              'Access wideners, mixins, save formats and rendering are not migrated.']}
    (args.cache / report_name).write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
