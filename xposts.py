"""Espace X : transforme des news en tweets séparés ou en thread, prêts à copier-coller sur X."""
import re
from datetime import datetime
from urllib.parse import quote

TWEET_MAX = 280
URL_LENGTH = 23  # X compte chaque lien comme 23 caractères, quelle que soit sa longueur
URL_RE = re.compile(r"https?://\S+")

# Plages de caractères comptés 1 par X (le reste, dont les emojis, compte 2) — d'après twitter-text
_LIGHT_RANGES = ((0, 4351), (8192, 8205), (8208, 8223), (8242, 8247))


def tweet_length(text: str) -> int:
    """Longueur d'un tweet telle que X la calcule."""
    links = URL_RE.findall(text)
    rest = URL_RE.sub("", text)
    weight = sum(1 if any(lo <= ord(ch) <= hi for lo, hi in _LIGHT_RANGES) else 2 for ch in rest)
    return weight + URL_LENGTH * len(links)


def _shorten(text: str, budget: int) -> str:
    """Coupe le texte sur un mot pour qu'il tienne dans le budget (en longueur X)."""
    # Espaces nettoyés ligne par ligne : les sauts de ligne voulus sont conservés
    text = "\n".join(" ".join(line.split()) for line in text.strip().splitlines())
    if tweet_length(text) <= budget:
        return text
    words = text.split(" ")
    while words and tweet_length(" ".join(words) + "…") > budget:
        words.pop()
    return (" ".join(words).rstrip(",;:.-–") + "…") if words else ""


def _first_sentence(summary: str) -> str:
    """1re vraie phrase du résumé (les formules comme « Greetings, explorers! » sont sautées)."""
    sentences = re.findall(r"[^.!?]+[.!?]+", summary or "") or [summary or ""]
    for sentence in sentences:
        if len(sentence.strip()) >= 40:
            return sentence.strip()
    return ""


def build_tweet(item: dict, *, prefix: str = "", body: str | None = None, with_link: bool = True) -> str:
    """Tweet prêt à poster : accroche + lien, coupé pour tenir dans 280 caractères.

    `body` remplace le texte par défaut (titre + 1re phrase du résumé), par exemple un texte rédigé par l'IA.
    """
    link = f"\n\n🔗 {item['url']}" if with_link and item.get("url") else ""
    budget = TWEET_MAX - tweet_length(prefix) - tweet_length(link)
    if body is None:
        title = item["title"].strip()
        sentence = _first_sentence(item.get("summary", ""))
        body = title
        # Ajoute la 1re phrase du résumé si elle apporte quelque chose et qu'il reste de la place
        if sentence and sentence.lower() not in title.lower():
            candidate = f"{title}\n\n{sentence}"
            body = candidate if tweet_length(candidate) <= budget else title
    return f"{prefix}{_shorten(body, budget)}{link}"


def thread_intro(count: int) -> str:
    return f"🎮 L'actu jeux vidéo du {datetime.now():%d/%m} en {count} news 🧵👇"


def build_thread(items: list[dict], bodies: dict | None = None, with_link: bool = True) -> list[str]:
    """Thread numéroté : un tweet d'intro puis un tweet par news (« 2/5 », « 3/5 »…)."""
    bodies = bodies or {}
    total = len(items) + 1
    tweets = [f"1/{total} {thread_intro(len(items))}"]
    for pos, item in enumerate(items, start=2):
        tweets.append(build_tweet(item, prefix=f"{pos}/{total} ", body=bodies.get(item["id"]), with_link=with_link))
    return tweets


def intent_url(text: str) -> str:
    """Lien qui ouvre X avec le tweet déjà rempli (il ne reste qu'à cliquer sur Poster)."""
    return "https://x.com/intent/post?text=" + quote(text, safe="")


def ai_tweet_body(item: dict, ai_cfg: dict) -> str:
    """Rédige l'accroche du tweet en français avec l'IA (sans le lien, ajouté ensuite)."""
    from engine import ask_ai

    prompt = (
        "Tu gères le compte X d'une chaîne YouTube d'actualité jeu vidéo (style Dashup). "
        "Écris UN tweet en français qui annonce cette news : une ou deux phrases factuelles et accrocheuses, "
        "un ton enthousiaste mais sobre, au plus un emoji, pas de hashtag, pas de lien, pas de guillemets. "
        "200 caractères maximum. Réponds uniquement avec le texte du tweet.\n\n"
        f"Titre : {item['title']}\nContenu : {item.get('summary', '')}"
    )
    return ask_ai(prompt, ai_cfg).strip().strip('"«» ')
