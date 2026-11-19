<p align="center">
  <img src="assets/icon.png" alt="Icône du Dashupeur" width="160">
</p>

# 🎮 Le Dashupeur

**Régie d'actualité gaming** : une petite application web qui fait la veille de l'actu jeu vidéo à ta place, t'aide à trier les news et prépare tout le nécessaire pour enregistrer une émission hebdomadaire sur YouTube : **🧅 On épluche l'actu**, sur la chaîne Onion Burger.

Le Dashupeur récupère les flux RSS de plusieurs sites gaming, donne un score de pertinence à chaque news selon des mots-clés, puis te laisse choisir les gros sujets et les épluchures (les news plus courtes). En un clic, il génère :

- 📝 le **script** de l'émission (en Markdown) ;
- 📺 la **description YouTube** avec les chapitres et les liens des sources ;
- 🎞️ les **trailers YouTube** des gros sujets (optionnel, via yt-dlp) ;
- 🤖 des **résumés traduits en français** par IA (optionnel, via OpenAI ou Ollama en local).

Il intègre aussi un **🎁 Radar Jeux Gratuits & F2P**, connecté en temps réel à l'Epic Games Store, GamerPower et Steam : un clic suffit pour envoyer un jeu offert ou un nouveau Free-to-Play dans le conducteur de l'émission.

Et un **📈 Détecteur de tendances** qui suit Twitch, Steam et YouTube toutes les 3 heures pour repérer les jeux qui explosent avant tout le monde.

Enfin, l'**🐦 Espace X** transforme les news de ton choix en tweets séparés ou en thread, prêts à copier-coller sur X.

Tout reste sur ton ordinateur : les news sont stockées dans une base SQLite locale.

---

## ✨ Fonctionnalités

