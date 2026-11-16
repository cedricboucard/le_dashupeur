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
        conn.execute("DELETE FROM news WHERE status IN ('inbox', 'ignored')")
