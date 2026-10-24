"""Assemble a copy-to-game bundle from two pinned, previously published ZIPs.

The tested plugin and upstream runtime DLLs are byte-identical. No game files,
executables, download-at-launch logic or installation scripts are added.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import struct
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = 'Outward-Companheiros-v0.1.0-Completo'
FOLDER = 'transfere pro jogo'
LOADER_URL = 'https://gcdn.thunderstore.io/live/repository/packages/BepInEx-BepInExPack_Outward-5.4.19.zip'
LOADER_SHA = 'ae703fcdfd33e65defb8b4e441fbc4d388a8223c0cc251ad791361a0dd6ce35b'
PLUGIN_URL = 'https://raw.githubusercontent.com/lucasmac412/Mods/outward-companheiros-v0.1.0-experimental/downloads/Outward-Companheiros-v0.1.0-Experimental.zip'
PLUGIN_ZIP_SHA = '2075e2da54a9b6363f4296bbdd7176b9f8e884c6cf64de6d40948044d88c7254'
PLUGIN_DLL_SHA = '85783fb6cbf30cd889100c2166c80206041e788a8ee202aec35c70047148383f'
PLUGIN_PATH = 'BepInEx/plugins/CompanheirosAurai/Outward.Companheiros.dll'
CORE = ('0Harmony', '0Harmony20', 'BepInEx', 'BepInEx.Harmony', 'BepInEx.Preloader',
        'HarmonyXInterop', 'Mono.Cecil', 'Mono.Cecil.Mdb', 'Mono.Cecil.Pdb',
        'Mono.Cecil.Rocks', 'MonoMod.RuntimeDetour', 'MonoMod.Utils')
MONO = ('mscorlib', 'System.Reflection', 'System.Reflection.Emit',
        'System.Reflection.Emit.ILGeneration', 'System.Reflection.Emit.Lightweight')
RUNTIME = {'winhttp.dll', 'doorstop_config.ini', 'BepInEx/config/BepInEx.cfg'}
RUNTIME.update('BepInEx/core/' + n + '.dll' for n in CORE)
RUNTIME.update('mono_fix/' + n + '.dll' for n in MONO)


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def obtain(url, digest, local_path=None):
    if local_path:
        data = Path(local_path).read_bytes()
    else:
        with urllib.request.urlopen(url, timeout=60) as response:
            require(response.status == 200, 'Download did not return HTTP 200')
            data = response.read(16 * 1024 * 1024 + 1)
    require(sha(data) == digest, 'Upstream ZIP hash differs: ' + url)
    return data


def archive(data):
    z = zipfile.ZipFile(io.BytesIO(data))
    require(z.testzip() is None, 'Upstream ZIP CRC failure')
    require(len(z.namelist()) == len(set(z.namelist())), 'Duplicate input ZIP path')
    for name in z.namelist():
        path = PurePosixPath(name)
        require(not path.is_absolute() and '..' not in path.parts and '\\' not in name,
                'Unsafe ZIP path: ' + name)
    return z


def managed_or_native_dll(name, data):
    require(len(data) > 256 and data[:2] == b'MZ', 'Invalid DLL header: ' + name)
    offset = struct.unpack_from('<I', data, 0x3C)[0]
    require(offset + 24 <= len(data) and data[offset:offset+4] == b'PE\0\0', 'Invalid PE: ' + name)
    machine, = struct.unpack_from('<H', data, offset + 4)
    flags, = struct.unpack_from('<H', data, offset + 22)
    require(flags & 0x2000, 'PE is not flagged as DLL: ' + name)
    if name == 'winhttp.dll':
        require(machine == 0x8664, 'Doorstop must be Windows x64')
    else:
        magic, = struct.unpack_from('<H', data, offset + 24)
        directories = offset + 24 + (112 if magic == 0x20B else 96)
        require(directories + 15*8 <= len(data), 'Truncated PE directories: ' + name)
        clr_rva, clr_size = struct.unpack_from('<II', data, directories + 14*8)
        require(clr_rva != 0 and clr_size >= 72, 'Managed CLR header missing: ' + name)


def build(loader_bytes, plugin_bytes, output):
    loader = archive(loader_bytes)
    plugin = archive(plugin_bytes)
    payload = {}
    prefix = 'BepInExPack_Outward/'
    upstream_dlls = {n[len(prefix):] for n in loader.namelist() if n.startswith(prefix) and n.endswith('.dll')}
    require(upstream_dlls == {n for n in RUNTIME if n.endswith('.dll')}, 'Unexpected or missing upstream DLL')
    for name in sorted(RUNTIME):
        payload[name] = loader.read(prefix + name)
    matches = [n for n in plugin.namelist() if n.endswith('/' + PLUGIN_PATH)]
    require(len(matches) == 1, 'Expected exactly one tested plugin')
    payload[PLUGIN_PATH] = plugin.read(matches[0])
    require(sha(payload[PLUGIN_PATH]) == PLUGIN_DLL_SHA, 'Plugin differs from approved Windows build')
    payload['LEIA-ME-COMPANHEIROS.txt'] = b'\xef\xbb\xbf' + (ROOT/'LEIA-ME-COMPLETO.txt').read_bytes()
    payload['LICENCAS-COMPANHEIROS.txt'] = b'\xef\xbb\xbf' + (ROOT/'LICENSES-COMPLETO.txt').read_bytes()

    for name, data in payload.items():
        require(not name.lower().endswith(('.exe','.cmd','.bat','.ps1','.sh')), 'Unexpected installer/executable: '+name)
        require('Assembly-CSharp' not in name and 'UnityEngine' not in name, 'Game assembly included')
        if name.endswith('.dll'):
            managed_or_native_dll(name, data)
    config = payload['doorstop_config.ini'].decode('utf-8-sig').replace(' ', '').lower()
    require('targetassembly=bepinex\\core\\bepinex.preloader.dll' in config, 'Wrong preloader target')
    require('dllsearchpathoverride=mono_fix' in config, 'Outward mono_fix disabled')
    require('enabled=true' in config, 'Doorstop is not enabled')
    guide = payload['LEIA-ME-COMPANHEIROS.txt'].decode('utf-8-sig')
    require(all(n in guide for n in ['transfere pro jogo', 'default-mono', 'Outward_Defed', 'DOIS computadores', 'experiment', 'não foi testado']), 'Guide misses prerequisite or status')

    output.mkdir(parents=True, exist_ok=True)
    target = output/(NAME+'.zip')
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(payload.items()):
            info = zipfile.ZipInfo(FOLDER+'/'+name, date_time=(2026,1,1,0,0,0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)
    with zipfile.ZipFile(target) as z:
        require(z.testzip() is None, 'Final ZIP CRC failure')
        require({PurePosixPath(n).parts[0] for n in z.namelist()} == {FOLDER}, 'Wrong top-level folder')
        require(len(z.namelist()) == len(payload), 'Final ZIP entry count differs')
        for name, data in payload.items():
            require(z.read(FOLDER+'/'+name) == data, 'Repacked bytes changed: '+name)

    digest = sha(target.read_bytes())
    (output/(NAME+'-SHA256.txt')).write_text(digest+'  '+target.name+'\n', encoding='ascii')
    report = {
        'package': NAME, 'folder': FOLDER, 'pluginVersion': '0.1.0',
        'status': 'EXPERIMENTAL_COMPILED_NO_LIVE_GAME_TEST',
        'game': 'Outward Definitive Edition Steam Mono / Windows x64',
        'requiresSteamMonoSelection': True, 'loaderIncluded': True, 'monoFixIncluded': True,
        'separateLoaderDownloadRequired': False, 'installScriptsIncluded': False,
        'soloGameplayVerified': False, 'coopGameplayVerified': False,
        'zip': target.name, 'bytes': target.stat().st_size, 'sha256': digest,
        'pluginSha256': PLUGIN_DLL_SHA,
        'sources': [ {'component':'BepInExPack_Outward 5.4.19','url':LOADER_URL,'sha256':LOADER_SHA},
                     {'component':'Tested plugin ZIP','url':PLUGIN_URL,'sha256':PLUGIN_ZIP_SHA}],
        'runtimeFilesUnchanged': True,
        'omittedUpstreamDocumentation': 'XML API docs, external manifest and icon; upstream README retained in license file',
        'files': [{'path':name,'bytes':len(data),'sha256':sha(data)} for name,data in sorted(payload.items())]
    }
    (output/(NAME+'-build.json')).write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='files'},indent=2,ensure_ascii=False))
    print(str(len(payload))+' entries verified: every bundled DLL unchanged; Windows x64 proxy, Mono paths, layout and ZIP CRC checked.')
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--loader-zip', type=Path)
    p.add_argument('--plugin-zip', type=Path)
    p.add_argument('--output', type=Path, default=ROOT/'dist')
    args = p.parse_args()
    build(obtain(LOADER_URL,LOADER_SHA,args.loader_zip),
          obtain(PLUGIN_URL,PLUGIN_ZIP_SHA,args.plugin_zip),args.output)


if __name__ == '__main__':
    main()
