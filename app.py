from datetime import datetime
from pathlib import Path

import streamlit as st

from db import (add_x_pick, clear_inbox, clear_x_picks, get_all_news, get_news, get_x_picks, init_db, move_x_pick,
                remove_x_pick, update_status)
from curator import propose_by_score, propose_with_ai
from engine import export_hebdo, fetch_all_feeds, parse_published
from radar import _item, fetch_epic_free, fetch_gamerpower, fetch_steam_f2p, radar_news_id, send_to_hebdo
from trends import (SOURCE_LABELS, UNITS, active_sources, collect_all, compute_trends, history,
                    latest_snapshot, snapshot_times, trend_summary)
from xposts import TWEET_MAX, ai_tweet_body, build_thread, build_tweet, intent_url, tweet_length

ICON_PATH = Path(__file__).parent / "assets" / "favicon.png"

st.set_page_config(
    page_title="Le Dashupeur",
    page_icon=str(ICON_PATH) if ICON_PATH.exists() else "🎮",
    layout="wide",
)
init_db()


def format_age(published: str) -> str:
    """Date de publication lisible d'un coup d'œil : « aujourd'hui 14:32 », « il y a 4 jours »…"""
    dt = parse_published(published)
    if not dt:
        return "date inconnue"
    local = dt.astimezone()
    days = (datetime.now().astimezone().date() - local.date()).days
    if days <= 0:
        return f"🆕 aujourd'hui {local:%H:%M}"
    if days == 1:
        return f"hier {local:%H:%M}"
    if days < 7:
        return f"il y a {days} jours ({local:%d/%m})"
    return f"{local:%d/%m/%Y}"


st.title("🎮 Le Dashupeur — Régie d'Actualité Gaming")
st.caption("Veille automatisée, tri de l'émission « On épluche l'actu », scripts et récupération de trailers.")

# --- BARRE LATÉRALE : ACTIONS & CONFIG ---
with st.sidebar:
    st.header("📡 Veille RSS")
    if st.button("🔄 Scanner l'actualité", use_container_width=True, type="primary"):
        with st.spinner("Aspiration des flux en cours..."):
            count, errs = fetch_all_feeds()
        # Gardé en mémoire pour l'afficher après le rechargement de la page
        st.session_state["last_scan"] = (count, errs)
        st.rerun()

    if "last_scan" in st.session_state:
        count, errs = st.session_state["last_scan"]
        st.success(f"Scan terminé : {count} nouvelle(s) news.")
        if errs:
            with st.expander(f"⚠️ {len(errs)} flux en erreur"):
                for e in errs:
                    st.warning(e)

    if st.button("🧹 Vider la boîte de réception", use_container_width=True):
        clear_inbox()
        st.rerun()

    st.divider()
    st.header("🤖 Options IA (Optionnel)")
    use_ai = st.checkbox("Activer l'IA (résumés de l'émission, tweets en français)")
    ai_cfg = {}
    if use_ai:
        ai_provider = st.radio("Fournisseur", ["OpenAI", "Ollama (Local)"])
        if ai_provider == "OpenAI":
            ai_cfg["api_key"] = st.text_input("Clé API OpenAI", type="password")
            ai_cfg["model"] = st.text_input("Modèle", value="gpt-4o-mini")
        else:
            ai_cfg["base_url"] = st.text_input("URL Ollama", value="http://localhost:11434/v1")
            ai_cfg["model"] = st.text_input("Modèle Ollama", value="llama3")

    st.divider()
    min_score = st.slider("Score de pertinence minimum", 0, 50, 0, step=5)

ai_ready = use_ai and bool(ai_cfg.get("api_key") or ai_cfg.get("base_url"))

# --- ONGLETS PRINCIPAUX ---
inbox_items = get_news(status="inbox", min_score=min_score)
major_items = get_news(status="major")
vrac_items = get_news(status="vrac")
x_picks = get_x_picks()
x_pick_ids = {n["id"] for n in x_picks}

st.markdown(
    f"📥 **{len(inbox_items)}** à trier · 🔥 **{len(major_items)}** gros sujet(s) · "
    f"⚡ **{len(vrac_items)}** en vrac · 🐦 **{len(x_picks)}** pour X"
)

