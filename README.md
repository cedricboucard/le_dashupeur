<p align="center">
  <img src="assets/icon.png" alt="Icône du Dashupeur" width="160">
</p>

# 🎮 Le Dashupeur

**Régie d'actualité gaming** : une petite application web qui fait la veille de l'actu jeu vidéo à ta place, t'aide à trier les news et prépare tout le nécessaire pour enregistrer une émission hebdomadaire (« L'Hebdo ») sur YouTube.

Le Dashupeur récupère les flux RSS de plusieurs sites gaming, donne un score de pertinence à chaque news selon des mots-clés, puis te laisse choisir les gros sujets et les news en vrac. En un clic, il génère :

- 📝 le **script** de l'émission (en Markdown) ;
- 📺 la **description YouTube** avec les chapitres et les liens des sources ;
- 🎞️ les **trailers YouTube** des gros sujets (optionnel, via yt-dlp) ;
- 🤖 des **résumés traduits en français** par IA (optionnel, via OpenAI ou Ollama en local).

Tout reste sur ton ordinateur : les news sont stockées dans une base SQLite locale.

---

## ✨ Fonctionnalités

| | |
|---|---|
| 📡 **Veille RSS** | Scan de 6 sources (MassivelyOP, MMORPG.com, ActuGaming, PC Gamer, Rock Paper Shotgun, Reddit r/MMORPG). Les doublons sont ignorés automatiquement. |
| 🎯 **Score de pertinence** | Chaque news gagne des points selon les mots-clés trouvés (MMORPG, free-to-play, bêta, shutdown…). Les news à 15 points ou plus sont marquées 🔥. |
| 📥 **Boîte de réception** | Filtre par score minimum ou par mot-clé, puis classe chaque news en **🔥 Gros Sujet**, **⚡ En Vrac** ou **🗑️ Ignorer**. |
| 🎬 **Pack de L'Hebdo** | Génère un dossier daté avec le script, la description YouTube (chapitres + sources) et un sous-dossier par gros sujet. |
| 🎞️ **Trailers** | Télécharge le premier trailer officiel trouvé sur YouTube pour chaque gros sujet (jusqu'en 1080p). |
| 🤖 **Résumés IA** | Traduit et résume chaque gros sujet en 3 points percutants, prêts pour le script vidéo. |

---

## 🚀 Installation

### Prérequis

- **Python 3.10 ou plus récent**
- **ffmpeg** (seulement pour télécharger les trailers : il assemble l'image et le son)

```bash
sudo apt install ffmpeg
```

### Étapes

```bash
git clone https://github.com/cedricboucard/le_dashupeur.git
cd le_dashupeur
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

---

## ▶️ Lancement

```bash
cd le_dashupeur
source venv/bin/activate
streamlit run app.py
```

L'application s'ouvre dans ton navigateur sur **http://localhost:8501**.

---

## 🧭 Utilisation

1. **Scanner** : dans la barre latérale, clique sur **🔄 Scanner l'actualité**. Le nombre de nouvelles news s'affiche, ainsi que les flux en erreur s'il y en a.
2. **Trier** : dans l'onglet **📥 Boîte de réception**, classe chaque news. Le curseur « Score de pertinence minimum » et la recherche par mot-clé t'aident à aller à l'essentiel.
3. **Vérifier** : dans l'onglet **🎬 Préparer l'Hebdo**, relis ta sélection. Le bouton **↩️ Retirer** renvoie une news dans la boîte de réception.
4. **Générer** : coche si besoin le téléchargement des trailers, puis clique sur **🚀 Générer le pack de L'Hebdo**.
5. **Faire le ménage** : **🧹 Vider la boîte de réception** supprime les news non triées et ignorées. Tes gros sujets et news en vrac sont conservés.

### Ce que contient un pack

```
exports/
└── Hebdo_2026-11-16_20-30/
    ├── script_hebdo.md            # Script de l'émission
    ├── description_youtube.txt    # Chapitres + liens des sources
    ├── 01_Titre_du_sujet/         # Un dossier par gros sujet
    │   └── Trailer officiel.mp4   # (si le téléchargement est activé)
    └── 02_.../
```

> Les chapitres sont espacés de 3 minutes par défaut (`03:00`, `06:00`…). Ajuste-les dans la description après le montage.

---

## 🤖 Résumés par IA (optionnel)

Sans IA, l'application fonctionne normalement : le script reprend simplement le résumé fourni par le flux RSS.

Pour activer l'IA, coche **« Résumer & traduire les gros sujets via IA »** dans la barre latérale, puis choisis :

- **OpenAI** : colle ta clé API (le modèle par défaut est `gpt-4o-mini`). La clé n'est ni enregistrée ni envoyée ailleurs qu'à OpenAI.
- **Ollama (local, gratuit)** : installe [Ollama](https://ollama.com), télécharge un modèle, puis laisse l'URL par défaut `http://localhost:11434/v1`.

```bash
ollama pull llama3
```

---

## ⚙️ Personnalisation

Tout se règle dans [`config.py`](config.py) :

- **`RSS_FEEDS`** : ajoute ou retire des sources (`"Nom affiché": "URL du flux"`).
- **`KEYWORDS_WEIGHTS`** : ajoute des mots-clés et leur poids. Plus le poids est élevé, plus une news qui contient ce mot remonte.

```python
KEYWORDS_WEIGHTS = {
    "mmorpg": 15,
    "mon jeu préféré": 20,   # ← exemple d'ajout
    ...
}
```

> Le score est calculé au moment du scan : les news déjà en base gardent leur ancien score.

---

## 🗂️ Structure du projet

```
le_dashupeur/
├── app.py        # Interface web Streamlit
├── engine.py     # Scan RSS, score, résumés IA, trailers et export
├── db.py         # Base SQLite locale (dashupeur.db)
├── config.py     # Flux RSS, mots-clés et pondération
├── requirements.txt  # Dépendances Python
└── assets/
    ├── icon.png       # Icône du projet (512 px)
    ├── favicon.png    # Icône de l'onglet du navigateur (64 px)
    └── make_icon.py   # Script qui redessine l'icône (Pillow)
```

Créés automatiquement au premier lancement, et ignorés par git :

- `dashupeur.db` : la base des news ;
- `exports/` : les packs de L'Hebdo.

---

## 💡 Bon à savoir

- **Reddit limite les requêtes** : si tu scannes plusieurs fois d'affilée, Reddit peut répondre « 429 Too Many Requests ». Attends quelques minutes ; les autres sources sont récupérées quand même.
- **Ne mets jamais ta clé API dans le code** : ce dépôt est public. Saisis-la dans l'application.
- **Le premier trailer trouvé n'est pas toujours le bon** : vérifie les vidéos avant de les utiliser au montage.

---

## 🛠️ Technologies

[Streamlit](https://streamlit.io) · [feedparser](https://github.com/kurtmckee/feedparser) · [Beautiful Soup](https://www.crummy.com/software/BeautifulSoup/) · [Requests](https://requests.readthedocs.io) · [yt-dlp](https://github.com/yt-dlp/yt-dlp) · [OpenAI Python](https://github.com/openai/openai-python) · SQLite
