"""Tiny isometric renderer for vanilla mob models + dyed leather armor (textures from the 26.x client jar).
Approximate, resource-pack-free looks of the NaughtyList mobs. Model units = pixels, y up, mob faces +z."""
import math
from PIL import Image, ImageDraw, ImageFont
T = '/workspace/naughtylist/tex/'
_tex = {}
def tex(n):
    if n not in _tex: _tex[n] = Image.open(T + n + '.png').convert('RGBA')
    return _tex[n]

def tinted(n, rgb):
    k = (n, rgb)
    if k not in _tex:
        im = tex(n).copy(); px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = px[x, y]
                px[x, y] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
        _tex[k] = im
    return _tex[k]

def rot(p, piv, rx=0.0, rz=0.0, ry=0.0):
    x, y, z = p[0] - piv[0], p[1] - piv[1], p[2] - piv[2]
    if rx: y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    if rz: x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    if ry: x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    return (x + piv[0], y + piv[1], z + piv[2])

class Scene:
    def __init__(self): self.quads = []
    def box(self, img, x0, y0, z0, w, h, d, u, v, inflate=0.0, mirror=False, piv=None, rx=0.0, rz=0.0, ry=0.0, tw=None, th=None):
        """img: texture image; (u,v) box UV origin in the texture's own pixel grid."""
        X0, Y0, Z0 = x0 - inflate, y0 - inflate, z0 - inflate
        sx, sy, sz = (w + 2 * inflate) / w, (h + 2 * inflate) / h, (d + 2 * inflate) / d
        piv = piv or (x0 + w / 2, y0 + h / 2, z0 + d / 2)
        def P(px, py, pz): return rot((X0 + px * sx, Y0 + py * sy, Z0 + pz * sz), piv, rx, rz, ry)
        faces = []
        # each face: texture rect (tu, tv, tw, th) and a function mapping texel (i,j)->3 corners
        # front (+z): col 0 at west unless mirrored
        faces.append(((u + d, v + d, w, h), lambda i, j: (P(i, h - j, d), P(i + 1, h - j, d), P(i, h - j - 1, d)), (0, 0, 1)))
        faces.append(((u + 2 * d + w, v + d, w, h), lambda i, j: (P(w - i, h - j, 0), P(w - i - 1, h - j, 0), P(w - i, h - j - 1, 0)), (0, 0, -1)))
        faces.append(((u + d + w, v + d, d, h), lambda i, j: (P(w, h - j, d - i), P(w, h - j, d - i - 1), P(w, h - j - 1, d - i)), (1, 0, 0)))
        faces.append(((u, v + d, d, h), lambda i, j: (P(0, h - j, i), P(0, h - j, i + 1), P(0, h - j - 1, i)), (-1, 0, 0)))
        faces.append(((u + d, v, w, d), lambda i, j: (P(i, h, j), P(i + 1, h, j), P(i, h, j + 1)), (0, 1, 0)))
        faces.append(((u + d + w, v, w, d), lambda i, j: (P(i, 0, d - j), P(i + 1, 0, d - j), P(i, 0, d - j - 1)), (0, -1, 0)))
        if mirror:  # swap side faces and flip texture columns
            faces[2], faces[3] = (faces[3][0], faces[2][1], faces[2][2]), (faces[2][0], faces[3][1], faces[3][2])
        px = img.load()
        for (tu, tv, fw, fh), fn, nrm in faces:
            n = rot((nrm[0], nrm[1], nrm[2]), (0, 0, 0), rx, rz, ry)
            if n[0] + n[2] + 1.2 * n[1] <= 0.01: continue
            shade = 1.0 if n[1] > 0.7 else (0.86 if abs(n[2]) >= abs(n[0]) else 0.68)
            for j in range(fh):
                for i in range(fw):
                    ii = fw - 1 - i if mirror else i
                    if tu + ii >= img.width or tv + j >= img.height: continue
                    c = px[tu + ii, tv + j]
                    if c[3] < 20: continue
                    a, b, cc = fn(i, j)
                    dd = (b[0] + cc[0] - a[0], b[1] + cc[1] - a[1], b[2] + cc[2] - a[2])
                    ctr = ((a[0] + dd[0]) / 2 + (b[0] + cc[0]) / 4 - a[0] / 2, 0, 0)
                    cx = (a[0] + b[0] + cc[0] + dd[0]) / 4; cy = (a[1] + b[1] + cc[1] + dd[1]) / 4; cz = (a[2] + b[2] + cc[2] + dd[2]) / 4
                    col = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255)
                    self.quads.append((cx + cz + 1.2 * cy + inflate * 0.01, (a, b, dd, cc), col))
    def flat(self, img, x0, y0, z, size, flipx=False, rx=0.0, piv=None):
        """a flat item sprite in the x-y plane (held item)."""
        w, h = img.size; s = size / w; px = img.load()
        piv = piv or (x0, y0, z)
        for j in range(h):
            for i in range(w):
                c = px[w - 1 - i if flipx else i, j]
                if c[3] < 20: continue
                pts = [rot((x0 + (i + a) * s, y0 + (h - j - b) * s, z), piv, rx) for a, b in ((0, 0), (1, 0), (1, 1), (0, 1))]
                cx = sum(p[0] for p in pts) / 4; cy = sum(p[1] for p in pts) / 4; cz = sum(p[2] for p in pts) / 4
                self.quads.append((cx + cz + 1.2 * cy + 0.5, tuple(pts), (c[0], c[1], c[2], 255)))
    def render(self, scale=8, pad=20, ox=0.0):
        cos30, sin30 = math.cos(math.pi / 6), 0.5
        def S(p): return ((p[0] + ox - p[2]) * cos30 * scale, -p[1] * scale + (p[0] + ox + p[2]) * sin30 * scale)
        pts = [S(p) for _, q, _ in self.quads for p in q]
        minx = min(p[0] for p in pts); miny = min(p[1] for p in pts)
        W = int(max(p[0] for p in pts) - minx) + 2 * pad; H = int(max(p[1] for p in pts) - miny) + 2 * pad
        im = Image.new('RGBA', (W, H), (0, 0, 0, 0)); dr = ImageDraw.Draw(im)
        for _, q, col in sorted(self.quads, key=lambda t: t[0]):
            dr.polygon([(S(p)[0] - minx + pad, S(p)[1] - miny + pad) for p in q], fill=col, outline=col)
        return im