# Libellés fixes + key : l'onglet ouvert reste le même après un clic
tab_inbox, tab_hebdo, tab_radar, tab_trends, tab_x = st.tabs(
    ["📥 Boîte de réception", "🧅 On épluche l'actu", "🎁 Radar Jeux Gratuits & F2P", "📈 Tendances", "🐦 Espace X"],
    key="main_tab",
    on_change="rerun",
)

# --- ONGLET 1 : TRI DES NEWS ---
with tab_inbox:
    col_search, col_sort = st.columns([3, 1])
    search = col_search.text_input("🔍 Filtrer par mot-clé (ex: MMO, Steam, gratuit...)")
    sort_by = col_sort.selectbox(
        "↕️ Trier par",
        ["🔥 Pertinence", "🕒 Plus récentes", "🕰️ Plus anciennes"],
        key="inbox_sort",
    )
    filtered = [
        n for n in inbox_items
        if not search or search.lower() in (n["title"] + n["summary"] + n["tags"]).lower()
    ]
    if sort_by != "🔥 Pertinence":
        # Les news sans date connue restent toujours en fin de liste
        dated = [n for n in filtered if parse_published(n["published"])]
        undated = [n for n in filtered if not parse_published(n["published"])]
        dated.sort(key=lambda n: parse_published(n["published"]), reverse=(sort_by == "🕒 Plus récentes"))
        filtered = dated + undated

    if not filtered:
        st.info("Aucune news dans la boîte de réception. Clique sur 'Scanner l'actualité' à gauche !")

    for item in filtered:
        with st.container(border=True):
            col1, col2 = st.columns([4, 1])
            with col1:
                badge = f"🔥 Score: {item['score']}" if item["score"] >= 15 else f"Score: {item['score']}"
                st.markdown(f"**[{item['title']}]({item['url']})**")
                st.caption(
                    f"🕒 {format_age(item['published'])} • {item['source']} • {badge} • "
                    f"Tags: `{item['tags'] or 'aucun'}`"
                )
                if item["summary"]:
                    st.write(item["summary"][:280] + ("..." if len(item["summary"]) > 280 else ""))
            with col2:
                if st.button("🔥 Gros Sujet", key=f"maj_{item['id']}", use_container_width=True):
                    update_status(item["id"], "major")
                    st.rerun()
                if st.button("⚡ En Vrac", key=f"vrac_{item['id']}", use_container_width=True):
                    update_status(item["id"], "vrac")
                    st.rerun()
                if st.button("🗑️ Ignorer", key=f"ign_{item['id']}", use_container_width=True):
                    update_status(item["id"], "ignored")
                    st.rerun()
                if item["id"] in x_pick_ids:
                    st.caption("🐦 Dans l'Espace X")
                elif st.button("🐦 Pour X", key=f"x_{item['id']}", use_container_width=True):
                    add_x_pick(item["id"])
                    st.rerun()

# --- ONGLET 2 : PRÉPARATION DE L'ÉMISSION (On épluche l'actu) ---
def render_proposal(proposal: dict):
    """Proposition de l'IA (ou du score) à relire : tout est coché, décoche ce que tu ne veux pas."""
    by = "🤖 l'IA" if proposal["by"] == "ia" else "📊 le score et la fraîcheur"
    st.caption(f"Proposition faite par {by}. Décoche ce que tu ne veux pas, puis valide.")
    col_p_maj, col_p_vrac = st.columns(2)
    for col, kind, title in ((col_p_maj, "major", "🔥 Gros sujets"), (col_p_vrac, "vrac", "⚡ En vrac")):
        with col:
            st.markdown(f"**{title} ({len(proposal[kind])})**")
            for entry in proposal[kind]:
                item = entry["item"]
                st.checkbox(
                    f"[{item['title']}]({item['url']})",
                    value=True,
                    key=f"prop_{kind}_{item['id']}",
                    help=f"{item['source']} • {format_age(item['published'])}",
                )
                if entry["why"]:
                    st.caption(f"↳ {entry['why']}")

    col_ok, col_cancel, _ = st.columns([1, 1, 2])
    if col_ok.button("✅ Valider la proposition", type="primary", use_container_width=True):
        counts = {"major": 0, "vrac": 0}
        for kind in ("major", "vrac"):
            for entry in proposal[kind]:
                if st.session_state.get(f"prop_{kind}_{entry['item']['id']}", True):
                    update_status(entry["item"]["id"], kind)
                    counts[kind] += 1
        st.session_state.pop("hebdo_proposal", None)
        st.session_state["hebdo_flash"] = (
            f"✅ {counts['major']} gros sujet(s) et {counts['vrac']} news en vrac ajoutés à l'émission."
        )
        st.rerun()
    if col_cancel.button("✖️ Annuler", use_container_width=True):
        st.session_state.pop("hebdo_proposal", None)
        st.rerun()


