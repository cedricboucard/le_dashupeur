"""Serveur MCP du Dashupeur : ton assistant IA (Claude Desktop, Claude Code, Gemini CLI…) pilote l'app.

Il lit la boîte de réception, prépare l'émission « On épluche l'actu » et rédige les tweets de l'Espace X, avec ton abonnement :
aucune clé API n'est nécessaire. Lancement : venv/bin/python mcp_server.py (transport stdio).
"""
from datetime import datetime, timedelta, timezone
from typing import Literal

from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations

from db import (add_x_pick, get_all_news, get_conn, get_news, get_x_picks, init_db, remove_x_pick, set_x_tweet,
                update_status)
from engine import export_hebdo, fetch_all_feeds, parse_published
from xposts import TWEET_MAX, build_tweet, tweet_length

STATUS_LABELS = {"major": "🔥 gros sujet", "vrac": "⚡ épluchure", "inbox": "📥 à trier", "ignored": "🗑️ ignorée"}

mcp = MCPServer(
    name="le-dashupeur",
    title="Le Dashupeur",
    instructions=(
        "Le Dashupeur est la régie d'actualité gaming d'une chaîne YouTube française (émission hebdomadaire « On épluche l'actu », "
        "style Dashup : MMO, free-to-play, bêtas, annonces, sorties, fermetures). Les news viennent de flux RSS "
        "(presse, YouTube, Steam). Statuts : inbox (à trier), major (gros sujet), vrac (épluchures : news courtes), ignored. "
        "L'Espace X est une sélection indépendante pour des tweets en français. Réponds à l'utilisateur en français."
    ),
)
init_db()

READ_ONLY = ToolAnnotations(readOnlyHint=True, openWorldHint=False)


def _brief(item: dict, summary_chars: int = 220) -> dict:
    """Version compacte d'une news (moins de texte = plus de news lisibles d'un coup)."""
    dt = parse_published(item.get("published"))
    summary = (item.get("summary") or "").strip()
    return {
        "id": item["id"],
        "titre": item["title"],
        "source": item["source"],
        "score": item["score"],
        "date": dt.astimezone().strftime("%d/%m %H:%M") if dt else "inconnue",
        "statut": item.get("status", ""),
        "resume": summary[:summary_chars] + ("…" if len(summary) > summary_chars else ""),
    }


def _existing_ids(news_ids: list[str]) -> tuple[list[str], list[str]]:
    with get_conn() as conn:
        found = {r[0] for r in conn.execute(
            f"SELECT id FROM news WHERE id IN ({','.join('?' * len(news_ids))})", news_ids)} if news_ids else set()
    return [i for i in news_ids if i in found], [i for i in news_ids if i not in found]


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=False, openWorldHint=True, idempotentHint=True))
def scanner_actualite() -> str:
    """Récupère les dernières news de tous les flux RSS (presse, YouTube, Steam) et les ajoute à la boîte de
    réception. Les doublons sont ignorés. À lancer avant de préparer l'épisode si le dernier scan est ancien."""
    added, errors = fetch_all_feeds()
    message = f"Scan terminé : {added} nouvelle(s) news."
    if errors:
        message += f" {len(errors)} flux en erreur : " + " ; ".join(errors[:5])
    return message


@mcp.tool(annotations=READ_ONLY)
def lister_news(
    statut: Literal["inbox", "major", "vrac", "ignored", "toutes"] = "inbox",
    jours: int = 7,
    recherche: str = "",
    score_min: int = 0,
    limite: int = 120,
) -> list[dict]:
    """Liste les news, des plus récentes aux plus anciennes.

    - statut : inbox (à trier, par défaut), major, vrac, ignored ou toutes.
    - jours : ne garder que les news publiées ces N derniers jours (0 = pas de limite).
    - recherche : mot-clé cherché dans le titre, le résumé et la source.
    - score_min : score de pertinence minimum (mots-clés MMO, F2P, bêta… ; 15+ = news chaude).
    - limite : nombre maximum de news renvoyées.
    Chaque news a un « id » à utiliser avec les autres outils."""
    if statut == "toutes":
        items = get_all_news(search=recherche, limit=5000)
    else:
        items = get_news(status=statut, min_score=0)
        if recherche:
            needle = recherche.lower()
            items = [n for n in items if needle in f"{n['title']} {n['summary']} {n['source']}".lower()]
    if jours > 0:
        since = datetime.now(timezone.utc) - timedelta(days=jours)
        items = [n for n in items if (dt := parse_published(n.get("published"))) and dt >= since]
    items = [n for n in items if n["score"] >= score_min]
    items.sort(key=lambda n: parse_published(n.get("published")) or datetime.min.replace(tzinfo=timezone.utc),
               reverse=True)
    return [_brief(n) for n in items[:max(1, limite)]]


@mcp.tool(annotations=READ_ONLY)
def lire_news(news_id: str) -> dict:
    """Détail complet d'une news : titre, lien, source, date, score, mots-clés, statut et résumé entier."""
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM news WHERE id = ?", (news_id,)).fetchone()
    if not row:
        raise ToolError(f"Aucune news avec l'id {news_id}.")
    item = dict(row)
    return _brief(item, summary_chars=2000) | {"lien": item["url"], "mots_cles": item["tags"]}


