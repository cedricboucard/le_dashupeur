"""Générateur de bandeaux (Lower Thirds) : PNG transparents prêts à poser sur la timeline de montage.

Chaque bandeau est une image de la taille de la vidéo (1920×1080 ou 3840×2160) au fond transparent,
avec le bandeau placé en bas à gauche dans la zone de sécurité. Il suffit de la poser au-dessus
de la vidéo, sans la redimensionner ni la déplacer.
"""
import io
import re
import zipfile
from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONTS_DIR = Path(__file__).parent / "assets" / "fonts"
FONT_TITLE = FONTS_DIR / "Lato-Black.ttf"
FONT_TEXT = FONTS_DIR / "Lato-Bold.ttf"

SIZES = {"1080p (1920×1080)": (1920, 1080), "4K (3840×2160)": (3840, 2160)}
STYLES = ["Dashupeur", "Plein", "Minimal"]

# Dégradé de l'icône du Dashupeur (violet → rose)
GRADIENT = ((109, 40, 217), (236, 72, 153))
PANEL = (18, 14, 32, 230)
WHITE = (255, 255, 255, 255)
SUBTITLE = (222, 214, 245, 255)

# Émojis et pictogrammes : la police ne les dessine pas, on les retire
EMOJI_RE = re.compile(
    "[\U0001F000-\U0001FAFF\U00002600-\U000027BF\U00002B00-\U00002BFF\U0000FE00-\U0000FE0F\U0000200D\U000020E3]"
)


@lru_cache(maxsize=64)
def _font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(path), size)


def clean_text(text: str) -> str:
    """Sans émojis ni espaces en trop."""
    return re.sub(r"\s+", " ", EMOJI_RE.sub("", text or "")).strip()


def missing_glyphs(text: str) -> list[str]:
    """Caractères que la police ne sait pas dessiner (japonais, coréen…) : ils s'afficheraient en carrés."""
    font = _font(FONT_TITLE, 40)
    notdef = font.getmask(chr(0x10FFFD))
    notdef_sig = (notdef.size, bytes(notdef))
    missing = []
    for ch in dict.fromkeys(clean_text(text)):
        if ch.isspace():
            continue
        mask = font.getmask(ch)
        if (mask.size, bytes(mask)) == notdef_sig and ch not in missing:
            missing.append(ch)
    return missing


def _text_width(text: str, font: ImageFont.FreeTypeFont) -> float:
    return font.getlength(text)


def _wrap(text: str, font: ImageFont.FreeTypeFont, max_width: int, max_lines: int) -> list[str] | None:
    """Découpe en lignes ; None si le texte ne tient pas dans max_lines."""
    words, lines, current = text.split(), [], ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if _text_width(candidate, font) <= max_width:
            current = candidate
            continue
        if current:
            lines.append(current)
        current = word
        if _text_width(word, font) > max_width:
            return None
    if current:
        lines.append(current)
    return lines if len(lines) <= max_lines else None


def _fit(text: str, path: Path, size: int, min_size: int, max_width: int, max_lines: int):
    """Plus grande taille de police où le texte tient ; à défaut, taille minimale et texte coupé avec « … »."""
    for s in range(size, min_size - 1, -2):
        font = _font(path, s)
        lines = _wrap(text, font, max_width, max_lines)
        if lines:
            return font, lines
    font = _font(path, min_size)
    words, lines = text.split(), []
    for _ in range(max_lines):
        line = ""
        while words and _text_width(f"{line} {words[0]}".strip(), font) <= max_width:
            line = f"{line} {words.pop(0)}".strip()
        if not line and words:  # mot trop long : on le coupe
            line = words.pop(0)
            while _text_width(line + "…", font) > max_width:
                line = line[:-1]
        lines.append(line)
    if words:
        while lines[-1] and _text_width(lines[-1] + "…", font) > max_width:
            lines[-1] = lines[-1][:-1]
        lines[-1] = lines[-1].rstrip() + "…"
    return font, lines


