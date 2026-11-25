"""The wallpapers of the store screenshots: a low-poly pattern in muted slate blue, low in contrast
and about as light at the top as at the bottom. store/wallpaper.png is the phone's and the
foldable's (portrait), store/wallpaper-tablet.png the tablet's (landscape); the triangles are about
the same size in both. Usage: python3 store/wallpaper.py store
"""
import sys, random
from PIL import Image, ImageDraw
out=sys.argv[1]
SS=2
def mix(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))

# A: low-poly triangles, muted slate blue
def low_poly(W, H, cols, rows, name):
    random.seed(7)
    img=Image.new("RGB",(W*SS,H*SS)); d=ImageDraw.Draw(img)
    pts=[[((x+ (random.uniform(-.35,.35) if 0<x<cols else 0))*W*SS/cols, (y+(random.uniform(-.35,.35) if 0<y<rows else 0))*H*SS/rows) for x in range(cols+1)] for y in range(rows+1)]
    top,bottom=(74,88,122),(52,63,92)
    for y in range(rows):
        for x in range(cols):
            a,b,c,e=pts[y][x],pts[y][x+1],pts[y+1][x],pts[y+1][x+1]
            for tri in ((a,b,e),(a,e,c)) if (x+y)%2==0 else ((a,b,c),(b,e,c)):
                cy=sum(p[1] for p in tri)/3/(H*SS)
                base=mix(top,bottom,cy); j=random.uniform(-7,7)
                d.polygon(tri, fill=tuple(max(0,min(255,int(v+j))) for v in base))
    img.resize((W,H), Image.LANCZOS).save(f"{out}/{name}.png")

low_poly(1080, 2424, 7, 15, "wallpaper")
low_poly(2560, 1600, 17, 10, "wallpaper-tablet")
