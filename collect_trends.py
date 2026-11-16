"""Relevé du détecteur de tendances, à lancer par la tâche planifiée (toutes les 3 h).

Exemple de ligne crontab :
    0 */3 * * * cd /chemin/vers/le_dashupeur && venv/bin/python collect_trends.py >> logs/trends.log 2>&1
"""
from datetime import datetime

from db import init_db
from trends import collect_all

if __name__ == "__main__":
    init_db()
    report = collect_all()
    details = " | ".join(f"{source}: {result}" for source, result in report.items())
    print(f"[{datetime.now():%Y-%m-%d %H:%M}] {details}", flush=True)
