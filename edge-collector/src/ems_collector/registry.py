"""Modbus 寄存器点表与解码。

每种设备类型一张点表：量测键 ↔ 保持寄存器地址、数据类型、缩放系数。
与 simulator/modbus_simulator.py、docs/modbus-registers.md 保持一致。

数据类型：
  u16 / i16  : 单寄存器，无符号 / 有符号
  u32_ab     : 双寄存器 32 位无符号，大端（高字在前）
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

# PCS 运行状态码 → 文本
PCS_RUN_STATE = {0: "STANDBY", 1: "CHARGING", 2: "DISCHARGING", 3: "FAULT"}
# 简化告警码表（0 表示无告警）
PCS_ALARM = {0: "NORMAL", 1: "OVER_TEMP", 2: "DC_OVER_VOLTAGE", 3: "AC_OVER_CURRENT", 4: "INSULATION_FAULT"}


@dataclass(frozen=True)
class RegDef:
    key: str
    address: int
    dtype: str = "u16"
    scale: float = 1.0
    offset: float = 0.0
    decimals: int | None = None
    enum: dict[int, str] | None = None


# 地址 0 起，u32_ab 占用 address 与 address+1 两个寄存器
REGISTER_MAPS: dict[str, list[RegDef]] = {
    "METER": [
        RegDef("power_kw", 0, "i16", 0.01, decimals=2),       # 园区总有功功率，可双向
        RegDef("energy_kwh", 1, "u32_ab", 0.01, decimals=2),  # 累计电量
        RegDef("voltage", 3, "u16", 0.1, decimals=1),
        RegDef("current", 4, "u16", 0.1, decimals=1),
        RegDef("pf", 5, "u16", 0.001, decimals=3),
    ],
    "PV": [
        RegDef("active_power_kw", 0, "u16", 0.01, decimals=2),
        RegDef("daily_energy_kwh", 1, "u16", 0.01, decimals=2),
        RegDef("total_energy_kwh", 2, "u32_ab", 0.01, decimals=2),
        RegDef("irradiance_wm2", 4, "u16", 1.0),
    ],
    "BMS": [
        RegDef("soc", 0, "u16", 0.1, decimals=1),
        RegDef("soh", 1, "u16", 0.1, decimals=1),
        RegDef("voltage", 2, "u16", 0.1, decimals=1),
        RegDef("current", 3, "i16", 0.1, decimals=1),          # 正放电 / 负充电
        RegDef("temp", 4, "i16", 0.1, decimals=1),
    ],
    "PCS": [
        RegDef("p_kw", 0, "i16", 0.01, decimals=2),            # 正放电 / 负充电
        RegDef("q_kvar", 1, "i16", 0.01, decimals=2),
        RegDef("run_state", 2, "u16", enum=PCS_RUN_STATE),
        RegDef("alarm_code", 3, "u16", enum=PCS_ALARM),
    ],
}


def _to_signed16(v: int) -> int:
    return v - 0x10000 if v > 0x7FFF else v


def decode_registers(registers: list[int], regmap: list[RegDef]) -> dict[str, Any]:
    """把原始寄存器数组解码为量测字典；枚举量同时输出数值键与 *_text。"""
    out: dict[str, Any] = {}
    for rd in regmap:
        if rd.dtype in ("u16", "i16"):
            if rd.address >= len(registers):
                continue
            raw = registers[rd.address]
            raw = _to_signed16(raw) if rd.dtype == "i16" else raw
        elif rd.dtype == "u32_ab":
            if rd.address + 1 >= len(registers):
                continue
            raw = (registers[rd.address] << 16) | registers[rd.address + 1]
        else:
            raise ValueError(f"unsupported dtype: {rd.dtype}")

        if rd.enum is not None:
            out[rd.key] = raw
            out[f"{rd.key}_text"] = rd.enum.get(raw, f"UNKNOWN({raw})")
        else:
            val = raw * rd.scale + rd.offset
            out[rd.key] = round(val, rd.decimals) if rd.decimals is not None else val
    return out