def _gradient(size: tuple[int, int], horizontal: bool = True) -> Image.Image:
    w, h = size
    steps = w if horizontal else h
    line = Image.new("RGBA", (steps, 1))
    (r1, g1, b1), (r2, g2, b2) = GRADIENT
    for i in range(steps):
        t = i / max(steps - 1, 1)
        line.putpixel((i, 0), (int(r1 + (r2 - r1) * t), int(g1 + (g2 - g1) * t), int(b1 + (b2 - b1) * t), 255))
    grad = line.resize((w, h)) if horizontal else line.rotate(-90, expand=True).resize((w, h))
    return grad


def _rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def _shadow(canvas: Image.Image, box: tuple[int, int, int, int], radius: int, blur: int, opacity: int = 140):
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(box, radius=radius, fill=(0, 0, 0, opacity))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))


def render_lower_third(
    title: str,
    subtitle: str = "",
    kicker: str = "",
    style: str = "Dashupeur",
    size: tuple[int, int] = (1920, 1080),
) -> Image.Image:
    """Dessine un bandeau sur une image transparente de la taille de la vidéo.

    title : texte principal (2 lignes au plus, la police rétrécit si besoin).
    subtitle : ligne secondaire (source, nom du jeu, date…), facultative.
    kicker : petite étiquette au-dessus (« GROS SUJET », « EN VRAC », « SORTIE STEAM »…), facultative.
    """
    W, H = size
    k = W / 1920  # toutes les mesures sont pensées en 1080p puis mises à l'échelle
    px = lambda v: max(1, round(v * k))  # noqa: E731

    title, subtitle, kicker = clean_text(title), clean_text(subtitle), clean_text(kicker).upper()
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    if not title:
        return canvas

    margin_x, margin_bottom = px(96), px(100)  # zone de sécurité
    pad_x, pad_y = px(34), px(22)
    max_text_w = px(1180)

    title_font, title_lines = _fit(title, FONT_TITLE, px(58), px(40), max_text_w, 2)
    sub_font, sub_lines = (None, [])
    if subtitle:
        sub_font, sub_lines = _fit(subtitle, FONT_TEXT, px(32), px(26), max_text_w, 1)

    title_lh = round(title_font.size * 1.12)
    sub_lh = round(sub_font.size * 1.25) if sub_font else 0
    gap = px(8) if sub_lines else 0
    text_h = title_lh * len(title_lines) + gap + sub_lh * len(sub_lines)
    text_w = max(
        [_text_width(line, title_font) for line in title_lines]
        + [_text_width(line, sub_font) for line in sub_lines]
    )

    accent_w = px(12) if style == "Dashupeur" else 0
    box_w = round(text_w) + 2 * pad_x + accent_w
    box_h = text_h + 2 * pad_y
    x0 = margin_x
    y1 = H - margin_bottom
    y0 = y1 - box_h
    radius = px(14)

    # --- Fond du bandeau ---
    if style == "Dashupeur":
        _shadow(canvas, (x0, y0 + px(6), x0 + box_w, y1 + px(6)), radius, px(18))
        panel = Image.new("RGBA", (box_w, box_h), PANEL)
        panel.paste(_gradient((accent_w, box_h), horizontal=False), (0, 0))
        canvas.paste(panel, (x0, y0), _rounded_mask((box_w, box_h), radius))
    elif style == "Plein":
        _shadow(canvas, (x0, y0 + px(6), x0 + box_w, y1 + px(6)), radius, px(18))
        canvas.paste(_gradient((box_w, box_h)), (x0, y0), _rounded_mask((box_w, box_h), radius))
    else:  # Minimal : texte seul avec ombre portée et soulignement en dégradé
        bar_h = px(6)
        canvas.paste(_gradient((round(text_w * 0.45) + px(40), bar_h)), (x0 + pad_x, y1 - pad_y + px(10)),
                     _rounded_mask((round(text_w * 0.45) + px(40), bar_h), bar_h // 2))

    # --- Textes ---
    text_layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(text_layer)
    tx, ty = x0 + accent_w + pad_x, y0 + pad_y
    sub_color = WHITE if style == "Plein" else SUBTITLE
    for line in title_lines:
        draw.text((tx, ty), line, font=title_font, fill=WHITE)
        ty += title_lh
    ty += gap
    for line in sub_lines:
        draw.text((tx, ty), line, font=sub_font, fill=sub_color)
        ty += sub_lh
    if style == "Minimal":  # sans fond : une ombre garantit la lisibilité sur une image claire
        alpha = text_layer.getchannel("A")
        shadow = Image.new("RGBA", size, (0, 0, 0, 0))
        shadow.putalpha(alpha.point(lambda a: a * 0.75).filter(ImageFilter.GaussianBlur(px(5))))
        canvas.alpha_composite(shadow, (px(2), px(3)))
    canvas.alpha_composite(text_layer)

    # --- Étiquette au-dessus du bandeau ---
    if kicker:
        kfont = _font(FONT_TITLE, px(24))
        kw = round(_text_width(kicker, kfont)) + 2 * px(16)
        kh = round(kfont.size * 1.2) + 2 * px(7)
        kx, ky = x0, y0 - kh - px(10)
        if style == "Plein":
            pill = Image.new("RGBA", (kw, kh), PANEL)
        else:
            pill = _gradient((kw, kh))
        canvas.paste(pill, (kx, ky), _rounded_mask((kw, kh), kh // 2))
        ImageDraw.Draw(canvas).text((kx + px(16), ky + px(7)), kicker, font=kfont, fill=WHITE)

    return canvas


def preview_on_background(img: Image.Image) -> Image.Image:
    """Bandeau posé sur un fond de test (dégradé sombre à gauche, clair à droite) pour juger la lisibilité."""
    w, h = img.size
    bg = Image.new("RGBA", (w, h))
    draw = ImageDraw.Draw(bg)
    for y in range(h):
        c = int(40 + 170 * y / h)
        draw.line([(0, y), (w, y)], fill=(c, int(c * 0.8), 90, 255))
    draw.rectangle((w * 0.55, 0, w, h), fill=(232, 232, 222, 255))
    bg.alpha_composite(img)
    return bg.convert("RGB")


def to_png_bytes(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def slug(text: str, max_len: int = 40) -> str:
    s = re.sub(r"[^a-zA-Z0-9_-]+", "_", clean_text(text)).strip("_")
    return s[:max_len] or "bandeau"


def build_zip(rows: list[dict], style: str, size: tuple[int, int]) -> bytes:
    """Zip de bandeaux numérotés (01_titre.png…) à partir de lignes {title, subtitle, kicker}."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_STORED) as zf:  # PNG déjà compressés
        n = 0
        for row in rows:
            if not clean_text(row.get("title", "")):
                continue
            n += 1
            img = render_lower_third(row["title"], row.get("subtitle", ""), row.get("kicker", ""), style, size)
            zf.writestr(f"{n:02d}_{slug(row['title'])}.png", to_png_bytes(img))
    return buf.getvalue()


def save_episode_lower_thirds(export_dir: Path, majors: list[dict], vracs: list[dict],
                              style: str = "Dashupeur", size: tuple[int, int] = (1920, 1080)) -> int:
    """Écrit les bandeaux de l'épisode dans export_dir/bandeaux. Renvoie le nombre de fichiers."""
    out = export_dir / "bandeaux"
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for kicker, items in (("Gros sujet", majors), ("En vrac", vracs)):
        for item in items:
            n += 1
            img = render_lower_third(item["title"], item.get("source", ""), kicker, style, size)
            img.save(out / f"{n:02d}_{slug(item['title'])}.png", optimize=True)
    return n