with tab_hebdo:
    with st.container(border=True):
        st.markdown("#### 🤖 Proposition automatique")
        st.caption(
            "Choisit 10 gros sujets et 8 news en vrac dans ta boîte de réception (news de la semaine, sans doublons). "
            + ("L'IA de la barre latérale fait le choix." if ai_ready
               else "Sans IA activée dans la barre latérale, le choix se fait au score et à la fraîcheur.")
        )
        if flash := st.session_state.pop("hebdo_flash", None):
            st.success(flash)
        if st.button("🤖 Proposer l'épisode", disabled=not inbox_items):
            with st.spinner("L'IA choisit les sujets de la semaine…" if ai_ready else "Sélection en cours…"):
                try:
                    proposal = propose_with_ai(inbox_items, ai_cfg) if ai_ready else propose_by_score(inbox_items)
                except Exception as e:  # IA injoignable, réponse illisible…
                    st.error(f"L'IA n'a pas pu proposer de sélection ({e}). Voici la proposition au score à la place.")
                    proposal = propose_by_score(inbox_items)
            # Nouvelle proposition : on oublie les cases cochées de la précédente
            for key in [k for k in st.session_state if k.startswith("prop_")]:
                del st.session_state[key]
            st.session_state["hebdo_proposal"] = proposal
        if proposal := st.session_state.get("hebdo_proposal"):
            render_proposal(proposal)

    col_maj, col_vrac = st.columns(2)

    with col_maj:
        st.subheader("🔥 Gros Sujets")
        for item in major_items:
            with st.container(border=True):
                st.markdown(f"**{item['title']}**")
                if st.button("↩️ Retirer", key=f"rem_maj_{item['id']}"):
                    update_status(item["id"], "inbox")
                    st.rerun()

    with col_vrac:
        st.subheader("⚡ News en Vrac")
        for item in vrac_items:
            with st.container(border=True):
                st.markdown(f"**{item['title']}**")
                if st.button("↩️ Retirer", key=f"rem_vrac_{item['id']}"):
                    update_status(item["id"], "inbox")
                    st.rerun()

    st.divider()
    dl_trailers = st.checkbox("📥 Télécharger automatiquement les trailers YouTube des Gros Sujets (via yt-dlp)")

    if st.button("🚀 Générer le pack de l'épisode", type="primary", disabled=(not major_items and not vrac_items)):
        with st.spinner("Génération du script, des chapitres et récupération des médias..."):
            out_path = export_hebdo(
                major_items,
                vrac_items,
                download_videos=dl_trailers,
                ai_cfg=ai_cfg if use_ai else None,
            )
        st.success(f"Pack généré avec succès dans : `{out_path}` !")
        st.code((out_path / "script_episode.md").read_text(encoding="utf-8"), language="markdown")

# --- ONGLET 3 : RADAR JEUX GRATUITS & F2P ---
RADAR_SOURCES = {
    "epic": fetch_epic_free,
    "gamerpower": fetch_gamerpower,
    "steam": fetch_steam_f2p,
}


@st.cache_data(ttl=600, show_spinner=False)
def load_radar(source: str):
    """Interroge une source du radar (résultat gardé 10 minutes)."""
    return RADAR_SOURCES[source](), datetime.now()


def format_deadline(item: dict) -> str:
    now = datetime.now().astimezone()
    starts, ends = item["starts"], item["ends"]
    if item["source"] != "Steam" and starts and starts > now:
        return f"📆 du {starts.astimezone():%d/%m} au {ends.astimezone():%d/%m}" if ends else f"📆 dès le {starts.astimezone():%d/%m}"
    if not ends:
        return ""
    left = ends - now
    if left.total_seconds() < 86400:
        return f"⏳ se termine dans {max(int(left.total_seconds() // 3600), 0)} h"
    return f"⏳ jusqu'au {ends.astimezone():%d/%m à %H:%M}"


