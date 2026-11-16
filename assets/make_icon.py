"""Dessine l'icône du Dashupeur : une manette avec un signal RSS, sur fond dégradé."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
S = 2048  # dessin en grand puis réduction = bords lisses

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# Fond : dégradé diagonal violet -> rose
top, bottom = (109, 40, 217), (236, 72, 153)
grad = Image.new("RGB", (S, S))
gd = ImageDraw.Draw(grad)
for y in range(S):
    gd.line([(0, y), (S, y)], fill=lerp(top, bottom, y / S))
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
img.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(img)

WHITE = (255, 255, 255, 255)
DARK = (46, 16, 101, 255)

# Signal RSS (en haut à droite)
cx, cy = int(S * 0.70), int(S * 0.36)
w = int(S * 0.045)
for r in (int(S * 0.13), int(S * 0.23)):
    d.arc([cx - r, cy - r, cx + r, cy + r], start=-90, end=0, fill=WHITE, width=w)
d.ellipse([cx - w, cy - w, cx + w, cy + w], fill=WHITE)
# Manette
bx0, by0, bx1, by1 = int(S * 0.16), int(S * 0.46), int(S * 0.84), int(S * 0.74)
d.rounded_rectangle([bx0, by0, bx1, by1], radius=int(S * 0.12), fill=WHITE)
g = int(S * 0.14)
d.ellipse([bx0 - int(S*0.01), by1 - g * 1.6, bx0 + g * 1.6, by1 + g * 0.55], fill=WHITE)
d.ellipse([bx1 - g * 1.6, by1 - g * 1.6, bx1 + int(S*0.01), by1 + g * 0.55], fill=WHITE)

# Croix directionnelle
px, py = int(S * 0.33), int(S * 0.60)
a, t = int(S * 0.075), int(S * 0.028)
d.rounded_rectangle([px - a, py - t, px + a, py + t], radius=t // 2, fill=DARK)
d.rounded_rectangle([px - t, py - a, px + t, py + a], radius=t // 2, fill=DARK)

# Boutons
bx, by = int(S * 0.67), int(S * 0.60)
o, r = int(S * 0.058), int(S * 0.032)
for (dx, dy), col in zip([(0, -1), (1, 0), (0, 1), (-1, 0)],
                         [(250, 204, 21), (239, 68, 68), (34, 197, 94), (59, 130, 246)]):
    x, y = bx + dx * o, by + dy * o
    d.ellipse([x - r, y - r, x + r, y + r], fill=col + (255,))

for size, name in [(512, "icon.png"), (64, "favicon.png")]:
    img.resize((size, size), Image.LANCZOS).save(out / name, optimize=True)
print("ok")
