"""MQTT 直连智能电表模拟器，每秒发布原生 JSON 到 devices/meter-002/up。

用法：python -m simulator.mqtt_simulator [--host localhost --port 1883]
"""
from __future__ import annotations

import argparse
import json
import logging
import math
import signal
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

log = logging.getLogger("mqtt-sim")

TOPIC = "devices/meter-002/up"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", type=int, default=1883)
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s %(levelname)-7s %(name)s | %(message)s")

    client = mqtt.Client(callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
                         client_id="sim-meter-002")
    client.reconnect_delay_set(min_delay=1, max_delay=10)
    client.connect(args.host, args.port)
    client.loop_start()

    energy = 4521.0
    stop = False
    signal.signal(signal.SIGINT, lambda *_: globals().__setitem__("stop", True))
    log.info("publishing native meter telemetry to %s @ %s:%s", TOPIC, args.host, args.port)
    while not stop:
        hour = datetime.now().hour + datetime.now().minute / 60
        power = 80 + 20 * math.sin(hour / 24 * 2 * math.pi) + 4 * math.sin(time.time())
        energy += power / 3600.0
        payload = {
            "ts": datetime.now(timezone.utc).isoformat(timespec="milliseconds")
                   .replace("+00:00", "Z"),
            "power_kw": round(power, 2),
            "energy_kwh": round(energy, 2),
            "voltage": 379.8,
            "current": round(power / (math.sqrt(3) * 0.38) / 1000 * 1000, 1),
            "pf": 0.98,
        }
        client.publish(TOPIC, json.dumps(payload), qos=1)
        time.sleep(1)
    client.loop_stop()


if __name__ == "__main__":
    main()
