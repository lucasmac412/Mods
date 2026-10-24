"""Original transparent map portraits; no game artwork or external images."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PALETTE = [(56,222,199),(250,179,74),(250,107,99),(184,145,255),(89,186,255),(255,128,189)]

def portrait(color):
    im = Image.new('RGBA',(512,512)); d = ImageDraw.Draw(im)
    navy=(12,25,38); gold=(230,190,107)
    d.ellipse((24,16,488,480), fill=(3,9,16,160))
    d.ellipse((28,12,484,468), fill=gold)
    d.ellipse((40,24,472,456), fill=navy)
    d.ellipse((51,35,461,445), outline=color,width=8)
    d.polygon([(79,359),(125,293),(204,270),(308,270),(387,293),(433,359),(422,407),(341,439),(171,439),(90,407)], fill=color)
    d.polygon([(161,284),(199,269),(256,319),(313,269),(351,284),(304,432),(208,432)],fill=navy)
    d.polygon([(122,234),(146,130),(211,69),(256,51),(301,69),(366,130),(390,234),(345,292),(167,292)],fill=color)
    d.line([(140,224),(164,138),(216,89),(256,75),(296,89),(348,138),(372,224)],fill=gold,width=7)
    d.polygon([(169,185),(218,123),(256,101),(294,123),(343,185),(326,262),(291,295),(221,295),(186,262)],fill=(11,20,30))
    d.polygon([(205,195),(256,161),(307,195),(300,247),(278,273),(234,273),(212,247)],fill=(223,175,126))
    d.polygon([(202,193),(231,169),(256,157),(281,169),(310,193),(280,185),(255,176),(230,185)],fill=(19,31,44))
    d.line([(218,217),(239,217)],fill=(15,31,41),width=9)
    d.line([(273,217),(294,217)],fill=(15,31,41),width=9)
    d.line([(246,251),(266,251)],fill=(133,84,62),width=5)
    d.polygon([(255,316),(271,335),(255,355),(239,335)],fill=gold)
    d.line([(129,318),(159,357),(147,398)],fill=gold,width=8)
    d.line([(383,318),(353,357),(365,398)],fill=gold,width=8)
    d.ellipse((96,307,120,331),fill=gold);d.ellipse((392,307,416,331),fill=gold)
    return im.resize((128,128),Image.Resampling.LANCZOS)

def main():
    for index,color in enumerate(PALETTE): portrait(color).save(ROOT/'assets'/f'player-{index}.png',optimize=True)
    im=Image.new('RGBA',(64,48));d=ImageDraw.Draw(im);d.polygon([(32,2),(60,44),(32,33),(4,44)],fill=(255,255,255,255));im.save(ROOT/'assets/player-arrow.png',optimize=True)
    sheet=Image.new('RGB',(840,202),(12,24,35));d=ImageDraw.Draw(sheet)
    d.text((24,15),'COMPANHEIROS DE AURAI | ICONES ORIGINAIS DO MAPA',fill=(239,204,137))
    for index,color in enumerate(PALETTE):
        sheet.paste(portrait(color),(10+index*138,51),portrait(color));d.text((47+index*138,184),f'PLAYER {index+1}',fill=color)
    sheet.save(ROOT/'docs/retratos.png',optimize=True)
if __name__=='__main__': main()
