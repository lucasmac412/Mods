#!/usr/bin/env python3
"""Package only the tested installable mod, verified Fabric API and beginner instructions."""
from pathlib import Path
import hashlib,io,json,subprocess,zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent.parent
VERSION='0.1.0-experimental'
NAME='Punhos-da-Lua-Azul-Fabric-26.2-v0.1.0-Experimental.zip'
FABRIC_SHA='e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a'
def sha(blob):return hashlib.sha256(blob).hexdigest()
def main():
    unit=ET.parse(ROOT/'moonfist-mod/build/test-results/test/TEST-br.com.moonfist.CombatStateTest.xml').getroot()
    server=ET.parse(ROOT/'moonfist-mod/build/test-results/gametest.xml').getroot()
    if int(unit.get('failures','0')) or int(unit.get('errors','0')) or int(unit.get('tests','0'))<10 or server.findall('.//failure') or len(server.findall('.//testcase'))<10:
        raise SystemExit('Required combat tests did not pass.')
    jar_name=f'moonfist-combat-{VERSION}.jar'
    mod=(ROOT/'moonfist-mod/build/libs'/jar_name).read_bytes()
    with zipfile.ZipFile(io.BytesIO(mod)) as z:
        meta=json.loads(z.read('fabric.mod.json'))
        assert meta['id']=='moonfist' and meta['version']==VERSION
        assert not any('gametest' in n.lower() for n in z.namelist())
        for n in ['br/com/moonfist/combat/CombatService.class','br/com/moonfist/client/CombatEffects.class','br/com/moonfist/mixin/FirstPersonMixin.class','moonfist.client.mixins.json','data/moonfist/damage_type/martial_strike.json','assets/moonfist/textures/entity/lunar_disciple.png']:
            assert n in z.namelist(),n
        assert z.testzip() is None
    baseline=(ROOT/'downloads/Luma-Fabric-26.2.zip').read_bytes()
    assert sha(baseline)=='00106b9f9a194dec6c73bf5aba125d318e2660c08d509d0e3b06c6140db79067'
    with zipfile.ZipFile(io.BytesIO(baseline)) as z:
        fabric=z.read(next(n for n in z.namelist() if n.endswith('/fabric-api-0.161.0+26.2.jar')))
    assert sha(fabric)==FABRIC_SHA
    with zipfile.ZipFile(io.BytesIO(fabric)) as z:license=z.read('LICENSE-fabric-api')
    entries={
        'mods/'+jar_name:mod,
        'mods/fabric-api-0.161.0+26.2.jar':fabric,
        'COMECE-AQUI.txt':(ROOT/'moonfist-mod/COMECE-AQUI.txt').read_bytes(),
        'licenses/LICENSE-MIT.txt':(ROOT/'LICENSE').read_bytes(),
        'licenses/LICENSE-fabric-api.txt':license,
        'licenses/CREDITOS.txt':('Punhos da Lua Azul: código, animações, geometria e texturas originais, MIT.\n'
            'Os sons referenciam eventos do Minecraft; nenhum áudio do jogo é redistribuído.\n'
            'Fabric API 0.161.0+26.2: FabricMC contributors, Apache-2.0.\n'
            'https://github.com/FabricMC/fabric\n'
            'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0+26.2/\n'
            'Minecraft e Fabric Loader são instalados separadamente.\n').encode()
    }
    source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    entries['VERSAO.txt']=f'Punhos da Lua Azul {VERSION}\nMinecraft 26.2 / Fabric 0.19.5+ / Fabric API 0.161.0+26.2 / Java 25\nFonte: {source}\n'.encode()
    entries['SHA256SUMS.txt']=''.join(f'{sha(value)}  {key}\n' for key,value in sorted(entries.items())).encode()
    out=ROOT/'downloads'/NAME
    with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for key,value in sorted(entries.items()):
            info=zipfile.ZipInfo(key,date_time=(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,value)
    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None
        assert sorted(n for n in z.namelist() if n.endswith('.jar'))==sorted(['mods/'+jar_name,'mods/fabric-api-0.161.0+26.2.jar'])
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1);assert sha(z.read(name))==digest,name
    report={'version':VERSION,'minecraft':'26.2','sourceCommit':source,'package':NAME,'bytes':out.stat().st_size,'sha256':sha(out.read_bytes()),'jarSha256':sha(mod),'fabricApiSha256':sha(fabric),'unitTests':int(unit.get('tests')),'serverCombatTests':len(server.findall('.//testcase'))-1,'files':{k:sha(v) for k,v in sorted(entries.items())}}
    (ROOT/'downloads'/NAME.replace('.zip','.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='files'},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
