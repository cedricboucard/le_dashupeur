from datetime import datetime
from pathlib import Path

import streamlit as st

from db import clear_inbox, get_news, init_db, update_status
from engine import export_hebdo, fetch_all_feeds, parse_published
from radar import _item, fetch_epic_free, fetch_gamerpower, fetch_steam_f2p, radar_news_id, send_to_hebdo
from trends import (SOURCE_LABELS, UNITS, active_sources, collect_all, compute_trends, history,
                    latest_snapshot, snapshot_times, trend_summary)

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
st.caption("Veille automatisée, tri de l'Hebdo, scripts et récupération de trailers.")

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
    use_ai = st.checkbox("Résumer & traduire les gros sujets via IA")
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

# --- ONGLETS PRINCIPAUX ---
inbox_items = get_news(status="inbox", min_score=min_score)
major_items = get_news(status="major")
vrac_items = get_news(status="vrac")

st.markdown(
    f"📥 **{len(inbox_items)}** à trier · 🔥 **{len(major_items)}** gros sujet(s) · "
    f"⚡ **{len(vrac_items)}** en vrac"
)

# Libellés fixes + key : l'onglet ouvert reste le même après un clic
tab_inbox, tab_hebdo, tab_radar, tab_trends = st.tabs(
    ["📥 Boîte de réception", "🎬 Préparer l'Hebdo", "🎁 Radar Jeux Gratuits & F2P", "📈 Tendances"],
    key="main_tab",
    on_change="rerun",
)

# --- ONGLET 1 : TRI DES NEWS ---
with tab_inbox:
    search = st.text_input("🔍 Filtrer par mot-clé (ex: MMO, Steam, gratuit...)")
    filtered = [
        n for n in inbox_items
        if not search or search.lower() in (n["title"] + n["summary"] + n["tags"]).lower()
    ]

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

# --- ONGLET 2 : EXPORT DE L'HEBDO ---
with tab_hebdo:
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

    if st.button("🚀 Générer le pack de L'Hebdo", type="primary", disabled=(not major_items and not vrac_items)):
        with st.spinner("Génération du script, des chapitres et récupération des médias..."):
            out_path = export_hebdo(
                major_items,
                vrac_items,
                download_videos=dl_trailers,
                ai_cfg=ai_cfg if use_ai else None,
            )
        st.success(f"Pack généré avec succès dans : `{out_path}` !")
        st.code((out_path / "script_hebdo.md").read_text(encoding="utf-8"), language="markdown")

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
                    with st.spinner("Ajout à L'Hebdo..."):
                        send_to_hebdo(item, new_status)
                    where = "gros sujets" if new_status == "major" else "news en vrac"
                    st.session_state["radar_flash"] = f"« {item['title']} » ajouté aux {where} de L'Hebdo."
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
            "envoie le jeu dans le conducteur de L'Hebdo (script + trailer)."
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
                    st.session_state["trends_flash"] = f"« {game['name']} » ajouté aux {where} de L'Hebdo."
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

        col_win, col_min = st.columns(2)
        period = col_win.segmented_control("Période", ["24 h", "7 jours"], default="24 h", key="trend_period")
        window_hours = 168 if period == "7 jours" else 24
        min_growth = col_min.slider("Hausse minimum", 10, 300, 25, step=5, format="%d %%", key="trend_min")

        trends_list = compute_trends(window_hours)
        if not trends_list:
            st.info(
                f"Pas encore assez d'historique pour la période {period} : il faut un relevé datant "
                f"d'environ {period}. Le classement apparaîtra automatiquement à ce moment-là."
            )
            top = latest_snapshot(("twitch", "steam"), limit=15)
            if top:
                st.subheader("🎯 En attendant : les plus joués et regardés en ce moment")
                for row in top:
                    st.markdown(f"- **[{row['game_name']}]({row['url']})** — {fmt_int(row['value'])} "
                                f"{UNITS[row['source']]} ({SOURCE_LABELS[row['source']]})")
        else:
            rising = [g for g in trends_list if g["score"] is None or g["score"] >= min_growth][:30]
            st.subheader(f"🚀 Ça monte ({len(rising)})")
            if not rising:
                st.info("Aucun jeu ne dépasse ce seuil de hausse. Baisse le curseur « Hausse minimum ».")
            for game in rising:
                render_trend_item(game, window_hours, in_hebdo)
