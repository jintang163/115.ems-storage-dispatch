"""MQTT 直连设备适配：订阅设备原生主题，归一化为 ems Point 推入采集队列。

原生报文示例（智能电表）：
{"ts":"2026-09-13T08:00:00Z","power_kw":128.4,"energy_kwh":10234.5,
 "voltage":380.1,"current":195.0,"pf":0.98}
"""
from __future__ import annotations

import json
import logging
import queue
import threading

import paho.mqtt.client as mqtt

from .config import MqttDevice
from .model import Point, utc_now_iso

log = logging.getLogger(__name__)


class NativeMqttDevices:
    def __init__(self, mqtt_cfg: dict, devices: list[MqttDevice], out_queue: "queue.Queue[Point]"):
        self.devices = devices
        self.q = out_queue
        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=mqtt_cfg.get("clientId", "edge-gw") + "-native",
        )
        if mqtt_cfg.get("username"):
            self._client.username_pw_set(mqtt_cfg["username"], mqtt_cfg.get("password", ""))
        self._client.on_connect = self._on_connect
        self._client.on_message = self._on_message
        self._topic_map = {d.topic: d for d in devices}

    def connect(self, host: str, port: int):
        self._client.connect_async(host, port)
        self._client.loop_start()

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        for d in self.devices:
            client.subscribe(d.topic, qos=1)
            log.info("subscribed native device topic %s (%s)", d.topic, d.deviceId)

    def _on_message(self, client, userdata, msg):
        dev = self._topic_map.get(msg.topic)
        if not dev:
            return
        try:
            raw = json.loads(msg.payload.decode("utf-8"))
            ts = raw.pop("ts", None) or utc_now_iso()
            values = {k: float(v) for k, v in raw.items() if isinstance(v, (int, float))}
            self.q.put(Point(dev.deviceId, dev.deviceType, ts, values, "GOOD"))
        except Exception as e:
            log.warning("bad native payload on %s: %s", msg.topic, e)