@mcp.tool(annotations=READ_ONLY)
def etat_hebdo() -> dict:
    """Ce qui est déjà prévu : gros sujets et épluchures de l'épisode, et news de l'Espace X (avec leur tweet)."""
    picks = get_x_picks()
    return {
        "gros_sujets": [_brief(n, 0) for n in get_news(status="major")],
        "epluchures": [_brief(n, 0) for n in get_news(status="vrac")],
        "espace_x": [_brief(n, 0) | {"tweet": n.get("x_tweet") or "(texte automatique)"} for n in picks],
        "a_trier": len(get_news(status="inbox")),
    }


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False, idempotentHint=True))
def classer_news(news_ids: list[str], statut: Literal["major", "vrac", "inbox", "ignored"]) -> str:
    """Range des news : major = gros sujet de l'épisode, vrac = épluchure (news courte), inbox = remettre à trier,
    ignored = écarter. Rien n'est supprimé : tout reste modifiable dans l'app."""
    ok, missing = _existing_ids(news_ids)
    for news_id in ok:
        update_status(news_id, statut)
    message = f"{len(ok)} news classée(s) en {STATUS_LABELS[statut]}."
    if missing:
        message += f" Id inconnus ignorés : {', '.join(missing)}."
    return message


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False, idempotentHint=True))
def gerer_espace_x(news_ids: list[str], action: Literal["ajouter", "retirer"] = "ajouter") -> str:
    """Ajoute des news à la sélection de l'Espace X (tweets), ou les en retire. Indépendant de l'émission."""
    ok, missing = _existing_ids(news_ids)
    for news_id in ok:
        (add_x_pick if action == "ajouter" else remove_x_pick)(news_id)
    verb = "ajoutée(s) à" if action == "ajouter" else "retirée(s) de"
    message = f"{len(ok)} news {verb} l'Espace X."
    if missing:
        message += f" Id inconnus ignorés : {', '.join(missing)}."
    return message


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False, idempotentHint=True))
def rediger_tweet(news_id: str, texte: str) -> dict:
    """Enregistre le tweet d'une news dans l'Espace X (la news y est ajoutée si besoin).

    Écris le tweet en français, SANS le lien : l'app ajoute « 🔗 lien » à la fin (et le numéro en mode thread).
    Le résultat donne le tweet final et sa longueur comptée comme X (lien = 23, emoji = 2, max 280).
    Un texte trop long est coupé par l'app : raccourcis-le si « trop_long » vaut true. Texte vide = revenir
    au texte automatique."""
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM news WHERE id = ?", (news_id,)).fetchone()
    if not row:
        raise ToolError(f"Aucune news avec l'id {news_id}.")
    item = dict(row)
    texte = texte.strip()
    add_x_pick(news_id)
    set_x_tweet(news_id, texte or None)
    with_link = f"{texte}\n\n🔗 {item['url']}" if texte else build_tweet(item)
    length = tweet_length(with_link)
    return {
        "tweet_final": with_link if length <= TWEET_MAX else build_tweet(item, body=texte or None),
        "longueur": length,
        "trop_long": length > TWEET_MAX,
    }


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False, openWorldHint=False))
def generer_pack_hebdo() -> str:
    """Génère le pack de l'épisode (script, description YouTube avec chapitres, dossiers des gros sujets) à partir
    des gros sujets et épluchures actuels. Les trailers ne sont pas téléchargés ici (bouton dans l'app)."""
    majors, vracs = get_news(status="major"), get_news(status="vrac")
    if not majors and not vracs:
        raise ToolError("L'épisode est vide : classe d'abord des news en major ou vrac.")
    out = export_hebdo(majors, vracs, download_videos=False, ai_cfg=None)
    return f"Pack généré dans {out} ({len(majors)} gros sujets, {len(vracs)} épluchures)."


@mcp.prompt(title="Préparer On épluche l'actu")
def preparer_hebdo(gros_sujets: int = 10, en_vrac: int = 8) -> str:
    """Consigne pour que l'assistant prépare l'épisode de la semaine de bout en bout."""
    return (
        "Prépare l'épisode de cette semaine d'« On épluche l'actu » avec les outils du Dashupeur :\n"
        "1. Regarde ce qui est déjà prévu avec etat_hebdo.\n"
        "2. Si besoin, lance scanner_actualite, puis lis les news à trier des 7 derniers jours avec lister_news.\n"
        f"3. Choisis {gros_sujets} GROS SUJETS (les plus importants pour le public : MMO, free-to-play, bêtas, "
        f"annonces majeures, sorties, fermetures) et {en_vrac} ÉPLUCHURES (news courtes). Jamais deux news sur la même histoire ; "
        "varie les jeux ; écarte les simples discussions Reddit et les guides.\n"
        "4. Montre-moi ta sélection (une ligne par news avec la raison) et attends mon accord.\n"
        "5. Après mon accord, applique-la avec classer_news (major puis vrac)."
    )


@mcp.prompt(title="Tweets de la semaine")
def tweets_de_la_semaine(nombre: int = 5) -> str:
    """Consigne pour rédiger des tweets en français dans l'Espace X."""
    return (
        f"Choisis les {nombre} news les plus marquantes des 2 derniers jours (lister_news) et rédige pour chacune "
        "un tweet en français, style Dashup : une ou deux phrases factuelles et accrocheuses, au plus un emoji, "
        "pas de hashtag, pas de lien (l'app l'ajoute). Montre-les-moi d'abord, puis enregistre-les avec "
        "rediger_tweet après mon accord. Vérifie que « trop_long » vaut false."
    )


if __name__ == "__main__":
    mcp.run("stdio")
