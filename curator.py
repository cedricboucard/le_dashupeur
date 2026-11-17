"""Proposition automatique de L'Hebdo : 10 gros sujets + quelques news en vrac, choisis par l'IA (ou par le score)."""
import json
import re
import unicodedata
from datetime import datetime, timedelta, timezone

from engine import ask_ai, parse_published

MAJOR_COUNT = 10
VRAC_COUNT = 8
MAX_CANDIDATES = 80  # Ollama coupe les consignes trop longues : on présélectionne les meilleures news
MAX_PER_SOURCE = 3   # Pas plus de 3 gros sujets venant de la même source (variété)

_STOPWORDS = {
    "the", "and", "for", "with", "from", "this", "that", "your", "new", "now", "game", "games", "official",
    "trailer", "les", "des", "une", "est", "pour", "dans", "avec", "sur", "qui", "son", "ses", "aux", "jeu",
}


def _words(title: str) -> set[str]:
    text = unicodedata.normalize("NFKD", title.lower()).encode("ascii", "ignore").decode()
    return {w for w in re.findall(r"[a-z0-9]{3,}", text) if w not in _STOPWORDS}


def _same_story(a: dict, b: dict) -> bool:
    """Deux titres qui partagent l'essentiel de leurs mots parlent sans doute de la même news."""
    wa, wb = _words(a["title"]), _words(b["title"])
    if not wa or not wb:
        return False
    return len(wa & wb) / min(len(wa), len(wb)) >= 0.6


def _rank(item: dict, now: datetime) -> float:
    """Score de pertinence + bonus de fraîcheur (jusqu'à +15 pour une news du jour) − malus Reddit."""
    dt = parse_published(item.get("published"))
    age_days = (now - dt).total_seconds() / 86400 if dt else 7
    # Les posts Reddit sont souvent des discussions plutôt que des news
    penalty = 10 if item["source"].startswith("Reddit") else 0
    return item["score"] + max(0.0, 15 - 2 * age_days) - penalty


def candidates(news: list[dict], days: int = 7) -> list[dict]:
    """News de la boîte de réception des `days` derniers jours, les plus prometteuses d'abord, sans doublons."""
    now = datetime.now(timezone.utc)
    since = now - timedelta(days=days)
    recent = [n for n in news if (dt := parse_published(n.get("published"))) and dt >= since]
    pool = recent if len(recent) >= MAJOR_COUNT + VRAC_COUNT else news
    pool = sorted(pool, key=lambda n: _rank(n, now), reverse=True)
    unique: list[dict] = []
    for item in pool:
        if not any(_same_story(item, kept) for kept in unique):
            unique.append(item)
        if len(unique) >= MAX_CANDIDATES:
            break
    return unique


def propose_by_score(news: list[dict]) -> dict:
    """Sans IA : les mieux notées (score + fraîcheur), avec au plus 3 gros sujets par source."""
    pool = candidates(news)
    major, vrac, per_source = [], [], {}
    for item in pool:
        if len(major) < MAJOR_COUNT and per_source.get(item["source"], 0) < MAX_PER_SOURCE:
            major.append({"item": item, "why": f"Score {item['score']} • {item['source']}"})
            per_source[item["source"]] = per_source.get(item["source"], 0) + 1
        elif len(vrac) < VRAC_COUNT:
            vrac.append({"item": item, "why": f"Score {item['score']} • {item['source']}"})
    return {"major": major, "vrac": vrac, "by": "score"}


def _extract_json(text: str) -> dict:
    """Récupère l'objet JSON de la réponse, même entouré de texte ou d'un bloc ```json."""
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise ValueError("l'IA n'a pas renvoyé de JSON")
    return json.loads(match.group(0))


def propose_with_ai(news: list[dict], ai_cfg: dict) -> dict:
    """L'IA choisit 10 gros sujets et quelques news en vrac parmi les meilleures candidates."""
    pool = candidates(news)
    by_ref = {f"n{i}": item for i, item in enumerate(pool, start=1)}
    lines = "\n".join(
        f"{ref} | {item['source']} | score {item['score']} | {item['title'][:110]}" for ref, item in by_ref.items()
    )
    prompt = (
        "Tu es rédacteur en chef de « L'Hebdo », l'émission hebdomadaire d'actualité jeu vidéo d'une chaîne "
        "YouTube française (style Dashup : MMO, free-to-play, bêtas, annonces, sorties, fermetures de jeux).\n"
        f"Parmi les news ci-dessous, choisis exactement {MAJOR_COUNT} GROS SUJETS (les plus importantes et "
        f"intéressantes pour le public) et {VRAC_COUNT} news EN VRAC (plus courtes, mais à mentionner). "
        "Ne prends jamais deux news qui racontent la même histoire. Varie les jeux.\n"
        "Réponds UNIQUEMENT avec ce JSON, sans autre texte :\n"
        '{"major": [{"id": "n1", "why": "raison en 8 mots max"}], "vrac": [{"id": "n2", "why": "..."}]}\n\n'
        f"NEWS (id | source | score | titre) :\n{lines}"
    )
    data = _extract_json(ask_ai(prompt, ai_cfg, temperature=0.2))

    used: set[str] = set()

    def pick(entries, limit):
        out = []
        for entry in entries or []:
            ref = str(entry.get("id", "")).strip() if isinstance(entry, dict) else str(entry).strip()
            if ref in by_ref and ref not in used and len(out) < limit:
                used.add(ref)
                why = entry.get("why", "") if isinstance(entry, dict) else ""
                out.append({"item": by_ref[ref], "why": str(why).strip()})
        return out

    major = pick(data.get("major"), MAJOR_COUNT)
    vrac = pick(data.get("vrac"), VRAC_COUNT)
    if not major:
        raise ValueError("l'IA n'a choisi aucune news valide")

    # Les petits modèles en rendent parfois moins : on complète avec les mieux notées
    for ref, item in by_ref.items():
        if ref in used:
            continue
        if len(major) < MAJOR_COUNT:
            major.append({"item": item, "why": "➕ complété par le score"})
        elif len(vrac) < VRAC_COUNT:
            vrac.append({"item": item, "why": "➕ complété par le score"})
        else:
            break
        used.add(ref)
    return {"major": major, "vrac": vrac, "by": "ia"}
