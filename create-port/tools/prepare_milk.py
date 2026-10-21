#!/usr/bin/env python3
"""Prepare a source-level server safety fix for the pinned MIT Milk 26.2 library.

No JAR is downloaded from this project or silently replaced in a game directory.
Build and integration validation are separate explicit development commands.
"""
import argparse
import difflib
import hashlib
from pathlib import Path, PurePosixPath
import re
import tarfile
import urllib.request

COMMIT = '5b1a83c22df485ad724f86bd755f724bac2ddc5c'
SHA = 'dc037131a58ed046ec36ab28f4841994259c1446fd91ad59e10b27dc4099716c'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, required=True)
    args = parser.parse_args()
    archive = args.cache / 'milk-26.2-source.tar.gz'
    args.cache.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        request = urllib.request.Request(f'https://api.github.com/repos/Mr-George-star/Milk-Lib/tarball/{COMMIT}',
                                         headers={'User-Agent': 'lucasmac412-CreatePortResearch/1.0'})
        with urllib.request.urlopen(request, timeout=90) as response:
            archive.write_bytes(response.read())
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA:
        raise SystemExit('Milk source checksum mismatch')
    output = args.cache / 'milk-patched'
    output.mkdir(exist_ok=True)
    marker = output / '.create-milk-source-workspace'
    if any(output.iterdir()) and not marker.exists():
        raise SystemExit('Output is not a generated Milk workspace')
    marker.write_text('Pinned development sources, not a Create mod.\n')
    before = {}
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            path = PurePosixPath(member.name)
            if not member.isfile() or len(path.parts) < 2:
                continue
            relative = PurePosixPath(*path.parts[1:])
            if '..' in relative.parts or relative.is_absolute():
                raise SystemExit('Unsafe archive path')
            data = source.extractfile(member).read()
            target = output / str(relative)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            if relative.suffix == '.java' or str(relative) == 'gradle.properties':
                before[str(relative)] = data.decode()

    common = output / 'src/main/java/net/george/milk/api/DrippableFluidManager.java'
    text = common.read_text()
    start = text.index('        ParticleProviderRegistry registry =')
    end = text.index('\n    }', start)
    body = text[start:end]
    text = text[:start] + '        DrippableFluidClient.register(particles, fluid);' + text[end:]
    text = text.replace('import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;\n', '')
    common.write_text(text)
    # Putting lambdas in a CLIENT-annotated method is insufficient: javac creates
    # unannotated synthetic methods with SpriteSet parameters on the common interface.
    client = common.with_name('DrippableFluidClient.java')
    client.write_text('''package net.george.milk.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

/** Client factories and their generated lambda methods must stay out of common classes. */
@Environment(EnvType.CLIENT)
final class DrippableFluidClient {
    private DrippableFluidClient() { }
    static void register(ParticleTypeSet particles, DrippableFluid fluid) {
''' + body + '\n    }\n}\n')

    mod = output / 'src/main/java/net/george/milk/MilkLib.java'
    text = mod.read_text()
    start = text.index('\t\tItemTooltipCallback.EVENT.register(')
    end = text.index('\n\t\t});', start) + len('\n\t\t});')
    tooltip = text[start:end]
    text = text[:start] + text[end:]
    text = text.replace('import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;\n', '')
    mod.write_text(text)
    mod_client = mod.with_name('MilkLibClient.java')
    text = mod_client.read_text().replace('package net.george.milk;\n', '''package net.george.milk;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
''')
    tooltip = re.sub(r'\b(MILK_BOTTLE|SPLASH_MILK_BOTTLE|LINGERING_MILK_BOTTLE)\b', r'MilkLib.\1', tooltip)
    text = text.replace('    public void onInitializeClient() {', '    public void onInitializeClient() {\n' + tooltip)
    mod_client.write_text(text)
    props = output / 'gradle.properties'
    props.write_text(props.read_text().replace('loom_version=1.17-SNAPSHOT', 'loom_version=1.18.4')
                     .replace('mod_version=1.2.1\n', 'mod_version=1.2.1+createport.1\n'))
    patch = []
    for path in sorted(set(before) | {'src/main/java/net/george/milk/api/DrippableFluidClient.java'}):
        after = (output / path).read_text()
        original = before.get(path, '')
        if original != after:
            patch.extend(difflib.unified_diff(original.splitlines(True), after.splitlines(True),
                         fromfile='a/' + path if path in before else '/dev/null', tofile='b/' + path))
    (args.cache / 'milk-server-split.patch').write_text(''.join(patch))
    print('Prepared source-level server/client separation fix at ' + str(output))


if __name__ == '__main__':
    main()
