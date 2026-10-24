#!/usr/bin/env python3
"""Deterministic original pixel art and vanilla data. Requires Pillow; no downloaded game assets."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
def data(path,obj):
 p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def png(path,img):
 p=ROOT/'assets/moonfist/textures'/path;p.parent.mkdir(parents=True,exist_ok=True);img.save(p)
pt={
 'item.moonfist.lunar_fists':'Punhos da Lua Azul',
 'item.moonfist.disciple_spawn_egg':'Selo do Discípulo Lunar',
 'entity.moonfist.lunar_disciple':'Discípulo Lunar',
 'key.moonfist.dodge':'Punhos: esquivar', 'key.moonfist.guard':'Punhos: defender / aparar',
 'key.moonfist.lock':'Punhos: fixar / soltar alvo', 'key.moonfist.stance':'Punhos: mudar postura',
 'key.moonfist.wave':'Punhos: onda de choque', 'key.moonfist.void':'Punhos: Cortes do Vazio',
 'death.attack.moonfist':'%1$s foi vencido pelos punhos de %2$s',
 'death.attack.moonfist.item':'%1$s foi vencido por %2$s usando %3$s'
}
en={
 'item.moonfist.lunar_fists':'Azure Moon Fists','item.moonfist.disciple_spawn_egg':'Lunar Disciple Seal',
 'entity.moonfist.lunar_disciple':'Lunar Disciple',
 'key.moonfist.dodge':'Fists: dodge','key.moonfist.guard':'Fists: guard / parry',
 'key.moonfist.lock':'Fists: lock / unlock target','key.moonfist.stance':'Fists: change stance',
 'key.moonfist.wave':'Fists: shockwave','key.moonfist.void':'Fists: Void Cuts',
 'death.attack.moonfist':'%1$s was defeated by %2$s\u2019s fists',
 'death.attack.moonfist.item':'%1$s was defeated by %2$s using %3$s'
}
for lang,obj in [('pt_br',pt),('en_us',en)]:data(f'assets/moonfist/lang/{lang}.json',obj)
for name in ['lunar_fists','disciple_spawn_egg']:
 data(f'assets/moonfist/items/{name}.json',{'model':{'type':'minecraft:model','model':f'moonfist:item/{name}'}})
 data(f'assets/moonfist/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'moonfist:item/{name}'}})
data('data/moonfist/recipe/lunar_fists.json',{'type':'minecraft:crafting_shaped','category':'equipment','pattern':[' IA','SLS','AI '],'key':{'I':'minecraft:iron_ingot','A':'minecraft:amethyst_shard','S':'minecraft:string','L':'minecraft:lapis_lazuli'},'result':{'id':'moonfist:lunar_fists','count':1}})
data('data/moonfist/recipe/disciple_spawn_egg.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':[' A ','APA',' A '],'key':{'A':'minecraft:amethyst_shard','P':'minecraft:paper'},'result':{'id':'moonfist:disciple_spawn_egg','count':1}})
data('data/moonfist/damage_type/martial_strike.json',{'exhaustion':0.1,'message_id':'moonfist','scaling':'never'})
data('data/minecraft/tags/damage_type/bypasses_cooldown.json',{'replace':False,'values':['moonfist:martial_strike']})
data('data/moonfist/loot_table/entities/lunar_disciple.json',{'type':'minecraft:entity','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'minecraft:amethyst_shard','functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':2,'max':5}}]}]}]})
# Compact paired gauntlets: articulated charcoal metal, luminous cyan knuckles, magenta seals.
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
for dx,dy in [(1,1),(16,9)]:
 d.polygon([(dx+2,dy+9),(dx+3,dy+3),(dx+6,dy+1),(dx+11,dy+3),(dx+13,dy+8),(dx+10,dy+13),(dx+3,dy+12)],fill='#10162c',outline='#090b15')
 d.polygon([(dx+4,dy+5),(dx+10,dy+4),(dx+11,dy+8),(dx+8,dy+11),(dx+3,dy+10)],fill='#36445f')
 d.line([(dx+4,dy+10),(dx+9,dy+10)],fill='#ff65cf',width=2)
 for x in [4,7,10]:d.rectangle((dx+x,dy+3,dx+x+1,dy+5),fill='#7dffff')
 d.line([(dx+4,dy+7),(dx+8,dy+6),(dx+9,dy+8)],fill='#24b8cc',width=1)
png('item/lunar_fists.png',im)
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.ellipse((2,2,29,29),fill='#15142e',outline='#7865a5',width=2)
d.ellipse((5,5,26,26),outline='#5ee9f0',width=2)
d.regular_polygon((16,16,10),6,rotation=30,outline='#b581e5',width=1)
d.polygon([(15,7),(20,13),(18,15),(22,20),(15,24),(10,20),(12,15),(10,12)],fill='#8efbff')
d.line((8,24,24,8),fill='#ed7ed6',width=2)
png('item/disciple_spawn_egg.png',im)
# Humanoid UV skin, built per cuboid UV layout so faces and limbs are consistent.
im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
def cuboid(u,v,w,h,depth,base,shade):
 # standard Minecraft ModelPart cube UV net
 faces=[(u+depth,v,w,depth),(u+depth+w,v,w,depth),(u,v+depth,depth,h),(u+depth,v+depth,w,h),(u+depth+w,v+depth,depth,h),(u+depth+w+depth,v+depth,w,h)]
 for i,(x,y,fw,fh) in enumerate(faces):
  d.rectangle((x,y,x+fw-1,y+fh-1),fill=shade if i%2 else base)
 # original engraved cyan/magenta energy channels on front and back
 for idx in [3,5]:
  x,y,fw,fh=faces[idx]
  for line in range(2,fh-1,4):d.line((x,y+line,x+fw-1,y+line),fill='#51cbd7',width=1)
  if fh>8:d.line((x+fw//2,y+1,x+fw//2,y+fh-2),fill='#b87bb8')
cuboid(0,0,8,8,8,'#cfa88c','#b68a78')
cuboid(16,16,8,12,4,'#273047','#161c32')
cuboid(40,16,4,12,4,'#cfa88c','#b68a78')
cuboid(32,48,4,12,4,'#cfa88c','#b68a78')
cuboid(0,16,4,12,4,'#20213b','#16182d')
cuboid(16,48,4,12,4,'#20213b','#16182d')
# face: glowing eyes, dark swept hair, pink forehead rune; no borrowed character skin.
d.rectangle((8,8,15,10),fill='#1c1833');d.rectangle((8,11,9,13),fill='#1c1833')
d.rectangle((10,12,11,12),fill='#9dffff');d.rectangle((13,12,14,12),fill='#9dffff')
d.point((12,10),fill='#f783d1');d.line((11,15,13,15),fill='#775f69')
png('entity/lunar_disciple.png',im)
print('Generated original assets and data')
