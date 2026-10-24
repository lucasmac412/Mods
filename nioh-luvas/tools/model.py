#!/usr/bin/env python3
"""Original glTF authoring rig. Not a Nioh G1M/G1A asset or converted game rig."""
import json,math,struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=json.loads((ROOT/'assets/weapon.json').read_text())
binary=bytearray(); views=[]; accessors=[]
def data(values,kind,count,component=5126,bounds=None,target=None):
    while len(binary)%4: binary.append(0)
    start=len(binary); fmt='f' if component==5126 else 'H'
    binary.extend(struct.pack('<'+str(len(values))+fmt,*([round(v,7) for v in values] if component==5126 else values)))
    view={'buffer':0,'byteOffset':start,'byteLength':len(binary)-start}
    if target: view['target']=target
    views.append(view); accessor={'bufferView':len(views)-1,'componentType':component,'count':count,'type':kind}
    if bounds: accessor.update(min=bounds[0],max=bounds[1])
    accessors.append(accessor); return len(accessors)-1
p=[];n=[];idx=[]
faces=[((1,0,0),[(.5,-.5,-.5),(.5,.5,-.5),(.5,.5,.5),(.5,-.5,.5)]),((-1,0,0),[(-.5,-.5,.5),(-.5,.5,.5),(-.5,.5,-.5),(-.5,-.5,-.5)]),((0,1,0),[(-.5,.5,-.5),(-.5,.5,.5),(.5,.5,.5),(.5,.5,-.5)]),((0,-1,0),[(-.5,-.5,.5),(-.5,-.5,-.5),(.5,-.5,-.5),(.5,-.5,.5)]),((0,0,1),[(-.5,-.5,.5),(.5,-.5,.5),(.5,.5,.5),(-.5,.5,.5)]),((0,0,-1),[(.5,-.5,-.5),(-.5,-.5,-.5),(-.5,.5,-.5),(.5,.5,-.5)])]
for normal,points in faces:
    start=len(p)//3
    for point in points:p.extend(point);n.extend(normal)
    idx.extend(start+i for i in [0,1,2,0,2,3])
pos=data(p,'VEC3',24,bounds=([-.5]*3,[.5]*3),target=34962);norm=data(n,'VEC3',24,target=34962);indices=data(idx,'SCALAR',36,5123,target=34963)
materials=[{'name':'Original mannequin','pbrMetallicRoughness':{'baseColorFactor':[.23,.30,.4,1],'metallicFactor':.35,'roughnessFactor':.5}}, {'name':'Compact black glove','pbrMetallicRoughness':{'baseColorFactor':[.025,.045,.065,1],'metallicFactor':.8,'roughnessFactor':.27}}, {'name':'Cyan knuckles','emissiveFactor':[.12,.85,1],'extensions':{'KHR_materials_emissive_strength':{'emissiveStrength':4}}}, {'name':'Magenta seams','emissiveFactor':[1,.12,.6],'extensions':{'KHR_materials_emissive_strength':{'emissiveStrength':3}}}]
meshes=[{'primitives':[{'attributes':{'POSITION':pos,'NORMAL':norm},'indices':indices,'material':i}]} for i in range(4)]
nodes=[]
def node(name,parent=None,translation=(0,0,0),scale=None,mesh=None):
    i=len(nodes);d={'name':name,'translation':list(translation)}
    if mesh is not None:d['mesh']=mesh
    if scale:d['scale']=list(scale)
    nodes.append(d)
    if parent is not None:nodes[parent].setdefault('children',[]).append(i)
    return i