| | |
|---|---|
| 📡 **Veille RSS** | Scan de 27 sources : presse MMO et jeu vidéo (MassivelyOP, MMORPG.com, Jeuxvideo.com, Gamekult, ActuGaming, PC Gamer, PCGamesN, Rock Paper Shotgun, Gematsu), **recherches Google News** (versions PC, lancements mondiaux et préinscriptions, bêtas MMO et gacha, playtests et accès anticipé, fermetures, Com2uS, MMO et gacha en français), **chaînes YouTube** de trailers et de jeux (IGN, GameSpot, GameTrailers, PlayStation, Xbox, chaînes de trailers indés, The First Descendant) et **actualités Steam** des jeux suivis (annonces, playtests, patch notes : Under a Rock, The First Descendant, ARES: The Iron Vanguard). Le scan ajoute aussi les **nouvelles sorties Steam** des 3 derniers jours : les nouveautés populaires mises en avant par Steam et les nouveaux free-to-play qui ont au moins 50 joueurs connectés. Avec un `STEAM_ID` dans `.env`, il suit aussi ta **liste de souhaits Steam** : les annonces des 7 derniers jours de chaque jeu (playtests, dates de sortie, week-ends gratuits) arrivent dans la boîte de réception avec un bonus de pertinence. Les doublons sont ignorés automatiquement. Des liens vers **GlobeNewswire** (communiqués « video game » et « MMORPG ») permettent une veille manuelle, tout comme des liens vers les **MMO asiatiques** (chaîne YouTube IceXgame, Inven Global, MMO Culture) pour suivre les sorties coréennes ou chinoises pas encore annoncées chez nous. |
| 🎯 **Score de pertinence** | Chaque news gagne des points selon les mots-clés trouvés (MMORPG, free-to-play, bêta, shutdown…). Les news à 15 points ou plus sont marquées 🔥. |
| 📥 **Boîte de réception** | Filtre par score minimum ou par mot-clé, puis classe chaque news en **🔥 Gros Sujet**, **⚡ Épluchure** ou **🗑️ Ignorer**. |
| 🤖 **Proposition automatique** | Un bouton choisit 10 gros sujets et 8 épluchures (par l'IA, ou au score sans IA), sans doublons, à relire et valider. |
| 🎬 **Pack de l'épisode** | Génère un dossier daté avec le script, la description YouTube (chapitres + sources) et un sous-dossier par gros sujet. |
| 🎞️ **Trailers** | Télécharge le premier trailer officiel trouvé sur YouTube pour chaque gros sujet (jusqu'en 1080p). |
| 🤖 **Résumés IA** | Traduit et résume chaque gros sujet en 3 points percutants, prêts pour le script vidéo. |
| 🎬 **Bandeaux (Lower Thirds)** | Dans l'onglet de l'émission : un bandeau PNG transparent par news de l'épisode (titre, sous-titre et étiquette « Gros sujet » / « Épluchure », modifiables dans un tableau), en 1080p ou 4K, en 3 styles. Aperçu sur fond de test, puis téléchargement d'un bandeau ou de tous en ZIP, prêts à poser sur la timeline. Police Lato fournie (licence OFL). |
| 🎁 **Radar Jeux Gratuits & F2P** | En temps réel : jeux gratuits de l'**Epic Games Store** (maintenant + semaine prochaine), jeux/DLC/bêtas offerts via **GamerPower** (Steam, GOG, Epic, Itch.io…) et nouveaux **Free-to-Play Steam** classés par joueurs connectés en direct. |
| 🔗 **Pont vers l'émission** | Depuis le Radar, **🔥 Gros Sujet** ou **⚡ Épluchure** envoie le jeu dans le conducteur, avec son résumé (valeur, date de fin de l'offre, joueurs en ligne) pour le script et le trailer. |
| 🐦 **Espace X** | Pioche librement des news (sans toucher à l'émission) et obtiens des **tweets séparés** ou un **thread** numéroté (1/5, 2/5…) avec lien de la source. Compteur de caractères calculé comme X (lien = 23, emoji = 2), retouche du texte, rédaction en français par IA (optionnel), bouton 📋 pour copier et **𝕏 Ouvrir dans X** avec le tweet pré-rempli. |
| 📈 **Détecteur de tendances** | Relevé automatique toutes les 3 h des spectateurs **Twitch**, des joueurs **Steam** et des vues **YouTube** de chaque jeu. Classement « 🚀 Ça monte » par croissance sur 24 h ou 7 jours, toutes plateformes confondues ou **une seule** (Twitch, YouTube ou Steam), repérage des 🆕 nouveaux venus, mini-courbes, et envoi direct vers l'émission. |

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
2. **Trier** : dans l'onglet **📥 Boîte de réception**, classe chaque news. Chaque news affiche sa date de publication (« aujourd'hui », « il y a 3 jours »…). Le curseur « Score de pertinence minimum » et la recherche par mot-clé t'aident à aller à l'essentiel.
3. **Piocher dans le Radar** (optionnel) : dans l'onglet **🎁 Radar Jeux Gratuits & F2P**, parcours les sous-onglets Epic Games Store, GamerPower et Steam Free-to-Play, puis envoie les jeux qui t'intéressent dans l'émission. Les données sont gardées 10 minutes ; **🔄 Actualiser** force une nouvelle interrogation.
   Dans l'onglet **📈 Tendances**, repère les jeux qui décollent et envoie-les aussi dans l'émission.
4. **Vérifier** : dans l'onglet **🧅 On épluche l'actu**, relis ta sélection. Pour aller plus vite, **🤖 Proposer l'épisode** choisit à ta place 10 gros sujets et 8 épluchures (voir ci-dessous). Le bouton **↩️ Retirer** renvoie une news dans la boîte de réception.
5. **Générer** : coche si besoin le téléchargement des trailers, puis clique sur **🚀 Générer le pack de l'épisode**.
6. **Faire le ménage** : **🧹 Vider la boîte de réception** supprime les news non triées et ignorées. Tes gros sujets, épluchures et news piochées pour X sont conservés.

### 🤖 Proposition automatique de l'épisode

Dans l'onglet **🧅 On épluche l'actu**, le bouton **🤖 Proposer l'épisode** prépare une sélection complète à partir de ta boîte de réception :

- il garde les news de la semaine, retire les doublons (la même histoire racontée par plusieurs sites) et présélectionne les 80 plus prometteuses (score + fraîcheur) ;
- **avec l'IA activée** dans la barre latérale, c'est elle qui choisit **10 gros sujets** et **8 épluchures**, avec une courte raison pour chacun. Si elle en rend moins, la liste est complétée au score ;
- **sans IA**, le choix se fait au score et à la fraîcheur, avec au plus 3 gros sujets par source.

Tout arrive coché : décoche ce que tu ne veux pas, puis **✅ Valider la proposition**. Rien n'est déplacé avant ta validation.

> Avec Ollama, un modèle de 8B (comme `llama3` ou `dolphin3`) peut mettre plusieurs minutes sur un PC sans carte graphique, et demande environ 6 Go de mémoire libre.

### 🐦 Publier sur X

1. Choisis tes news : **➕** dans l'onglet **🐦 Espace X** (recherche sur toutes les news, quel que soit leur statut) ou **🐦 Pour X** dans la boîte de réception. La sélection est indépendante de l'émission et reste enregistrée.
2. Choisis le format : **🐦 Tweets séparés** (un tweet par news) ou **🧵 Thread** (un tweet d'intro puis un tweet par news, dans l'ordre de **↕️ Réordonner la sélection**).
3. Ajuste si besoin : **✏️ Retoucher** pour modifier le texte, **✍️ Rédiger avec l'IA** pour un tweet en français (IA à activer dans la barre latérale). Le compteur passe au 🔴 au-delà de 280 caractères.
4. Publie : le bouton **📋** en haut à droite de chaque tweet le copie, à coller dans X. **𝕏 Ouvrir dans X** ouvre directement la fenêtre de rédaction avec le tweet pré-rempli. Pour un thread, poste le 1er tweet puis ajoute les suivants avec **+**.

### Ce que contient un pack

```
exports/
└── OnEpluche_2026-11-16_20-30/
    ├── script_episode.md          # Script de l'émission
    ├── description_youtube.txt    # Chapitres + liens des sources
    ├── 01_Titre_du_sujet/         # Un dossier par gros sujet
    │   └── Trailer officiel.mp4   # (si le téléchargement est activé)
    └── 02_.../
```

> Les chapitres sont espacés de 3 minutes par défaut (`03:00`, `06:00`…). Ajuste-les dans la description après le montage.

---

## 📈 Détecteur de tendances

### Comment ça marche

Toutes les 3 heures, une tâche planifiée relève pour chaque jeu :

| Source | Mesure | Clé |
|---|---|---|
| 🎮 **Steam** | Joueurs connectés en direct (top 100 + nouveautés Free-to-Play populaires) | Aucune |
| 🟣 **Twitch** | Spectateurs cumulés des 100 plus gros lives du jeu (200 catégories les plus regardées) | Gratuite |
| 🔴 **YouTube** | Vues des vidéos du jeu publiées ces 7 derniers jours (40 jeux, 1 relevé par jour) | Gratuite |

L'onglet **📈 Tendances** compare le dernier relevé à celui d'**il y a 24 h ou 7 jours, à la même heure** (pour ne pas confondre une vraie hausse avec le pic du soir). Les petits chiffres sont ignorés (passer de 2 à 10 spectateurs ne veut rien dire).

> Le classement apparaît **24 h après le premier relevé** (7 jours pour la vue hebdomadaire). En attendant, l'onglet affiche les jeux les plus joués et regardés du moment.

Sans clé, le détecteur fonctionne déjà avec Steam. Twitch et YouTube s'ajoutent dès que leurs clés sont dans le fichier `.env`.

### Obtenir les clés (gratuites)

Copie d'abord le modèle :

```bash
cp .env.example .env
```

**🟣 Twitch**

1. Active la double authentification sur ton compte Twitch (obligatoire pour les développeurs).
2. Va sur [dev.twitch.tv/console](https://dev.twitch.tv/console) → **Register Your Application**.
3. Nom : au choix (ex. `Le Dashupeur`), URL de redirection OAuth : `http://localhost`, catégorie : **Analytics Tool**, type de client : **Confidentiel**.
4. Ouvre l'application créée (**Manage**) : copie le **Client ID**, puis clique sur **New Secret** et copie le secret.
5. Colle-les dans `.env` (`TWITCH_CLIENT_ID=` et `TWITCH_CLIENT_SECRET=`).

**🔴 YouTube**

1. Va sur [console.cloud.google.com](https://console.cloud.google.com) et crée un projet (ex. `le-dashupeur`).
2. **API et services → Bibliothèque** : cherche **YouTube Data API v3** et clique sur **Activer**.
3. **API et services → Identifiants → Créer des identifiants → Clé API**.
4. Conseillé : modifie la clé et restreins-la à « YouTube Data API v3 ».
5. Colle-la dans `.env` (`YOUTUBE_API_KEY=`).

> Le quota gratuit de YouTube est de 10 000 unités par jour ; le détecteur en utilise environ 4 000.

### Liste de souhaits Steam (optionnel)

1. Sur Steam : **Profil → Modifier le profil → Paramètres de confidentialité** : mets « Détails du jeu » sur **Public** (la liste de souhaits en dépend).
2. Ton SteamID64 (17 chiffres, commence par `7656119`) est dans l'adresse de ton profil `steamcommunity.com/profiles/<id>`. Si ton profil a une adresse personnalisée, un site comme steamid.io le retrouve.
3. Ajoute-le dans `.env` : `STEAM_ID=7656119…`. Aucune clé API n'est nécessaire.

### La tâche planifiée

Le relevé est lancé par `cron` toutes les 3 heures :

```bash
crontab -e
```

```
0 */3 * * * cd /home/cedric/le_dashupeur && venv/bin/python collect_trends.py >> logs/trends.log 2>&1
```

Adapte le chemin si le projet est ailleurs. Le journal des relevés est dans `logs/trends.log`. Tu peux aussi lancer un relevé à la main avec le bouton **📸 Faire un relevé maintenant**.

---

## 🔌 Serveur MCP : ton assistant IA pilote Le Dashupeur

Plutôt que de payer une clé API, utilise **ton abonnement Claude** (ou Gemini) : le serveur MCP donne à l'assistant des outils pour lire tes news et préparer l'épisode à ta place. Tu lui dis « Prépare-moi l'épisode de la semaine », il choisit les sujets, te les montre, puis les range dans ta base. Tu n'as plus qu'à vérifier dans l'app.

| Outil | Ce qu'il fait |
|---|---|
| `scanner_actualite` | Récupère les dernières news de tous les flux |
| `lister_news` | Liste les news (par statut, période, mot-clé, score) |
| `lire_news` | Détail complet d'une news |
| `etat_hebdo` | Gros sujets, épluchures et Espace X déjà prévus |
| `classer_news` | Range des news en gros sujet, épluchure, à trier ou ignorée |
| `gerer_espace_x` | Ajoute ou retire des news de l'Espace X |
| `rediger_tweet` | Enregistre un tweet rédigé par l'IA (affiché « ✍️ rédigé par l'IA » dans l'Espace X) |
| `generer_pack_hebdo` | Génère le pack de l'épisode (sans les trailers) |

Deux consignes toutes prêtes sont fournies : **Préparer On épluche l'actu** et **Tweets de la semaine**. L'assistant te montre toujours sa sélection avant de l'appliquer. Rien n'est jamais supprimé : tout reste modifiable dans l'app.

### Installer le serveur

Dans les commandes ci-dessous, remplace `/chemin/vers/le_dashupeur` par le dossier du projet.

**Claude Code** :

```bash
claude mcp add --scope user le-dashupeur -- /chemin/vers/le_dashupeur/venv/bin/python /chemin/vers/le_dashupeur/mcp_server.py
```

**Claude Desktop** : ajoute ce bloc dans `claude_desktop_config.json` (Paramètres → Développeur → Modifier la configuration), puis redémarre Claude :

```json
{
  "mcpServers": {
    "le-dashupeur": {
      "command": "/chemin/vers/le_dashupeur/venv/bin/python",
      "args": ["/chemin/vers/le_dashupeur/mcp_server.py"]
    }
  }
}
```

**Gemini CLI** : le même bloc `mcpServers` va dans `~/.gemini/settings.json`.

> L'app web et le serveur MCP partagent la même base : après une action de l'assistant, recharge la page de l'app pour voir le résultat.

---

## 📱 Application Android

Le Dashupeur existe aussi en application Android native (Kotlin + Jetpack Compose), avec les mêmes onglets : **Actus**, **Radar F2P**, **Tendances**, **Émission** et **Espace X**. Sur mobile, l'Espace X copie le tweet ou l'ouvre directement dans l'app X ; la rédaction par IA n'existe que dans la version web.

L'app Android peut aussi t'**alerter des sorties Steam** : touche la cloche 🔔 en haut de l'écran. Toutes les 3 heures, elle cherche les nouvelles sorties (nouveautés populaires de Steam et free-to-play à 50 joueurs connectés ou plus), les ajoute à la boîte de réception et envoie une notification pour chacune (un récapitulatif au-delà de 3). Android peut retarder la vérification quand le téléphone économise la batterie.

Pour suivre ta **liste de souhaits Steam** sur mobile, touche la clé 🔑 et renseigne ton SteamID64 (17 chiffres, liste publique) : chaque scan ajoute alors les annonces des 7 derniers jours des 200 jeux ajoutés le plus récemment.

### Installer l'APK

1. Depuis ton téléphone, ouvre la [dernière release](https://github.com/cedricboucard/le_dashupeur/releases/latest) et télécharge **LeDashupeur-vX.Y.apk**.
2. Ouvre le fichier et autorise l'installation depuis cette source quand Android le demande.
3. Lance **Le Dashupeur**. Les clés Twitch et YouTube se saisissent dans l'app (icône 🔑) et restent sur le téléphone.

Android 8.0 ou plus récent requis.

### Construire l'APK soi-même

Prérequis : JDK 17 et le SDK Android (installé avec Android Studio).

```bash
cd android
./gradlew assembleDebug
```

L'APK est généré dans `android/app/build/outputs/apk/debug/app-debug.apk`.

> Optionnel : copie ta base `dashupeur.db` dans `android/app/src/main/assets/` pour que l'app démarre avec tes news. Sans elle, l'app démarre avec une base vide.

---

## 🤖 Résumés par IA (optionnel)

Sans IA, l'application fonctionne normalement : le script reprend simplement le résumé fourni par le flux RSS, et les tweets reprennent le titre et la 1re phrase de la news.

Pour activer l'IA, coche **« Activer l'IA (résumés de l'émission, tweets en français) »** dans la barre latérale, puis choisis :

- **OpenAI** : colle ta clé API (le modèle par défaut est `gpt-4o-mini`). La clé n'est ni enregistrée ni envoyée ailleurs qu'à OpenAI.
- **Ollama (local, gratuit)** : installe [Ollama](https://ollama.com), télécharge un modèle, puis laisse l'URL par défaut `http://localhost:11434/v1`.

```bash
ollama pull llama3
```

---

## ⚙️ Personnalisation

Tout se règle dans [`config.py`](config.py) :

- **`RSS_FEEDS`** : ajoute ou retire des sources (`"Nom affiché": "URL du flux"`).
- **Suivre une chaîne YouTube** (trailers officiels d'un jeu, chaîne d'éditeur…) : ajoute une ligne avec l'identifiant de la chaîne (il commence par `UC`, visible dans l'adresse de la chaîne ou via « Partager la chaîne → Copier l'identifiant ») :

  ```python
  "YouTube · Nom du jeu": YOUTUBE_FEED.format("UCxxxxxxxxxxxxxxxxxxxxxx"),
  ```

  Gratuit et sans clé, mais le flux d'une chaîne ne contient que ses 15 dernières vidéos : scanne au moins une fois par jour.
- **Suivre un jeu sur Steam** (annonces, playtests, patch notes publiés par les développeurs) : ajoute une ligne avec l'identifiant Steam du jeu, le nombre dans l'adresse `store.steampowered.com/app/<id>/` :

  ```python
  "Steam · Nom du jeu": STEAM_NEWS_FEED.format(1667040),
  ```

  Gratuit et sans clé. Pense à faire la même chose dans `android/…/data/Config.kt` pour l'app mobile.
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
├── radar.py      # Radar Jeux Gratuits & F2P (Epic, GamerPower, Steam)
├── lowerthirds.py # Bandeaux (Lower Thirds) en PNG transparent
├── wishlist.py   # Suivi de la liste de souhaits Steam
├── trends.py     # Détecteur de tendances (relevés + calcul)
├── xposts.py     # Espace X : tweets, threads et compteur de caractères
├── curator.py    # Proposition automatique de l'épisode (IA ou score)
├── mcp_server.py # Serveur MCP : Claude / Gemini pilotent l'app
├── collect_trends.py  # Relevé lancé par la tâche planifiée
├── .env.example  # Modèle pour les clés Twitch / YouTube
├── db.py         # Base SQLite locale (dashupeur.db)
├── config.py     # Flux RSS, mots-clés et pondération
├── requirements.txt  # Dépendances Python
├── LICENSE       # Licence MIT
├── android/      # Application Android (Kotlin + Jetpack Compose)
└── assets/
    ├── icon.png       # Icône du projet (512 px)
    ├── favicon.png    # Icône de l'onglet du navigateur (64 px)
    └── make_icon.py   # Script qui redessine l'icône (Pillow)
```

Créés automatiquement au premier lancement, et ignorés par git :

- `dashupeur.db` : la base des news ;
- `exports/` : les packs des épisodes ;
- `logs/` : le journal des relevés de tendances ;
- `.env` : tes clés API.

---

## 💡 Bon à savoir

- **Ne mets jamais ta clé API dans le code** : ce dépôt est public. Saisis-la dans l'application.
- **Le premier trailer trouvé n'est pas toujours le bon** : vérifie les vidéos avant de les utiliser au montage.

---

## 🛠️ Technologies

[Streamlit](https://streamlit.io) · [feedparser](https://github.com/kurtmckee/feedparser) · [Beautiful Soup](https://www.crummy.com/software/BeautifulSoup/) · [Requests](https://requests.readthedocs.io) · [yt-dlp](https://github.com/yt-dlp/yt-dlp) · [OpenAI Python](https://github.com/openai/openai-python) · SQLite

---

## 📄 Licence

Distribué sous licence **MIT** : tu peux utiliser, modifier et redistribuer ce code librement, à condition de conserver la mention de copyright. Voir le fichier [`LICENSE`](LICENSE).
