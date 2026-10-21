#!/usr/bin/env python3
"""Original MIT pixel textures for the celestial lantern; no Minecraft texture inputs.
Requires Pillow only for regeneration. The game uses the three bundled PNGs.
"""
from pathlib import Path
from PIL import Image, ImageDraw

DEST=Path(__file__).resolve().parents[1]/'src/main/resources/assets/celestial_dragon/textures/block'
DARK=(16,18,30);STONE=(27,31,47);EDGE=(48,52,68)
GOLD=(196,139,45);GLOW=(250,211,110);SHADE=(104,69,29)
TEAL=(25,111,133);CYAN=(58,206,217);ICE=(183,255,236)


def plate():
    im=Image.new('RGB',(32,32),DARK);d=ImageDraw.Draw(im)
    # Stable stone grain instead of a ticking texture or transparent layers.
    for y in range(2,30):
        for x in range(2,30):
            if (x*13+y*7)%17<4:d.point((x,y),STONE)
    d.rectangle((1,1,30,30),outline=SHADE,width=2)
    d.line([(2,29),(2,2),(29,2)],fill=GOLD,width=1)
    d.line([(3,28),(28,28),(28,3)],fill=EDGE,width=1)
    for x,y in [(4,4),(27,4),(4,27),(27,27)]:
        d.rectangle((x-1,y-1,x+1,y+1),fill=GOLD);d.point((x-1,y-1),fill=GLOW)
    return im,d


def side():
    im,d=plate()
    # A long suspended crystal and gold runes, clearly different from sea lantern glass.
    d.polygon([(15,4),(24,15),(15,27),(7,15)],fill=SHADE)
    d.polygon([(15,5),(22,15),(15,25),(9,15)],fill=GOLD)
    d.polygon([(15,7),(20,15),(15,23),(11,15)],fill=TEAL)
    d.polygon([(15,8),(16,15),(15,22),(12,15)],fill=CYAN)
    d.line([(15,10),(15,19)],fill=ICE,width=2)
    d.line([(4,12),(6,14),(4,16),(6,18)],fill=GLOW,width=1)
    d.line([(27,12),(25,14),(27,16),(25,18)],fill=GLOW,width=1)
    d.line([(12,4),(18,4)],fill=GLOW,width=1)
    d.line([(12,27),(18,27)],fill=GLOW,width=1)
    return im


def cap(bottom=False):
    im,d=plate()
    d.polygon([(15,4),(19,11),(27,15),(19,19),(15,27),(11,19),(4,15),(11,11)],outline=GOLD,width=2)
    d.rectangle((10,10,21,21),outline=GOLD,width=1)
    d.polygon([(15,9),(22,15),(15,22),(9,15)],fill=SHADE if bottom else TEAL)
    d.polygon([(15,12),(19,15),(15,19),(12,15)],fill=GOLD if bottom else CYAN)
    d.rectangle((15,14,16,16),fill=GLOW if bottom else ICE)
    return im


if __name__=='__main__':
    DEST.mkdir(parents=True,exist_ok=True)
    for name,im in [('celestial_lantern_side',side()),('celestial_lantern_top',cap()),('celestial_lantern_bottom',cap(True))]:
        im.save(DEST/(name+'.png'))
    print('Generated three original 32x32 opaque celestial lantern textures.')
