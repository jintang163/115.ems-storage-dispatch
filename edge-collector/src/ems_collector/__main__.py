"""边缘采集器入口。

用法：python -m ems_collector --config config.example.yaml
"""
from __future__ import annotations

import argparse
import logging
import queue
import signal
import threading
import time

from .config import Config
from .model import BatchEnvelope, Point, utc_now_iso
from .modbus_reader import ModbusDeviceReader
from .native_mqtt import NativeMqttDevices
from .publisher import Publisher
from .spool import Spool

log = logging.getLogger("ems_collector")


class Collector:
    def __init__(self, cfg: Config):
        self.cfg = cfg
        col_cfg = cfg.collector
        self.interval = float(col_cfg.get("pollIntervalSec", 1))

        self.spool = Spool(col_cfg.get("spoolPath", ":memory:"))
        self.publisher = Publisher(cfg.mqtt, self.spool,
                                   int(col_cfg.get("replayBatchSize", 50)))
        self.readers = [
            ModbusDeviceReader(d.host, d.port, d.slaveId, d.deviceId, d.deviceType)
            for d in cfg.modbus_devices
        ]
        self._native_q: queue.Queue[Point] = queue.Queue(maxsize=10000)
        self.native = (NativeMqttDevices(cfg.mqtt, cfg.mqtt_devices, self._native_q)
                       if cfg.mqtt_devices else None)
        self._stop = threading.Event()
        self._stats = {"polls": 0, "mqtt": 0, "spooled": 0, "http": 0, "bad": 0}

    def run(self):
        self.publisher.start()
        # 等待首个连接（连不上也继续——数据会进 spool）
        if self.native:
            self.native.connect(self.cfg.mqtt["host"], int(self.cfg.mqtt.get("port", 1883)))

        for r in self.readers:
            ok = r.connect()
            log.info("modbus %s connect=%s", r.device_id, ok)

        signal.signal(signal.SIGINT, self._signal)
        signal.signal(signal.SIGTERM, self._signal)

        log.info("collector started: %d modbus devices, interval=%ss, spool pending=%d",
                 len(self.readers), self.interval, self.spool.size())
        while not self._stop.is_set():
            t0 = time.time()
            self._tick()
            dt = time.time() - t0
            self._stop.wait(max(0.0, self.interval - dt))
        self._shutdown()

    def _tick(self):
        # 1) 轮询所有 Modbus 设备（顺序读，设备少；设备多时可改线程池）
        points: list[Point] = []
        for r in self.readers:
            p = r.poll()
            if p.quality == "BAD":
                self._stats["bad"] += 1
            points.append(p)
        # 2) 收取 MQTT 直连设备本周期内的点
        while True:
            try:
                points.append(self._native_q.get_nowait())
            except queue.Empty:
                break
        if not points:
            return

        batch = BatchEnvelope(
            siteId=self.cfg.site_id,
            gatewayId=self.cfg.gateway_id,
            seq=self.spool.next_seq(),
            sentAt=utc_now_iso(),
            points=points,
        )
        result = self.publisher.publish(batch.seq, batch.to_json())
        self._stats["polls"] += 1
        self._stats[{"MQTT_ACK": "mqtt", "SPOOLED": "spooled", "HTTP": "http"}[result]] += 1
        if self._stats["polls"] % 30 == 0:
            log.info("stats %s spool_size=%d", self._stats, self.spool.size())

    def _signal(self, signum, frame):
        self._stop.set()

    def _shutdown(self):
        log.info("shutting down ...")
        for r in self.readers:
            r.close()
        self.publisher.stop()
        self.spool.close()


def main():
    ap = argparse.ArgumentParser(description="EMS edge collector")
    ap.add_argument("--config", "-c", required=True, help="YAML 配置文件")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(name)s | %(message)s",
    )
    cfg = Config.load(args.config)
    Collector(cfg).run()


if __name__ == "__main__":
    main()
