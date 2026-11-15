import hashlib
import re
from datetime import datetime
from pathlib import Path

import feedparser
import requests
import yt_dlp
from bs4 import BeautifulSoup

from config import KEYWORDS_WEIGHTS, RSS_FEEDS, USER_AGENT
from db import upsert_news

EXPORTS_DIR = Path(__file__).parent / "exports"


def clean_html(raw_html: str) -> str:
    if not raw_html:
        return ""
    text = BeautifulSoup(raw_html, "html.parser").get_text(separator=" ")
    return re.sub(r"\s+", " ", text).strip()[:600]


def score_article(title: str, summary: str) -> tuple[int, list[str]]:
    content = f"{title} {summary}".lower()
    score = 0
    matched_tags = []
    for kw, weight in KEYWORDS_WEIGHTS.items():
        if kw in content:
            score += weight
            matched_tags.append(kw)
    return score, matched_tags


def fetch_all_feeds() -> tuple[int, list[str]]:
    added = 0
    errors = []
    headers = {"User-Agent": USER_AGENT}

    for source_name, feed_url in RSS_FEEDS.items():
        try:
            resp = requests.get(feed_url, headers=headers, timeout=10)
            resp.raise_for_status()
            parsed = feedparser.parse(resp.content)

            for entry in parsed.entries[:20]:
                url = entry.get("link", "")
                title = entry.get("title", "Sans titre").strip()
                if not url:
                    continue

                raw_summary = entry.get("summary", entry.get("description", ""))
                summary = clean_html(raw_summary)
                score, tags = score_article(title, summary)
                news_id = hashlib.md5(url.encode("utf-8")).hexdigest()

                inserted = upsert_news({
                    "id": news_id,
                    "title": title,
                    "url": url,
                    "source": source_name,
                    "published": entry.get("published", "")[:25],
                    "summary": summary,
                    "score": score,
                    "tags": ", ".join(tags),
                })
                if inserted:
                    added += 1
        except Exception as e:
            errors.append(f"{source_name}: {e}")

    return added, errors


def summarize_with_ai(title: str, summary: str, api_key: str, base_url: str = "", model: str = "gpt-4o-mini") -> str:
    """Résume et traduit en français via OpenAI ou Ollama (si base_url renseignée)."""
    if not api_key and not base_url:
        return summary

    from openai import OpenAI

    client_kwargs = {"api_key": api_key or "ollama"}
    if base_url:
        client_kwargs["base_url"] = base_url

    client = OpenAI(**client_kwargs)
    prompt = (
        "Tu es chroniqueur pour une chaîne YouTube d'actualité jeu vidéo (style Dashup). "
        "Traduis et résume cette news en français en 3 points courts et percutants pour un script vidéo.\n\n"
        f"Titre : {title}\nContenu : {summary}"
    )

    resp = client.chat.completions.create(
        model=model,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.5,
    )
    return resp.choices[0].message.content.strip()


def download_trailer(query: str, output_dir: Path) -> str:
    """Télécharge le 1er trailer YouTube correspondant via yt-dlp."""
    output_dir.mkdir(parents=True, exist_ok=True)
    ydl_opts = {
        "format": "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
        "outtmpl": str(output_dir / "%(title).50s.%(ext)s"),
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
    }
    search_term = f"ytsearch1:{query} official trailer"
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(search_term, download=True)
        entries = info.get("entries", [info])
        if entries:
            return entries[0].get("title", "Trailer téléchargé")
    return "Aucun trailer trouvé"


def export_hebdo(
    majors: list[dict],
    vracs: list[dict],
    download_videos: bool = False,
    ai_cfg: dict | None = None,
) -> Path:
    date_str = datetime.now().strftime("%Y-%m-%d_%H-%M")
    export_dir = EXPORTS_DIR / f"Hebdo_{date_str}"
    export_dir.mkdir(parents=True, exist_ok=True)

    script_lines = [f"# 🎬 SCRIPT L'HEBDO DU {datetime.now().strftime('%d/%m/%Y')}\n"]
    desc_lines = [
        "Bienvenue dans ce nouvel épisode de L'Hebdo !\n",
        "⏱️ CHAPITRES :",
        "00:00 - Introduction",
    ]
    links_lines = ["\n🔗 SOURCES & LIENS :"]

    script_lines.append("## 🔥 GROS SUJETS\n")
    for idx, item in enumerate(majors, start=1):
        folder_name = f"{idx:02d}_{re.sub(r'[^a-zA-Z0-9_-]', '_', item['title'][:25])}"
        item_dir = export_dir / folder_name
        item_dir.mkdir(exist_ok=True)

        body = item["summary"]
        if ai_cfg and (ai_cfg.get("api_key") or ai_cfg.get("base_url")):
            try:
                body = summarize_with_ai(
                    item["title"],
                    item["summary"],
                    api_key=ai_cfg.get("api_key", ""),
                    base_url=ai_cfg.get("base_url", ""),
                    model=ai_cfg.get("model", "gpt-4o-mini"),
                )
            except Exception as e:
                body = f"[Erreur IA: {e}]\n{item['summary']}"

        script_lines.append(f"### {idx}. {item['title']}")
        script_lines.append(f"*Source : {item['source']} | Tags : {item['tags']}*\n")
        script_lines.append(f"{body}\n")
        script_lines.append(f"Lien : {item['url']}\n---\n")

        desc_lines.append(f"{idx * 3:02d}:00 - {item['title']}")
        links_lines.append(f"- {item['title']} : {item['url']}")

        if download_videos:
            try:
                download_trailer(item["title"], item_dir)
            except Exception as e:
                (item_dir / "erreur_dl.txt").write_text(str(e), encoding="utf-8")

    script_lines.append("\n## ⚡ NEWS EN VRAC\n")
    desc_lines.append("XX:XX - News en vrac")
    for item in vracs:
        script_lines.append(f"- **{item['title']}** ({item['source']}) -> {item['url']}")
        script_lines.append(f"  > {item['summary'][:200]}...\n")
        links_lines.append(f"- [Vrac] {item['title']} : {item['url']}")

    (export_dir / "script_hebdo.md").write_text("\n".join(script_lines), encoding="utf-8")
    (export_dir / "description_youtube.txt").write_text(
        "\n".join(desc_lines + links_lines), encoding="utf-8"
    )

    return export_dir
