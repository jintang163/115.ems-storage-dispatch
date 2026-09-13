"""Modbus TCP 设备模拟器：单 TCP 端口 5020 上挂 4 个从站。

slave 1 METER 负荷电表   slave 2 PV 光伏逆变器   slave 3 BMS   slave 4 PCS
寄存器布局与 ems_collector.registry.REGISTER_MAPS 完全一致。

用法：python -m simulator.modbus_simulator [--port 5020]
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import math
import threading
import time
from datetime import datetime

from pymodbus.datastore import (
    ModbusSequentialDataBlock,
    ModbusServerContext,
)
try:  # pymodbus >=3.10 改名为 ModbusDeviceContext
    from pymodbus.datastore import ModbusDeviceContext as ModbusSlaveContext
except ImportError:  # pragma: no cover - 旧版本兼容
    from pymodbus.datastore import ModbusSlaveContext
from pymodbus.server import StartAsyncTcpServer

log = logging.getLogger("simulator")

HOLDING_FC = 3  # pymodbus：保持寄存器功能码 0x03（store 内部地址自动 +1）


def u16(v: float, scale: float) -> int:
    return max(0, min(0xFFFF, int(round(v / scale))))


def i16(v: float, scale: float) -> int:
    raw = int(round(v / scale))
    if raw < 0:
        raw += 0x10000
    return max(0, min(0xFFFF, raw))


def u32(v: float, scale: float) -> tuple[int, int]:
    raw = max(0, min(0xFFFFFFFF, int(round(v / scale))))
    return (raw >> 16) & 0xFFFF, raw & 0xFFFF


class DeviceModel:
    """每秒推进一次的设备物理模型，写入自己的保持寄存器区。"""

    def __init__(self):
        self.energy_kwh = 10234.0
        self.pv_total_kwh = 58210.0
        self.pv_day_kwh = 0.0
        self.soc = 55.0
        self.pcs_p = 0.0
        self.alarm = 0

    def step(self, now: datetime) -> dict[int, list[int]]:
        hour = now.hour + now.minute / 60.0

        # ---- METER：基础工业负荷 90~210kW，叠加班次波动 ----
        load = 150 + 45 * math.sin((hour - 6) / 24 * 2 * math.pi) \
            + 18 * math.sin(now.second / 9.0)
        load = max(30.0, load)
        self.energy_kwh += load / 3600.0
        meter = [
            i16(load, 0.01),                    # 0 power_kw
            *u32(self.energy_kwh, 0.01),       # 1-2 energy_kwh
            u16(380.2, 0.1),                   # 3 voltage
            u16(load / (math.sqrt(3) * 0.38), 0.1),  # 4 current(A)
            u16(0.97, 0.001),                  # 5 pf
        ]

        # ---- PV：白天钟形出力曲线，峰值 ~500kW ----
        irr = max(0.0, 950 * math.exp(-((hour - 12.5) ** 2) / 12.0))
        pv_p = 0.5 * irr  # 500kWp
        self.pv_day_kwh += pv_p / 3600.0
        self.pv_total_kwh += pv_p / 3600.0
        pv = [
            u16(pv_p, 0.01),                  # 0 active_power_kw
            u16(self.pv_day_kwh, 0.01),       # 1 daily_energy_kwh
            *u32(self.pv_total_kwh, 0.01),    # 2-3 total_energy_kwh
            u16(irr, 1.0),                    # 4 irradiance_wm2
        ]

        # ---- PCS：跟随分时电价的充放电策略（演示用简化策略）----
        peak = (8.5 <= hour < 11.5) or (17.5 <= hour < 21)
        valley = hour < 7 or (12 <= hour < 13.5)
        if valley and self.soc < 95:
            self.pcs_p = -120.0
            state = 1
        elif peak and self.soc > 12:
            self.pcs_p = 200.0 + 10 * math.sin(now.second / 5.0)
            state = 2
        else:
            self.pcs_p = 0.0
            state = 0
        pcs = [i16(self.pcs_p, 0.01), i16(0.0, 0.01), state, self.alarm]

        # ---- BMS：SOC 跟随 PCS 功率变化（1MWh 电池，效率 0.95）----
        d_soc = (-self.pcs_p * (0.95 if self.pcs_p < 0 else 1 / 0.95)) / 1000.0 / 3600.0 * 100
        self.soc = min(98.0, max(8.0, self.soc + d_soc))
        current = -self.pcs_p * 1000 / 750.0   # P=U*I 近似
        bms = [
            u16(self.soc, 0.1),
            u16(98.1, 0.1),
            u16(750.0 + 6 * math.sin(now.second / 8.0), 0.1),
            i16(current, 0.1),
            i16(28.0 + 3 * math.sin(hour / 4), 0.1),
        ]
        return {1: meter, 2: pv, 3: bms, 4: pcs}


def build_context() -> tuple[ModbusServerContext, DeviceModel]:
    # datablock 从地址 1 起：pymodbus 服务端把 PDU 地址 0 映射到内部地址 1
    store = {slave: ModbusSlaveContext(hr=ModbusSequentialDataBlock(1, [0] * 16))
             for slave in (1, 2, 3, 4)}
    ctx = ModbusServerContext(slaves=store, single=False)
    return ctx, DeviceModel()


def updater(ctx: ModbusServerContext, model: DeviceModel):
    while True:
        values = model.step(datetime.now())
        for slave, regs in values.items():
            ctx[slave].setValues(HOLDING_FC, 0, regs)
        time.sleep(1)


async def main_async(port: int):
    ctx, model = build_context()
    threading.Thread(target=updater, args=(ctx, model), daemon=True).start()
    log.info("Modbus simulator listening on 0.0.0.0:%s (slaves 1=METER 2=PV 3=BMS 4=PCS)", port)
    await StartAsyncTcpServer(context=ctx, address=("0.0.0.0", port))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=5020)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO,
                        format="%(asctime)s %(levelname)-7s %(name)s | %(message)s")
    asyncio.run(main_async(args.port))


if __name__ == "__main__":
    main()
