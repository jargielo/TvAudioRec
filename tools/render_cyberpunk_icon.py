#!/usr/bin/env python3
"""A single consistent cyberpunk logo for legacy and adaptive Android launchers."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
S=1024
C=S//2
R=268
DOT=53

def ring_image():
    alpha=Image.new("RGBA",(S,S),(0,0,0,0))
    def glow(x,y,rgb,rad,blur):
        mask=Image.new("L",(S,S))
        ImageDraw.Draw(mask).ellipse((x-rad,y-rad,x+rad,y+rad),fill=255)
        mask=mask.filter(ImageFilter.GaussianBlur(blur))
        layer=Image.new("RGBA",(S,S),rgb+(0,))
        layer.putalpha(mask)
        return layer
    def outline_glow(width, blur, intensity):
        mask=Image.new("L",(S,S),0)
        ImageDraw.Draw(mask).ellipse((C-R,C-R,C+R,C+R),outline=intensity,width=width)
        blurred=mask.filter(ImageFilter.GaussianBlur(blur))
        img=Image.new("RGBA",(S,S),(255,20,55,0))
        img.putalpha(blurred)
        return img
    for width,blur,intensity in [(58,33,75),(34,18,120),(17,7,150)]:
        alpha=Image.alpha_composite(alpha,outline_glow(width,blur,intensity))
    core=Image.new("RGBA",(S,S),(0,0,0,0))
    p=ImageDraw.Draw(core)
    p.ellipse((C-R,C-R,C+R,C+R),outline=(245,23,57,255),width=11)
    p.ellipse((C-R+2,C-R+2,C+R-2,C+R-2),outline=(255,239,244,255),width=6)
    alpha=Image.alpha_composite(alpha,core)
    alpha=Image.alpha_composite(alpha,glow(C-R,C,(255,10,45),84,21))
    dot=Image.new("RGBA",(S,S),(0,0,0,0))
    d=ImageDraw.Draw(dot)
    x=C-R
    d.ellipse((x-DOT,C-DOT,x+DOT,C+DOT),fill=(251,6,48,255),outline=(255,246,246,255),width=6)
    return Image.alpha_composite(alpha,dot)

overlay=ring_image()
flat=Image.new("RGB",(S,S),(0,0,0))
flat.paste(overlay,mask=overlay.getchannel("A"))
resources=Path("app/src/main/res")
destination=resources/"drawable-nodpi/grabber_cyberpunk.png"
destination.parent.mkdir(parents=True,exist_ok=True)
flat.resize((512,512),Image.Resampling.LANCZOS).save(destination,optimize=True)
dest=resources/"drawable-nodpi/cyberpunk_foreground.png"
overlay.resize((512,512),Image.Resampling.LANCZOS).save(dest,optimize=True)
for directory,size in [("mipmap-mdpi",48),("mipmap-hdpi",72),("mipmap-xhdpi",96),("mipmap-xxhdpi",144),("mipmap-xxxhdpi",192)]:
    p=resources/directory
    p.mkdir(parents=True,exist_ok=True)
    for name in ["ic_launcher.png","ic_launcher_round.png"]:
        file=p/name
        flat.resize((size,size),Image.Resampling.LANCZOS).save(file,optimize=True)
        assert Image.open(file).getpixel((0,0))==(0,0,0)
        print(file,file.stat().st_size)
assert not (resources/"drawable/grabber_cyberpunk.xml").exists()
