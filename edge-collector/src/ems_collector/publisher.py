"""上送通道：MQTT QoS1 优先；断线时落 SQLite，重连补传；长期不可用走 HTTP 兜底。"""
from __future__ import annotations

import json
import logging
import threading
import time

import paho.mqtt.client as mqtt
import requests

from .spool import Spool

log = logging.getLogger(__name__)


class Publisher:
    def __init__(self, mqtt_cfg: dict, spool: Spool, replay_batch_size: int = 50):
        self.cfg = mqtt_cfg
        self.spool = spool
        self.replay_batch_size = replay_batch_size
        self.topic = mqtt_cfg["topic"]
        self.http_url = mqtt_cfg.get("httpFailoverUrl")
        self.http_after = float(mqtt_cfg.get("httpFailoverAfterSec", 30))

        self.connected = threading.Event()
        self._online_since: float | None = None     # 最近一次连接成功时刻
        self._offline_since: float | None = None   # 最近一次断开时刻
        self._last_http_attempt = 0.0

        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=mqtt_cfg.get("clientId", "edge-gw"),
            clean_session=True,
        )
        if mqtt_cfg.get("username"):
            self._client.username_pw_set(mqtt_cfg["username"], mqtt_cfg.get("password", ""))
        self._client.on_connect = self._on_connect
        self._client.on_disconnect = self._on_disconnect
        self._client.on_publish = self._on_publish

    # ---------- MQTT 回调 ----------
    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        ok = (reason_code == 0) if isinstance(reason_code, int) else (getattr(reason_code, "is_failure", True) is False)
        if ok:
            self.connected.set()
            self._online_since = time.time()
            self._offline_since = None
            log.info("MQTT connected, replaying spool ...")
            threading.Thread(target=self._replay_loop, daemon=True).start()
        else:
            log.error("MQTT connect failed: %s", reason_code)

    def _on_disconnect(self, client, userdata, *args, **kwargs):
        self.connected.clear()
        self._offline_since = time.time()
        log.warning("MQTT disconnected, new batches will be spooled")

    def _on_publish(self, client, userdata, mid, reason_code=None, properties=None):
        # PUBACK 到达；补传报文在 wait_for_publish() 确认后直接删缓存，此处无需处理
        pass

    # ---------- 生命周期 ----------
    def start(self):
        self._client.connect_async(self.cfg["host"], int(self.cfg.get("port", 1883)),
                                   keepalive=int(self.cfg.get("keepaliveSec", 30)))
        self._client.loop_start()

    def stop(self):
        self._client.loop_stop()
        self._client.disconnect()

    # ---------- 发布 ----------
    def publish(self, seq: int, payload: str) -> str:
        """返回投递结果：MQTT_ACK / SPOOLED / HTTP。"""
        if self.connected.is_set():
            info = self._client.publish(self.topic, payload, qos=1)
            # 等待 PUBACK（短超时），超时则落盘交给补传，保证不丢
            info.wait_for_publish(timeout=5)
            if info.is_published():
                return "MQTT_ACK"
            log.warning("publish ack timeout for seq=%s, spooling", seq)
            self.spool.put(seq, payload)
            return "SPOOLED"

        # 离线：先落盘
        self.spool.put(seq, payload)
        # 离线超过阈值，尝试 HTTP 兜底
        if self.http_url and self._offline_since and \
                (time.time() - self._offline_since) > self.http_after:
            if self._http_failover(payload):
                return "HTTP"
        return "SPOOLED"

    def _http_failover(self, payload: str) -> bool:
        # 简单限频，避免实时通道每 1s 都打 HTTP
        if time.time() - self._last_http_attempt < 5:
            return False
        self._last_http_attempt = time.time()
        try:
            resp = requests.post(self.http_url, data=payload.encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, timeout=5)
            if resp.status_code == 200:
                log.info("HTTP failover delivered batch")
                return True
            log.warning("HTTP failover status=%s", resp.status_code)
        except Exception as e:
            log.warning("HTTP failover error: %s", e)
        return False

    # ---------- 补传 ----------
    def _replay_loop(self):
        """连接恢复后按时间顺序补传缓存批次；MQTT 再次断开则退出，下次重连重启。"""
        while self.connected.is_set():
            pending = self.spool.fetch_oldest(self.replay_batch_size)
            if not pending:
                time.sleep(0.5)
                continue
            for spool_id, seq, payload in pending:
                if not self.connected.is_set():
                    return
                try:
                    obj = json.loads(payload)
                    obj["buffered"] = True
                    payload = json.dumps(obj, ensure_ascii=False, separators=(",", ":"))
                    info = self._client.publish(self.topic, payload, qos=1)
                    info.wait_for_publish(timeout=10)
                    if info.is_published():
                        # wait_for_publish() 仅在 PUBACK 到达后返回 True，
                        # 此时确认删除，保证"已删除即已送达"。
                        self.spool.delete(spool_id)
                        log.info("replayed buffered seq=%s (spool=%s, left=%d)",
                                 seq, spool_id, self.spool.size())
                    else:
                        return  # 发不出去，停止本轮，等下次重连
                except Exception as e:
                    log.warning("replay error seq=%s: %s", seq, e)
                    return
            time.sleep(0.2)
