"""统一遥测报文模型 ems.telemetry.v1。"""
from __future__ import annotations

import json
import time
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from typing import Any

from . import PROTOCOL


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


@dataclass
class Point:
    deviceId: str
    deviceType: str
    ts: str
    values: dict[str, Any] = field(default_factory=dict)
    quality: str = "GOOD"

    @staticmethod
    def bad(device_id: str, device_type: str) -> "Point":
        return Point(device_id, device_type, utc_now_iso(), {}, "BAD")


@dataclass
class BatchEnvelope:
    siteId: str
    gatewayId: str
    seq: int
    sentAt: str
    points: list[Point]
    protocol: str = PROTOCOL
    buffered: bool = False

    def to_json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False, separators=(",", ":"))

    @staticmethod
    def from_json(raw: str | bytes) -> "BatchEnvelope":
        obj = json.loads(raw)
        obj["points"] = [Point(**p) for p in obj.get("points", [])]
        return BatchEnvelope(**obj)


def now_ms() -> int:
    return int(time.time() * 1000)
