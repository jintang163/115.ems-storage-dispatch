"""SQLite 断线缓存（spool）。

断网期间批次落盘；重连后按自增 id 顺序补传，发送成功（收到 PUBACK）后删除。
seq（批次序号）也持久化在 meta 表，重启不回退。
"""
from __future__ import annotations

import json
import sqlite3
import threading
from pathlib import Path


class Spool:
    def __init__(self, path: str = ":memory:"):
        self._lock = threading.Lock()
        # check_same_thread=False：采集协程线程与 MQTT 回调线程都会访问
        if path != ":memory:":
            parent = Path(path).parent
            parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.execute(
            """CREATE TABLE IF NOT EXISTS batches (
                   id INTEGER PRIMARY KEY AUTOINCREMENT,
                   seq INTEGER NOT NULL,
                   payload TEXT NOT NULL,
                   created_at TEXT NOT NULL DEFAULT (datetime('now'))
               )"""
        )
        self._conn.execute(
            """CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v INTEGER NOT NULL)"""
        )
        self._conn.commit()

    # ---- 批次缓存 ----
    def put(self, seq: int, payload: str) -> int:
        with self._lock:
            cur = self._conn.execute(
                "INSERT INTO batches(seq, payload) VALUES (?, ?)", (seq, payload)
            )
            self._conn.commit()
            return int(cur.lastrowid)

    def fetch_oldest(self, limit: int = 50) -> list[tuple[int, int, str]]:
        """返回 [(id, seq, payload), ...]，按入缓存顺序（时间顺序）。"""
        with self._lock:
            rows = self._conn.execute(
                "SELECT id, seq, payload FROM batches ORDER BY id ASC LIMIT ?", (limit,)
            ).fetchall()
            return [(int(r[0]), int(r[1]), r[2]) for r in rows]

    def delete(self, batch_id: int) -> None:
        with self._lock:
            self._conn.execute("DELETE FROM batches WHERE id = ?", (batch_id,))
            self._conn.commit()

    def size(self) -> int:
        with self._lock:
            return int(self._conn.execute("SELECT COUNT(*) FROM batches").fetchone()[0])

    # ---- 单调序号 ----
    def next_seq(self) -> int:
        with self._lock:
            row = self._conn.execute("SELECT v FROM meta WHERE k='seq'").fetchone()
            seq = (row[0] + 1) if row else 1
            self._conn.execute(
                "INSERT INTO meta(k,v) VALUES('seq',?) "
                "ON CONFLICT(k) DO UPDATE SET v=excluded.v",
                (seq,),
            )
            self._conn.commit()
            return seq

    def close(self) -> None:
        with self._lock:
            self._conn.close()