def render_radar_item(item: dict, section: str, in_hebdo: dict):
    news_id = radar_news_id(item)
    with st.container(border=True):
        col_img, col_txt, col_btn = st.columns([1, 3, 1])
        with col_img:
            if item["image"]:
                st.image(item["image"], width="stretch")
        with col_txt:
            st.markdown(f"**[{item['title']}]({item['url']})**")
            meta = [item["kind"], item["platforms"]]
            if item["worth"]:
                meta.append(f"💰 {item['worth']}")
            meta.append(format_deadline(item))
            if item["players"] is not None:
                meta.append(f"👥 {item['players']:,} joueurs en ligne".replace(",", " "))
            if item["claimed"]:
                meta.append(f"🙋 {item['claimed']:,} récupérés".replace(",", " "))
            if item["released"]:
                meta.append(f"📅 sorti le {item['released']}")
            st.caption(" • ".join(m for m in meta if m))
            if item["summary"]:
                st.write(item["summary"][:220] + ("..." if len(item["summary"]) > 220 else ""))
        with col_btn:
            status = in_hebdo.get(news_id)
            if status:
                st.success("✅ Gros sujet" if status == "major" else "✅ En vrac")
                return
            for label, new_status in (("🔥 Gros Sujet", "major"), ("⚡ En Vrac", "vrac")):
                if st.button(label, key=f"radar_{new_status}_{section}_{news_id}", use_container_width=True):
                    with st.spinner("Ajout à l'émission..."):
                        send_to_hebdo(item, new_status)
                    where = "gros sujets" if new_status == "major" else "news en vrac"
                    st.session_state["radar_flash"] = f"« {item['title']} » ajouté aux {where} de l'émission."
                    st.rerun()


def render_radar_source(source: str, section: str, in_hebdo: dict, empty_msg: str, kinds: list[str] | None = None):
    try:
        with st.spinner("Interrogation en direct..."):
            items, fetched_at = load_radar(source)
    except Exception as e:
        st.error(f"Source indisponible pour le moment : {e}")
        return []
    st.caption(f"Mis à jour à {fetched_at:%H:%M} · {len(items)} résultat(s)")
    if kinds is not None:
        items = [i for i in items if i["kind"] in kinds]
    if not items:
        st.info(empty_msg)
    for item in items:
        render_radar_item(item, section, in_hebdo)
    return items


if tab_radar.open:
    with tab_radar:
        in_hebdo = {n["id"]: "major" for n in major_items} | {n["id"]: "vrac" for n in vrac_items}

        col_info, col_refresh = st.columns([4, 1])
        col_info.caption(
            "Jeux gratuits et Free-to-Play en temps réel. Un clic sur 🔥 Gros Sujet ou ⚡ En Vrac "
            "envoie le jeu dans le conducteur de l'émission (script + trailer)."
        )
        if col_refresh.button("🔄 Actualiser", use_container_width=True):
            load_radar.clear()
            st.rerun()

        if "radar_flash" in st.session_state:
            st.success(st.session_state.pop("radar_flash"))

        sub_epic, sub_gp, sub_steam = st.tabs(
            ["🟢 Epic Games Store", "🎁 GamerPower", "🆓 Steam Free-to-Play"],
            key="radar_tab",
            on_change="rerun",
        )

        if sub_epic.open:
            with sub_epic:
                st.subheader("🟢 Gratuit maintenant")
                render_radar_source("epic", "epic_now", in_hebdo, "Aucun jeu gratuit en ce moment.",
                                    kinds=["🟢 Gratuit maintenant"])
                st.subheader("⏭️ La semaine prochaine")
                render_radar_source("epic", "epic_next", in_hebdo, "Epic n'a pas encore dévoilé les prochains jeux.",
                                    kinds=["⏭️ Gratuit la semaine prochaine"])

        if sub_gp.open:
            with sub_gp:
                st.caption("Jeux, DLC et bêtas offerts sur Steam, GOG, Epic, Itch.io, Ubisoft, EA, Battle.net…")
                kinds = st.pills(
                    "Type d'offre",
                    ["🎮 Jeu offert", "🧩 DLC offert", "🧪 Bêta / Playtest"],
                    selection_mode="multi",
                    default=["🎮 Jeu offert", "🧩 DLC offert", "🧪 Bêta / Playtest"],
                    key="gp_kinds",
                )
                render_radar_source("gamerpower", "gp", in_hebdo, "Aucune offre pour ces filtres.", kinds=kinds)

        if sub_steam.open:
            with sub_steam:
                st.caption("Free-to-Play sortis sur Steam ces 12 derniers mois, classés par joueurs connectés en direct.")
                render_radar_source("steam", "steam", in_hebdo, "Aucun Free-to-Play récent trouvé.")

