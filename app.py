import streamlit as st

from db import clear_inbox, get_news, init_db, update_status
from engine import export_hebdo, fetch_all_feeds

st.set_page_config(page_title="Le Dashupeur", page_icon="🎮", layout="wide")
init_db()

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

tab_inbox, tab_hebdo = st.tabs([
    f"📥 Boîte de réception ({len(inbox_items)})",
    f"🎬 Préparer l'Hebdo ({len(major_items)} Gros Sujets | {len(vrac_items)} Vrac)"
])

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
                st.caption(f"{item['source']} • {badge} • Tags: `{item['tags'] or 'aucun'}`")
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
