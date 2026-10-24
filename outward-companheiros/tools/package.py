"""Package only our plugin and its documentation, never game/framework DLLs."""
import hashlib,json,sys,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
NAME='Outward-Companheiros-v0.3.0-Experimental'

def main():
    dll=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'src/bin/Release/net472/Outward.Companheiros.dll'
    data=dll.read_bytes()
    if data[:2]!=b'MZ':raise ValueError('Not a compiled plugin PE')
    output=ROOT/'dist';output.mkdir(exist_ok=True)
    files={'BepInEx/plugins/CompanheirosAurai/Outward.Companheiros.dll':data,
           'LEIA-ME-PRIMEIRO.txt':b'\xef\xbb\xbf'+(ROOT/'LEIA-ME.txt').read_bytes(),
           'LICENSES.txt':(ROOT/'LICENSES.txt').read_bytes()}
    target=output/(NAME+'.zip')
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,payload in sorted(files.items()):
            stamp=(2026,10,24,0,2,2) if name.endswith('.dll') else (2026,1,1,0,0,0)
            info=zipfile.ZipInfo(NAME+'/'+name,date_time=stamp);info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100644<<16;z.writestr(info,payload)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None and len(z.namelist())==3
    checksum=hashlib.sha256(target.read_bytes()).hexdigest()
    (output/(NAME+'-SHA256.txt')).write_text(checksum+'  '+target.name+'\n',encoding='ascii')
    report={'version':'0.3.0','status':'EXPERIMENTAL_COMPILED_NO_LIVE_GAME_TEST',
        'game':'Outward Definitive Edition Steam - Mono',
        'reference':'Outward.GameLibs 2022.5.18-r.0 / Unity 2020.3.26',
        'requirements':['Windows x64','BepInExPack_Outward 5.4.19 with mono_fix','same plugin on every PC'],
        'sideLoaderRequired':False,'soloGameplayVerified':False,'coopGameplayVerified':False,
        'nativeTransactionUsed':True,'customNetworkProtocol':False,
        'zip':target.name,'bytes':target.stat().st_size,'sha256':checksum,
        'pluginSha256':hashlib.sha256(data).hexdigest(),'entries':list(files)}
    (output/'build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))
if __name__=='__main__':main()
