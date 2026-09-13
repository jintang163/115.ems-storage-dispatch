"""Modbus TCP 读取器：按点表轮询单台设备，返回 Point。"""
from __future__ import annotations

import logging

from pymodbus.client import ModbusTcpClient

from .model import Point, utc_now_iso
from .registry import REGISTER_MAPS, decode_registers

log = logging.getLogger(__name__)

# 点表中最大地址跨度，一次连续读取 0..max_addr+1
_READ_SPAN = 8


class ModbusDeviceReader:
    def __init__(self, host: str, port: int, slave_id: int,
                 device_id: str, device_type: str, timeout: float = 3.0):
        self.device_id = device_id
        self.device_type = device_type
        self.slave_id = slave_id
        self.regmap = REGISTER_MAPS[device_type]
        self._client = ModbusTcpClient(host=host, port=port, timeout=timeout)

    def connect(self) -> bool:
        return self._client.connect()

    def poll(self) -> Point:
        try:
            rr = self._client.read_holding_registers(
                address=0, count=_READ_SPAN, slave=self.slave_id
            )
            if rr.isError():
                log.warning("[%s] modbus read error: %s", self.device_id, rr)
                return Point.bad(self.device_id, self.device_type)
            values = decode_registers(rr.registers, self.regmap)
            return Point(self.device_id, self.device_type, utc_now_iso(), values, "GOOD")
        except Exception as e:  # 网络异常 / 超时
            log.warning("[%s] modbus poll failed: %s", self.device_id, e)
            return Point.bad(self.device_id, self.device_type)

    def close(self) -> None:
        self._client.close()
