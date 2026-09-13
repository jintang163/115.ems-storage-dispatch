"""配置加载。"""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import yaml


@dataclass
class ModbusDevice:
    host: str
    port: int
    slaveId: int
    deviceId: str
    deviceType: str


@dataclass
class MqttDevice:
    topic: str
    deviceId: str
    deviceType: str


@dataclass
class Config:
    raw: dict = field(default_factory=dict)

    @property
    def site_id(self) -> str:
        return self.raw["site"]["id"]

    @property
    def gateway_id(self) -> str:
        return self.raw["gateway"]["id"]

    @property
    def mqtt(self) -> dict:
        return self.raw["mqtt"]

    @property
    def collector(self) -> dict:
        return self.raw.get("collector", {})

    @property
    def modbus_devices(self) -> list[ModbusDevice]:
        return [ModbusDevice(**d) for d in self.raw.get("modbus", [])]

    @property
    def mqtt_devices(self) -> list[MqttDevice]:
        return [MqttDevice(**d) for d in self.raw.get("mqttDevices", [])]

    @staticmethod
    def load(path: str | Path) -> "Config":
        with open(path, "r", encoding="utf-8") as f:
            return Config(yaml.safe_load(f))