LEATHER = 'equipment_humanoid_leather'; LEGS = 'equipment_humanoid_leggings_leather'
def armor(sc, dx, helmet=None, chest=None, legs=None, boots=None, gold_helmet=False, arms_forward=False, arm_x=(-8, 4)):
    def piece(texname, rgb, boxes):
        img = tinted(texname, rgb) if rgb else tex(texname)
        for b in boxes: sc.box(img, *b[0], **b[1])
        if rgb and texname.startswith('equipment_humanoid'):
            ov = tex(texname + '_overlay')
            for b in boxes: sc.box(ov, *b[0], **dict(b[1], inflate=b[1].get('inflate', 0) + 0.02))
    ar = dict(rx=-math.pi / 2, piv=None)
    if helmet: piece(LEATHER, helmet, [((dx - 4, 24, -4, 8, 8, 8, 0, 0), dict(inflate=1.0))])
    if gold_helmet: sc.box(tex('equipment_humanoid_gold'), dx - 4, 24, -4, 8, 8, 8, 0, 0, inflate=1.0)
    if chest:
        bx = [((dx - 4, 12, -2, 8, 12, 4, 16, 16), dict(inflate=1.0))]
        for k, ax in enumerate(arm_x):
            kw = dict(inflate=1.0, mirror=k == 1)
            if arms_forward: kw.update(rx=-math.pi / 2, piv=(dx + ax + 2, 22, 0))
            bx.append(((dx + ax, 12, -2, 4, 12, 4, 40, 16), kw))
        piece(LEATHER, chest, bx)
    if legs: piece(LEGS, legs, [((dx - 4, 12, -2, 8, 12, 4, 16, 16), dict(inflate=0.5)), ((dx - 4, 0, -2, 4, 12, 4, 0, 16), dict(inflate=0.5)), ((dx, 0, -2, 4, 12, 4, 0, 16), dict(inflate=0.5, mirror=True))])
    if boots: piece(LEATHER, boots, [((dx - 4, 0, -2, 4, 12, 4, 0, 16), dict(inflate=1.0)), ((dx, 0, -2, 4, 12, 4, 0, 16), dict(inflate=1.0, mirror=True))])

