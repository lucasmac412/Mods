// Record the real standalone canvas preview in headless Chrome/Edge.
// No game, AI-generated footage or external assets are used.
import {spawn} from 'node:child_process';
import {existsSync} from 'node:fs';
import {readFile,writeFile,mkdir,mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.resolve(process.argv[2]||path.join(root,'dist/video'));
await mkdir(output,{recursive:true});
const candidates=[];
for(const base of [process.env.ProgramFiles,process.env['ProgramFiles(x86)']].filter(Boolean)){
 candidates.push(path.join(base,'Google/Chrome/Application/chrome.exe'),path.join(base,'Microsoft/Edge/Application/msedge.exe'));
}
const browser=candidates.find(existsSync);
if(!browser)throw new Error('Installed Chrome/Edge required to capture the actual preview.');
const profile=await mkdtemp(path.join(tmpdir(),'nioh-video-'));
const child=spawn(browser,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',
 '--disable-background-networking','--disable-component-update','--disable-background-timer-throttling',
 '--disable-renderer-backgrounding','--window-size=1280,900','--remote-debugging-port=0','--user-data-dir='+profile],{stdio:['ignore','ignore','pipe']});
let diagnostics='';child.stderr.on('data',data=>{diagnostics=(diagnostics+data.toString()).slice(-8000);});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let ws;
try{
 let port;
 for(let n=0;n<150;n++){
  try{port=(await readFile(path.join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await sleep(100);}
 }
 if(!port)throw new Error('Browser did not start: '+diagnostics);
 const pages=await(await fetch('http://127.0.0.1:'+port+'/json/list')).json();
 const page=pages.find(p=>p.type==='page');if(!page)throw new Error('No browser page');
 ws=new WebSocket(page.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
 let id=0;const pending=new Map();
 ws.addEventListener('message',e=>{const message=JSON.parse(e.data);if(!message.id)return;const item=pending.get(message.id);if(!item)return;
 pending.delete(message.id);if(message.error)item.reject(new Error(JSON.stringify(message.error)));else item.resolve(message.result);});
 const call=(method,params={})=>new Promise((resolve,reject)=>{const key=++id;pending.set(key,{resolve,reject});ws.send(JSON.stringify({id:key,method,params}));});
 await call('Page.enable');await call('Runtime.enable');
 await call('Emulation.setDeviceMetricsOverride',{width:1280,height:900,deviceScaleFactor:1,mobile:false});
 await call('Page.navigate',{url:pathToFileURL(path.join(root,'preview/Previa.html')).href});
 let ready=false;
 for(let n=0;n<100;n++){
  const result=await call('Runtime.evaluate',{expression:"typeof window.moonfistPreview === 'object'",returnByValue:true});
  if(result.result.value){ready=true;break;}await sleep(100);
 }
 if(!ready)throw new Error('Preview did not initialize.');
 const result=await call('Runtime.evaluate',{awaitPromise:true,returnByValue:true,timeout:65000,expression:`(async()=>{
  window.requestAnimationFrame=()=>0;
  await new Promise(r=>setTimeout(r,80));
  const source=document.getElementById('view');
  const recording=document.createElement('canvas');recording.width=1280;recording.height=720;
  const c=recording.getContext('2d');
  const scenes=[
   {title:'Luvas compactas e aura',stance:1,duration:2.4,actions:[]},
   {title:'Postura baixa',stance:0,duration:3.2,actions:[[.4,'light'],[1.5,'heavy']]},
   {title:'Postura média',stance:1,duration:3.5,actions:[[.4,'light'],[1.7,'heavy']]},
   {title:'Postura alta',stance:2,duration:3.7,actions:[[.4,'light'],[1.9,'heavy']]},
   {title:'Caps Lock · Cortes do Vazio',stance:1,duration:3.2,actions:[[.3,'caps']]},
   {title:'Especial em câmera lenta',stance:1,duration:5,slow:.5,actions:[[.35,'caps']]},
   {title:'Protótipo de combate',stance:1,duration:2.5,actions:[]}
  ];
  const chunks=[],log=[];let poster=null;
  const mime=MediaRecorder.isTypeSupported('video/webm;codecs=vp8')?'video/webm;codecs=vp8':'video/webm';
  const stream=recording.captureStream(30),recorder=new MediaRecorder(stream,{mimeType:mime,videoBitsPerSecond:3500000});
  recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data);};
  const done=new Promise(resolve=>recorder.onstop=resolve);
  let index=0,sceneStart=performance.now(),previous=sceneStart,triggered=new Set(),frames=0;
  reset();paused=false;setStance(scenes[0].stance);yaw=-.5;ui('glow').value='100';
  const totalDuration=scenes.reduce((s,scene)=>s+scene.duration,0);
  function render(scene,elapsed){
   c.fillStyle='#090e19';c.fillRect(0,0,1280,720);
   c.fillStyle='#eef7ff';c.font='bold 30px sans-serif';c.fillText('Punhos da Lua Azul',30,43);
   c.font='15px sans-serif';c.fillStyle='#a6bbd4';c.fillText('PRÉVIA INDEPENDENTE · FORA DO NIOH',850,40);
   c.drawImage(source,0,76,940,590);
   c.fillStyle='#142035';c.fillRect(954,76,302,590);
   c.fillStyle='#7beaff';c.font='bold 19px sans-serif';c.fillText(scene.title.startsWith('Caps')?'CORTES DO VAZIO':scene.title.startsWith('Especial')?'CÂMERA LENTA':scene.title.toUpperCase(),976,115);
   c.font='15px sans-serif';c.fillStyle='#cad9ee';c.fillText('Luvas próximas às mãos',976,155);c.fillText('Aura azul e rosa',976,183);
   c.fillStyle='#8dabc8';c.fillText('POSTURA',976,232);c.font='bold 25px sans-serif';c.fillStyle='#eef7ff';c.fillText(['Baixa','Média','Alta'][stance],976,265);
   c.font='15px sans-serif';c.fillStyle='#8dabc8';c.fillText('Ki simulado: '+Math.floor(ki),976,315);c.fillStyle='#28425a';c.fillRect(976,331,252,10);c.fillStyle='#77eeff';c.fillRect(976,331,252*ki/100,10);
   c.fillStyle='#e4b0d9';c.font='bold 18px sans-serif';c.fillText(active?active.m.name:'Em guarda',976,395);
   c.font='15px sans-serif';c.fillStyle='#9cb4cf';c.fillText('Impactos da sequência: '+hitTotal,976,432);c.fillText('Caps Lock: oito cortes',976,478);
   c.fillText('Modelos e movimentos',976,544);c.fillText('do protótipo de autoria.',976,570);
   c.fillStyle='#c8b9d8';c.font='17px sans-serif';c.fillText('Ainda não é uma arma equipável no jogo. Demonstração sem áudio.',30,700);
   c.fillStyle='#6f86a4';c.fillRect(30,678,1226*((scenes.slice(0,index).reduce((s,x)=>s+x.duration,0)+Math.min(elapsed,scene.duration))/totalDuration),3);
  }
  draw();render(scenes[0],0);recorder.start(500);
  const timer=setInterval(()=>{
   const now=performance.now();let scene=scenes[index],elapsed=(now-sceneStart)/1000;
   if(elapsed>=scene.duration){
    log.push({scene:scene.title,hits:hitTotal});index++;
    if(index>=scenes.length){clearInterval(timer);recorder.stop();return;}
    scene=scenes[index];sceneStart=now;elapsed=0;triggered=new Set();reset();setStance(scene.stance);
   }
   for(let n=0;n<scene.actions.length;n++)if(elapsed>=scene.actions[n][0]&&!triggered.has(n)){
    triggered.add(n);const type=scene.actions[n][1];
    if(type==='caps'){window.dispatchEvent(new KeyboardEvent('keydown',{code:'CapsLock',cancelable:true}));window.dispatchEvent(new KeyboardEvent('keyup',{code:'CapsLock',cancelable:true}));}
    else document.getElementById(type).click();
   }
   const delta=Math.min(.1,(now-previous)/1000);previous=now;
   tick(delta*(scene.slow||1));yaw=-.5+Math.sin((index+elapsed*.25)*.4)*.12;draw();render(scene,elapsed);frames++;
   if(index===5&&elapsed>1.9&&!poster)poster=recording.toDataURL('image/png').split(',')[1];
  },1000/30);
  await done;for(const track of stream.getTracks())track.stop();
  const blob=new Blob(chunks,{type:mime}),bytes=new Uint8Array(await blob.arrayBuffer());
  let binary='';for(let n=0;n<bytes.length;n+=32768)binary+=String.fromCharCode(...bytes.subarray(n,n+32768));
  return {video:btoa(binary),poster,metadata:{width:1280,height:720,target_fps:30,rendered_frames:frames,scenes:log,source:'actual standalone browser preview',nioh_gameplay:false,audio:false}};
 })()`});
 if(result.exceptionDetails)throw new Error(JSON.stringify(result.exceptionDetails));
 const value=result.result.value;if(!value?.video)throw new Error('Recorder returned no data.');
 const hits=value.metadata.scenes.map(s=>s.hits),expected=[0,3,4,3,8,8,0];
 if(JSON.stringify(hits)!==JSON.stringify(expected))throw new Error('Recording incomplete: '+JSON.stringify(value.metadata));
 await writeFile(path.join(output,'nioh-gloves-preview.webm'),Buffer.from(value.video,'base64'));
 if(value.poster)await writeFile(path.join(output,'poster.png'),Buffer.from(value.poster,'base64'));
 await writeFile(path.join(output,'recording.json'),JSON.stringify(value.metadata,null,2)+'\n');
 console.log(JSON.stringify(value.metadata,null,2));
 await call('Browser.close').catch(()=>{});
}finally{
 ws?.close();child.kill();await sleep(500);await rm(profile,{recursive:true,force:true,maxRetries:5,retryDelay:500}).catch(()=>{});
}
