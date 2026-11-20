import sys; sys.path.insert(0, '/workspace/naughtylist/tools')
from mobrender import *
from PIL import Image, ImageDraw, ImageFont
RED=(176,46,38); BLACK=(29,29,33); WHITE=(249,255,254); BLUE=(60,68,170); GOLD=(254,216,61)
def R(fn, k=1.0, s=8):
    sc = Scene(); fn(sc); im = sc.render(scale=s, pad=4)
    return im if k == 1 else im.resize((int(im.width * k), int(im.height * k)), Image.NEAREST)
try:
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26); f2 = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 18)
except Exception:
    F = f2 = ImageFont.load_default()
def bg(W, H):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    for y in range(H):
        t = y / H; d.line([(0, y), (W, y)], fill=(int(70 + 60 * t), int(100 + 70 * t), int(160 + 60 * t)))
    d.rectangle([0, H - 70, W, H], fill=(238, 244, 250))
    return im
def sheet(out, items, title):
    gap = 40
    tmp = ImageDraw.Draw(Image.new('RGB', (1, 1)))
    cw = [max(i[0].width, tmp.textlength(i[1], font=F), tmp.textlength(i[2], font=f2)) for i in items]
    W = int(max(sum(cw) + gap * (len(items) + 1), tmp.textlength(title, font=F) + 2 * gap)); H = max(i[0].height for i in items) + 230
    im = bg(W, H); d = ImageDraw.Draw(im); x = gap + (W - sum(cw) - gap * (len(items) + 1)) // 2
    d.text((gap, 18), title, font=F, fill=(255, 255, 255))
    for (img, name, sub), w in zip(items, cw):
        im.paste(img, (int(x + (w - img.width) / 2), H - 90 - img.height), img)
        d.text((x, H - 62), name, font=F, fill=(160, 20, 20) if 'Evil' in name or 'Abom' in name or 'Grinch' in name or 'Nut' in name else (30, 90, 30))
        d.text((x, H - 32), sub, font=f2, fill=(40, 40, 60))
        x += int(w) + gap
    im.save(out, optimize=True); print(out, im.size)
P = '/workspace/naughtylist/previews/'
sheet(P + '1-snowmen.png', [
    (R(lambda s: snow_golem(s, pumpkin=True)), 'Snow Golem', 'normal, friendly (pumpkin)'),
    (R(lambda s: snow_golem(s)), 'Evil Snowman', 'no pumpkin = naughty'),
    (R(lambda s: snow_golem(s), 2.5), 'Abominable Snowman', '2.5x size, rare boss'),
    (R(steve), 'Player', 'for scale')], 'NaughtyList: snowmen (vanilla snow golem model, no resource pack)')
sheet(P + '2-grinch-goblin.png', [
    (R(lambda s: (zombie(s), armor(s, 0, helmet=RED, chest=RED, legs=RED, boots=BLACK, arms_forward=True))), 'Grinch Goblin', 'close-up: stolen Santa suit'),
    (R(lambda s: (zombie(s, item='item_diamond'), armor(s, 0, helmet=RED, chest=RED, legs=RED, boots=BLACK, arms_forward=True)), 0.6), 'Grinch Goblin', '0.6x size, holding your item'),
    (R(steve), 'Player', 'for scale')], 'NaughtyList: Grinch goblin (small zombie in a stolen Santa suit)')
sheet(P + '3-nutcrackers.png', [
    (R(lambda s: (skeleton(s), armor(s, 0, helmet=BLACK, chest=RED, legs=WHITE, boots=BLACK))), 'Nutcracker Guard', 'skeleton + dyed leather, bow'),
    (R(lambda s: (skeleton(s), armor(s, 0, gold_helmet=True, chest=RED, legs=BLUE, boots=BLACK))), 'Nutcracker Captain', 'gold "crown", tougher'),
    (R(steve), 'Player', 'for scale')], 'NaughtyList: nutcracker soldiers (guard big loot spots)')