# --- ONGLET 4 : DÉTECTEUR DE TENDANCES ---
def fmt_int(n: int) -> str:
    return f"{n:,}".replace(",", " ")


def render_trend_item(game: dict, window_hours: int, in_hebdo: dict):
    item = _item(
        title=game["name"], url=game["url"], source="Tendances", kind="📈 Tendance",
        image=game["image"], summary=trend_summary(game, window_hours),
    )
    news_id = radar_news_id(item)
    with st.container(border=True):
        col_img, col_txt, col_btn = st.columns([1, 4, 1])
        with col_img:
            if game["image"]:
                st.image(game["image"], width="stretch")
        with col_txt:
            badge = "🆕 Nouveau venu" if game["score"] is None else f"🚀 {game['score']:+.0f} %"
            st.markdown(f"**[{game['name']}]({game['url']})** · {badge}")
            parts = []
            for source, s in game["sources"].items():
                growth = "nouveau" if s["growth"] is None else f"{s['growth']:+.0f} %"
                parts.append(f"{SOURCE_LABELS[source]} {fmt_int(s['now'])} {UNITS[source]} ({growth})")
            st.caption(" • ".join(parts))
            series = history(game["key"], days=7)
            if any(len(points) > 1 for points in series.values()):
                import pandas as pd
                chart = pd.DataFrame({
                    SOURCE_LABELS[src]: pd.Series({t.astimezone(): v for t, v in points})
                    for src, points in series.items()
                })
                st.line_chart(chart, height=140)
        with col_btn:
            status = in_hebdo.get(news_id)
            if status:
                st.success("✅ Gros sujet" if status == "major" else "✅ En vrac")
                return
            for label, new_status in (("🔥 Gros Sujet", "major"), ("⚡ En Vrac", "vrac")):
                if st.button(label, key=f"trend_{new_status}_{news_id}", use_container_width=True):
                    send_to_hebdo(item, new_status)
                    where = "gros sujets" if new_status == "major" else "news en vrac"
                    st.session_state["trends_flash"] = f"« {game['name']} » ajouté aux {where} de l'émission."
                    st.rerun()


if tab_trends.open:
    with tab_trends:
        in_hebdo = {n["id"]: "major" for n in major_items} | {n["id"]: "vrac" for n in vrac_items}
        active = active_sources()

        # État des sources
        cols = st.columns(3)
        for col, source in zip(cols, ("twitch", "steam", "youtube")):
            times = snapshot_times(source)
            if not active[source]:
                col.warning(f"{SOURCE_LABELS[source]} : clé manquante")
            elif times:
                last = times[-1].astimezone()
                col.success(f"{SOURCE_LABELS[source]} : {len(times)} relevé(s), dernier le {last:%d/%m à %H:%M}")
            else:
                col.info(f"{SOURCE_LABELS[source]} : aucun relevé pour l'instant")

        if not (active["twitch"] and active["youtube"]):
            with st.expander("🔑 Ajouter les clés Twitch / YouTube"):
                st.markdown(
                    "Crée un fichier **`.env`** dans le dossier du projet (il n'est jamais envoyé sur GitHub) :\n"
                    "```\nTWITCH_CLIENT_ID=ton_client_id\nTWITCH_CLIENT_SECRET=ton_secret\n"
                    "YOUTUBE_API_KEY=ta_cle_youtube\n```\n"
                    "Les clés sont prises en compte au prochain relevé. Le guide complet est dans le README."
                )

        col_btn, col_hint = st.columns([1, 3])
        if col_btn.button("📸 Faire un relevé maintenant", use_container_width=True):
            with st.spinner("Relevé en cours (jusqu'à une minute)..."):
                report = collect_all()
            st.session_state["trends_flash"] = "Relevé terminé — " + " | ".join(
                f"{SOURCE_LABELS[s]} : {r} jeux" if isinstance(r, int) else f"{SOURCE_LABELS[s]} : {r}"
                for s, r in report.items()
            )
            st.rerun()
        col_hint.caption("Les relevés automatiques ont lieu toutes les 3 h (tâche planifiée). "
                         "Les tendances comparent au relevé d'il y a 24 h ou 7 jours, à la même heure.")

        if "trends_flash" in st.session_state:
            st.success(st.session_state.pop("trends_flash"))

        col_win, col_src, col_min = st.columns([1, 2, 2])
        period = col_win.segmented_control("Période", ["24 h", "7 jours"], default="24 h", key="trend_period")
        window_hours = 168 if period == "7 jours" else 24
        platform_choices = {"Toutes": ("twitch", "steam", "youtube")} | {
            SOURCE_LABELS[s]: (s,) for s in ("twitch", "youtube", "steam")
        }
        platform = col_src.segmented_control("Plateforme", list(platform_choices), default="Toutes",
                                             key="trend_platform") or "Toutes"
        min_growth = col_min.slider("Hausse minimum", 10, 300, 25, step=5, format="%d %%", key="trend_min")

        trends_list = compute_trends(window_hours, sources=platform_choices[platform])
        if not trends_list:
            st.info(
                f"Pas encore assez d'historique pour la période {period}"
                + ("" if platform == "Toutes" else f" sur {platform}")
                + " : il faut un relevé datant "
                f"d'environ {period}. Le classement apparaîtra automatiquement à ce moment-là."
            )
            # Un top par source : spectateurs, joueurs et vues ne se comparent pas entre eux
            tops = {source: latest_snapshot((source,), limit=10) for source in ("twitch", "steam", "youtube")}
            if any(tops.values()):
                st.subheader("🎯 En attendant : le top du moment, source par source")
                for col, (source, rows) in zip(st.columns(3), tops.items()):
                    with col:
                        st.markdown(f"**{SOURCE_LABELS[source]}** · {UNITS[source]}")
                        if not rows:
                            st.caption("Aucun relevé pour l'instant.")
                        for rank, row in enumerate(rows, start=1):
                            st.markdown(f"{rank}. [{row['game_name']}]({row['url']}) — {fmt_int(row['value'])}")
        else:
            rising = [g for g in trends_list if g["score"] is None or g["score"] >= min_growth][:30]
            st.subheader(f"🚀 Ça monte ({len(rising)})")
            if not rising:
                st.info("Aucun jeu ne dépasse ce seuil de hausse. Baisse le curseur « Hausse minimum » "
                        "ou choisis une autre plateforme.")
            for game in rising:
                render_trend_item(game, window_hours, in_hebdo)

