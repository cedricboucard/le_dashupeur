import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).parent / "dashupeur.db"


def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    with get_conn() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS news (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                source TEXT NOT NULL,
                published TEXT,
                summary TEXT,
                score INTEGER DEFAULT 0,
                tags TEXT,
                status TEXT DEFAULT 'inbox',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        # Relevés du détecteur de tendances (un relevé = une ligne par jeu et par source)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS trend_snapshots (
                taken_at TEXT NOT NULL,
                source TEXT NOT NULL,
                game_key TEXT NOT NULL,
                game_name TEXT NOT NULL,
                value INTEGER NOT NULL,
                url TEXT,
                image TEXT,
                PRIMARY KEY (taken_at, source, game_key)
            )
        """)
        # Espace X : news piochées pour des tweets, indépendamment de leur statut dans l'Hebdo
        conn.execute("""
            CREATE TABLE IF NOT EXISTS x_picks (
                news_id TEXT PRIMARY KEY REFERENCES news(id) ON DELETE CASCADE,
                position REAL NOT NULL
            )
        """)
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_trend_game
            ON trend_snapshots (source, game_key, taken_at)
        """)


def upsert_news(item: dict) -> bool:
    """Ajoute la news si elle n'existe pas déjà. Renvoie True si elle a été ajoutée."""
    with get_conn() as conn:
        cur = conn.execute("""
            INSERT OR IGNORE INTO news (id, title, url, source, published, summary, score, tags)
            VALUES (:id, :title, :url, :source, :published, :summary, :score, :tags)
        """, item)
        return cur.rowcount == 1


def get_news(status: str | None = None, min_score: int = 0):
    with get_conn() as conn:
        query = "SELECT * FROM news WHERE score >= ?"
        params = [min_score]
        if status:
            query += " AND status = ?"
            params.append(status)
        query += " ORDER BY score DESC, created_at DESC"
        return [dict(row) for row in conn.execute(query, params).fetchall()]


def update_status(news_id: str, new_status: str):
    with get_conn() as conn:
        conn.execute("UPDATE news SET status = ? WHERE id = ?", (new_status, news_id))


def clear_inbox():
    with get_conn() as conn:
        # Les news piochées pour X restent, même si elles étaient dans la boîte de réception
        conn.execute("""
            DELETE FROM news WHERE status IN ('inbox', 'ignored')
            AND id NOT IN (SELECT news_id FROM x_picks)
        """)


# --- Espace X ---

def get_all_news(search: str = "", limit: int = 60):
    """Toutes les news (quel que soit leur statut), les plus récemment ajoutées d'abord."""
    with get_conn() as conn:
        query = "SELECT * FROM news"
        params = []
        if search:
            query += " WHERE lower(title || ' ' || ifnull(summary, '') || ' ' || source) LIKE ?"
            params.append(f"%{search.lower()}%")
        query += " ORDER BY published DESC, created_at DESC LIMIT ?"
        params.append(limit)
        return [dict(row) for row in conn.execute(query, params).fetchall()]


def get_x_picks():
    with get_conn() as conn:
        rows = conn.execute("""
            SELECT news.* FROM x_picks JOIN news ON news.id = x_picks.news_id
            ORDER BY x_picks.position
        """).fetchall()
        return [dict(row) for row in rows]


def add_x_pick(news_id: str):
    with get_conn() as conn:
        conn.execute("""
            INSERT OR IGNORE INTO x_picks (news_id, position)
            VALUES (?, (SELECT ifnull(max(position), 0) + 1 FROM x_picks))
        """, (news_id,))


def remove_x_pick(news_id: str):
    with get_conn() as conn:
        conn.execute("DELETE FROM x_picks WHERE news_id = ?", (news_id,))


def clear_x_picks():
    with get_conn() as conn:
        conn.execute("DELETE FROM x_picks")


def move_x_pick(news_id: str, offset: int):
    """Échange la news avec sa voisine (offset -1 = monter, +1 = descendre)."""
    with get_conn() as conn:
        ids = [r[0] for r in conn.execute("SELECT news_id FROM x_picks ORDER BY position")]
        i = ids.index(news_id)
        j = i + offset
        if not 0 <= j < len(ids):
            return
        ids[i], ids[j] = ids[j], ids[i]
        conn.executemany("UPDATE x_picks SET position = ? WHERE news_id = ?",
                         [(pos, nid) for pos, nid in enumerate(ids, start=1)])
