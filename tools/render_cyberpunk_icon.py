#!/usr/bin/env python3
"""Produce ONE consistent solid-black raster icon for Huawei/EMUI launcher and inside app."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

S=1024
CX=CY=S//2
R=395
base=Image.new("RGBA",(S,S),(0,0,0,255))

def glow_ring(width,opacity,blur,color=(255,18,54)):
    mask=Image.new("L",(S,S),0)
    ImageDraw.Draw(mask).ellipse((CX-R,CY-R,CX+R,CY+R),outline=opacity,width=width)
    mask=mask.filter(ImageFilter.GaussianBlur(blur))
    layer=Image.new("RGBA",(S,S),(*color,0))
    layer.putalpha(mask)
    return layer

for width,opacity,blur in [(60,76,43),(43,100,26),(19,125,13),(8,120,7)]:
    base=Image.alpha_composite(base,glow_ring(width,opacity,blur))

line=Image.new("RGBA",(S,S),(0,0,0,0))
d=ImageDraw.Draw(line)
d.ellipse((CX-R,CY-R,CX+R,CY+R),outline=(255,34,63,255),width=17)
d.ellipse((CX-R+3,CY-R+3,CX+R-3,CY+R-3),outline=(255,243,243,255),width=11)
base=Image.alpha_composite(base,line)

arc=Image.new("RGBA",(S,S),(0,0,0,0))
ImageDraw.Draw(arc).arc((CX-R,CY-R,CX+R,CY+R),4,175,fill=(255,19,52,210),width=6)
base=Image.alpha_composite(base,arc.filter(ImageFilter.GaussianBlur(6)))

X,Y=CX-R,CY
node=Image.new("RGBA",(S,S),(0,0,0,0))
n=ImageDraw.Draw(node)
for radius,color in [(92,(255,10,42,37)),(78,(255,8,40,55)),(62,(255,14,42,77))]:
    n.ellipse((X-radius,Y-radius,X+radius,Y+radius),fill=color)
base=Image.alpha_composite(base,node.filter(ImageFilter.GaussianBlur(20)))
sharp=Image.new("RGBA",(S,S),(0,0,0,0))
ImageDraw.Draw(sharp).ellipse((X-82,Y-82,X+82,Y+82),fill=(252,7,44,255),
                              outline=(255,237,237,255),width=7)
base=Image.alpha_composite(base,sharp)

rgb=base.convert("RGB")
root=Path("app/src/main/res")
targets={"drawable-nodpi/grabber_cyberpunk.png":512,
         "mipmap-mdpi/ic_launcher.png":48,
         "mipmap-hdpi/ic_launcher.png":72,
         "mipmap-xhdpi/ic_launcher.png":96,
         "mipmap-xxhdpi/ic_launcher.png":144,
         "mipmap-xxxhdpi/ic_launcher.png":192}
for file,size in targets.items():
    dest=root/file
    dest.parent.mkdir(parents=True,exist_ok=True)
    rgb.resize((size,size),Image.Resampling.LANCZOS).save(dest,optimize=True)
    assert Image.open(dest).getpixel((0,0))==(0,0,0), "Icon background must be opaque black"
    print("RENDERED",dest,size,dest.stat().st_size)

# This build must never accidentally revert to the obsolete XML vector/adaptive icon.
assert not (root/"drawable/grabber_cyberpunk.xml").exists()
assert not (root/"mipmap-anydpi-v26/ic_launcher.xml").exists()