root=node('OriginalAuthoringRig',translation=(0,.95,0));node('Torso',root,(0,.23,0),(.4,.48,.2),0);node('Head',root,(0,.71,0),(.20,.25,.2),0)
arms=[];legs=[]
for side,sign in [('L',-1),('R',1)]:
    arm=node('UpperArm.'+side,root,(sign*.30,.45,0));node('ArmBody.'+side,arm,(0,-.15,0),(.085,.3,.085),0)
    elbow=node('Elbow.'+side,arm,(0,-.3,0));node('ForearmBody.'+side,elbow,(0,-.12,0),(.065,.24,.065),0)
    hand=node('Hand.'+side,elbow,(0,-.26,0));node('CompactGlove.'+side,hand,(0,-.033,.008),(.104,.11,.073),1)
    node('Cuff.'+side,hand,(0,.021,0),(.077,.033,.061),1)
    node('LuminousSeam.'+side,hand,(0,.003,.038),(.080,.012,.008),3)
    for f in range(4):node('Knuckle.%s.%d'%(side,f),hand,((f-1.5)*.023,-.071,.044),(.018,.017,.012),2)
    arms.append((arm,elbow))
    leg=node('Thigh.'+side,root,(sign*.13,-.03,0));node('ThighBody.'+side,leg,(0,-.21,0),(.13,.42,.14),0)
    knee=node('Knee.'+side,leg,(0,-.43,0));node('ShinBody.'+side,knee,(0,-.19,0),(.09,.38,.11),0)
    node('Foot.'+side,knee,(0,-.4,.06),(.12,.08,.23),0);legs.append(leg)
def quat(x):return [round(math.sin(x/2),7),0,0,round(math.cos(x/2),7)]
for arm,elbow in arms:nodes[arm]['rotation']=quat(-.65);nodes[elbow]['rotation']=quat(-1.7)
animations=[]
for m in spec['moves']:
    frames=max(3,math.ceil(m['duration']*24)+1);times=[m['duration']*i/(frames-1) for i in range(frames)]
    time=data(times,'SCALAR',frames,bounds=([0],[m['duration']]))
    samplers=[];channels=[]
    for side,(arm,elbow) in enumerate(arms):
        for bone,is_elbow in [(arm,False),(elbow,True)]:
            values=[]
            for t in times:
                hit=max([math.exp(-((t-at)/.095)**2) for j,at in enumerate(m['hits']) if j%2==side] or [0])
                if m['input']=='caps':angle=(-1.7 if is_elbow else -.65)-math.sin(math.pi*min(1,t/.65))*.65
                else:angle=(-1.7+hit*1.55) if is_elbow else (-.65-hit*.50)
                values.extend(quat(angle))
            sampler=len(samplers);samplers.append({'input':time,'output':data(values,'VEC4',frames),'interpolation':'LINEAR'});channels.append({'sampler':sampler,'target':{'node':bone,'path':'rotation'}})
    if m['id'] in ('low_heavy','high_light'):
        values=[]
        for t in times:values.extend(quat(-1.8*math.exp(-((t-m['hits'][0])/.16)**2)))
        samplers.append({'input':time,'output':data(values,'VEC4',frames),'interpolation':'LINEAR'});channels.append({'sampler':len(samplers)-1,'target':{'node':legs[0],'path':'rotation'}})
    if m['id']=='high_heavy':
        values=[]
        for t in times:values.extend([0,.95+math.sin(math.pi*t/m['duration'])*.35,0])
        samplers.append({'input':time,'output':data(values,'VEC3',frames),'interpolation':'LINEAR'});channels.append({'sampler':len(samplers)-1,'target':{'node':root,'path':'translation'}})
    animations.append({'name':m['id'],'samplers':samplers,'channels':channels,'extras':{'native_animation_id':None,'authoring_only':True}})
scene={'asset':{'version':'2.0','generator':'Punhos da Lua Azul original authoring exporter'},'extensionsUsed':['KHR_materials_emissive_strength'],'scene':0,'scenes':[{'nodes':[root]}],'nodes':nodes,'meshes':meshes,'materials':materials,'animations':animations,'bufferViews':views,'accessors':accessors,'buffers':[{'byteLength':len(binary)}],'extras':{'status':'AUTHORING_ONLY_NOT_NIOH_FORMAT','native_game_skeleton':False}}
text=json.dumps(scene,separators=(',',':')).encode();text+=b' '*((-len(text))%4);binary+=b'\0'*((-len(binary))%4)
body=struct.pack('<II',len(text),0x4E4F534A)+text+struct.pack('<II',len(binary),0x004E4942)+binary
output=ROOT/'assets/Punhos-Lua-Azul-authoring.glb';output.write_bytes(struct.pack('<III',0x46546C67,2,len(body)+12)+body)
print('Created original authoring GLB:',len(nodes),'nodes,',len(animations),'clips,',output.stat().st_size,'bytes. Not a Nioh-ready asset.')
