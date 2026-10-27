#!/usr/bin/env python3
"""Package only our plugin and documentation; no game assemblies or test probes."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parents[1]
repo = root.parent
if (root / 'DISTRIBUICAO-SUSPENSA.txt').exists():
    raise SystemExit('Distribuição suspensa: detecção do Defender não resolvida. Consulte sevendays-bau/docs/SEGURANCA.md.')
name = '7Days-Crafting-dos-Baus-3.4-v0.3.0-Experimental'
output = repo / 'downloads' / (name + '.zip')
files = {
    'LEIA-ME.txt': root / 'LEIA-ME.txt',
    'LICENCA.txt': repo / 'LICENSE',
    'transfere pro jogo/Mods/CraftingDosBaus/CraftingDosBaus.dll': root / 'src/bin/Release/net48/CraftingDosBaus.dll',
    'transfere pro jogo/Mods/CraftingDosBaus/ModInfo.xml': root / 'ModInfo.xml',
    'transfere pro jogo/Mods/CraftingDosBaus/config.json': root / 'config.json',
    'transfere pro jogo/Mods/CraftingDosBaus/Config/XUi_InGame/windows.xml': root / 'Config/XUi_InGame/windows.xml',
}
output.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path, source in files.items():
        info = zipfile.ZipInfo(path, (2026, 10, 27, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, source.read_bytes())
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(files)
    assert archive.read('transfere pro jogo/Mods/CraftingDosBaus/CraftingDosBaus.dll')[:2] == b'MZ'
digest = hashlib.sha256(output.read_bytes()).hexdigest()
output.with_name(name + '-SHA256.txt').write_text(f'{digest}  {output.name}\n')
print(f'{output} ({output.stat().st_size} bytes)\nSHA256: {digest}')