def skeleton(sc, dx=0, bow=True):
    t = tex('skeleton_skeleton')
    sc.box(t, dx - 4, 24, -4, 8, 8, 8, 0, 0)
    sc.box(t, dx - 4, 12, -2, 8, 12, 4, 16, 16)
    # right arm raised forward holding the bow, left arm down
    sc.box(t, dx - 6, 12, -1, 2, 12, 2, 40, 16, piv=(dx - 5, 22, 0), rx=-math.pi / 2.3)
    sc.box(t, dx + 4, 12, -1, 2, 12, 2, 40, 16, mirror=True)
    sc.box(t, dx - 3, 0, -1, 2, 12, 2, 0, 16)
    sc.box(t, dx + 1, 0, -1, 2, 12, 2, 0, 16, mirror=True)
    if bow: sc.flat(tex('item_bow'), dx - 11, 14, 11, 12)

def zombie(sc, dx=0, item=None):
    t = tex('zombie_zombie')
    sc.box(t, dx - 4, 24, -4, 8, 8, 8, 0, 0)
    sc.box(t, dx - 4, 24, -4, 8, 8, 8, 32, 0, inflate=0.5)
    sc.box(t, dx - 4, 12, -2, 8, 12, 4, 16, 16)
    for k, ax in enumerate((-8, 4)):
        sc.box(t, dx + ax, 12, -2, 4, 12, 4, 40, 16, mirror=k == 1, piv=(dx + ax + 2, 22, 0), rx=-math.pi / 2)
    sc.box(t, dx - 4, 0, -2, 4, 12, 4, 0, 16)
    sc.box(t, dx, 0, -2, 4, 12, 4, 0, 16, mirror=True)
    if item: sc.flat(tex(item), dx - 9, 16, 12, 8)

def snow_golem(sc, dx=0, pumpkin=False, scale=1.0):
    t = tex('snow_golem_snow_golem')
    s = scale
    def B(x0, y0, z0, w, h, d, u, v, **kw):
        # scale the model by drawing each texel bigger: emulate by scaled geometry via inflate-like stretch
        sc.box(t, dx + x0 * s, y0 * s, z0 * s, w, h, d, u, v, inflate=0, **kw) if s == 1 else sc.box(t, dx + x0 * s, y0 * s, z0 * s, w, h, d, u, v, **kw)
    sc.box(t, dx - 6, 0, -6, 12, 12, 12, 0, 36)
    sc.box(t, dx - 5, 11, -5, 10, 10, 10, 0, 16)
    if pumpkin:
        p = tex('carved_pumpkin')
        sc.box(_pumpkin(), dx - 5, 19.5, -5, 10, 10, 10, 0, 0)
    else:
        sc.box(t, dx - 4, 20, -4, 8, 8, 8, 0, 0, inflate=-0.5)
    for side in (-1, 1):
        x0 = dx + (5 if side > 0 else -17)
        sc.box(t, x0, 17, -1, 12, 2, 2, 32, 0, piv=(dx + 5 * side, 18, 0), rz=0.85 * side)

def _pumpkin():
    if 'pk' not in _tex:
        p = tex('carved_pumpkin').resize((10, 10), Image.NEAREST)
        side = Image.open(T + 'pumpkin_side.png').convert('RGBA').resize((10, 10), Image.NEAREST)
        top = Image.open(T + 'pumpkin_top.png').convert('RGBA').resize((10, 10), Image.NEAREST)
        im = Image.new('RGBA', (40, 20))
        im.paste(top, (10, 0)); im.paste(top, (20, 0)); im.paste(side, (0, 10)); im.paste(p, (10, 10)); im.paste(side, (20, 10)); im.paste(side, (30, 10))
        _tex['pk'] = im
    return _tex['pk']

def steve(sc, dx=0):
    t = tex('steve')
    sc.box(t, dx - 4, 24, -4, 8, 8, 8, 0, 0); sc.box(t, dx - 4, 12, -2, 8, 12, 4, 16, 16)
    sc.box(t, dx - 8, 12, -2, 4, 12, 4, 40, 16); sc.box(t, dx + 4, 12, -2, 4, 12, 4, 32, 48)
    sc.box(t, dx - 4, 0, -2, 4, 12, 4, 0, 16); sc.box(t, dx, 0, -2, 4, 12, 4, 16, 48)