# --- ONGLET 5 : ESPACE X ---
def x_text_key(mode: str, news_id: str) -> str:
    return f"xtxt_{mode}_{news_id}"


def x_set_text(key: str, text: str):
    st.session_state[key] = text


def x_ai_rewrite(key: str, item: dict, prefix: str, with_link: bool):
    try:
        body = ai_tweet_body(item, ai_cfg)
        st.session_state[key] = build_tweet(item, prefix=prefix, body=body, with_link=with_link)
    except Exception as e:  # clé absente, Ollama éteint…
        st.session_state["x_ai_error"] = str(e)


def x_forget_texts():
    """Oublie les textes rédigés : ils seront recalculés (numérotation du thread, lien…)."""
    for key in [k for k in st.session_state if k.startswith("xtxt_")]:
        del st.session_state[key]


def render_tweet(key: str, default: str, item: dict | None, prefix: str, with_link: bool, label: str):
    """Un tweet : aperçu à copier, compteur de caractères, retouche, rédaction IA et ouverture dans X."""
    if key not in st.session_state:
        st.session_state[key] = default
    text = st.session_state[key]
    length = tweet_length(text)

    with st.container(border=True):
        head, count = st.columns([4, 1])
        head.markdown(f"**{label}**")
        count.markdown(f"{'🔴' if length > TWEET_MAX else '🟢'} `{length}/{TWEET_MAX}`")
        # Le bouton 📋 en haut à droite du bloc copie le tweet
        st.code(text, language=None, wrap_lines=True)

        with st.expander("✏️ Retoucher"):
            st.text_area("Texte du tweet", key=key, height=160, label_visibility="collapsed")
            if item is not None:
                st.button("↺ Revenir au texte de base", key=f"reset_{key}",
                          on_click=x_set_text, args=(key, default))
        cols = st.columns(2)
        if item is not None:
            cols[0].button("✍️ Rédiger avec l'IA", key=f"ai_{key}", use_container_width=True, disabled=not ai_ready,
                           help="Rédiger en français avec l'IA" if ai_ready else "Active l'IA dans la barre latérale",
                           on_click=x_ai_rewrite, args=(key, item, prefix, with_link))
        cols[1].link_button("𝕏 Ouvrir dans X", intent_url(text), use_container_width=True, help="Ouvrir dans X, tweet pré-rempli")


with tab_x:
    st.caption(
        "Pioche librement des news (sans toucher à l'émission), puis copie-colle les tweets sur X "
        "avec le bouton 📋 de chaque bloc, ou ouvre-les directement dans X."
    )
    col_pick, col_write = st.columns([2, 3], gap="large")

    with col_pick:
        st.subheader("🧺 Piocher")
        x_search = st.text_input("🔍 Chercher une news (titre, résumé, source)", key="x_search")
        for item in get_all_news(search=x_search, limit=60):
            picked = item["id"] in x_pick_ids
            with st.container(border=True):
                txt, btn = st.columns([5, 1])
                txt.markdown(f"**[{item['title']}]({item['url']})**")
                txt.caption(f"🕒 {format_age(item['published'])} • {item['source']}")
                if btn.button("", icon=":material/check:" if picked else ":material/add:", key=f"xpick_{item['id']}",
                              help="Retirer de la sélection" if picked else "Ajouter à la sélection"):
                    (remove_x_pick if picked else add_x_pick)(item["id"])
                    x_forget_texts()
                    st.rerun()

    with col_write:
        st.subheader(f"✍️ Ma sélection ({len(x_picks)})")
        if not x_picks:
            st.info("Ta sélection est vide : ajoute des news avec ➕ (ou 🐦 Pour X dans la boîte de réception).")
        else:
            x_mode = st.segmented_control(
                "Format", ["🐦 Tweets séparés", "🧵 Thread"], default="🐦 Tweets séparés",
                key="x_mode", on_change=x_forget_texts,
            ) or "🐦 Tweets séparés"
            opt_link, opt_clear = st.columns([3, 1])
            with_link = opt_link.toggle("🔗 Ajouter le lien de la source", value=True, key="x_link",
                                    on_change=x_forget_texts)
            if opt_clear.button("🧹 Vider", use_container_width=True):
                clear_x_picks()
                x_forget_texts()
                st.rerun()

            if not ai_ready:
                st.caption("💡 Pour des tweets rédigés en français, active l'IA dans la barre latérale.")
            if err := st.session_state.pop("x_ai_error", None):
                st.error(f"L'IA n'a pas répondu : {err}")

            # Ordre de la sélection (= ordre du thread)
            with st.expander("↕️ Réordonner la sélection"):
                for i, item in enumerate(x_picks):
                    c_title, c_up, c_down, c_del = st.columns([6, 1, 1, 1])
                    c_title.markdown(f"{i + 1}. {item['title']}")
                    for col, sym, offset, ok in ((c_up, "⬆️", -1, i > 0), (c_down, "⬇️", 1, i < len(x_picks) - 1)):
                        if col.button(sym, key=f"xmove{offset}_{item['id']}", disabled=not ok):
                            move_x_pick(item["id"], offset)
                            x_forget_texts()
                            st.rerun()
                    if c_del.button("✖️", key=f"xdel_{item['id']}", help="Retirer"):
                        remove_x_pick(item["id"])
                        x_forget_texts()
                        st.rerun()

            if x_mode == "🧵 Thread":
                thread = build_thread(x_picks, with_link=with_link)
                total = len(thread)
                st.caption(
                    "Poste le 1er tweet, puis réponds-lui avec les suivants dans l'ordre "
                    "(ou utilise « + » dans la fenêtre de rédaction de X)."
                )
                render_tweet(x_text_key("thread", "intro"), thread[0], None, "", with_link, f"1/{total} · Intro")
                for pos, (item, default) in enumerate(zip(x_picks, thread[1:]), start=2):
                    prefix = f"{pos}/{total} "
                    if item.get("x_tweet"):  # tweet rédigé par Claude via le serveur MCP
                        default = build_tweet(item, prefix=prefix, body=item["x_tweet"], with_link=with_link)
                    render_tweet(x_text_key("thread", item["id"]), default, item, prefix,
                                 with_link, f"{pos}/{total} · {item['source']}")
            else:
                for item in x_picks:
                    # Tweet rédigé par Claude via le serveur MCP, sinon texte automatique
                    default = build_tweet(item, body=item.get("x_tweet") or None, with_link=with_link)
                    label = item["source"] + (" · ✍️ rédigé par l'IA" if item.get("x_tweet") else "")
                    render_tweet(x_text_key("single", item["id"]), default, item, "", with_link, label)
