# Modbus 寄存器点表（保持寄存器 Holding Registers，地址从 0 开始）

所有设备经同一 Modbus TCP 网关（模拟器端口 5020），以 slave id 区分：

| Slave | 设备 | deviceType |
|---|---|---|
| 1 | 园区总电表（负荷） | METER |
| 2 | 光伏逆变器 | PV |
| 3 | 电池管理系统 | BMS |
| 4 | 储能变流器 | PCS |

数值统一：寄存器为 raw int，工程值 = raw × scale + offset。
32 位量采用大端双寄存器（高字在低地址 `u32_ab`）。

## METER (slave 1)

| 地址 | 类型 | scale | 键 | 单位/说明 |
|---|---|---|---|---|
| 0 | i16 | 0.01 | power_kw | kW，总有功功率（双向） |
| 1-2 | u32_ab | 0.01 | energy_kwh | kWh，累计电量 |
| 3 | u16 | 0.1 | voltage | V |
| 4 | u16 | 0.1 | current | A |
| 5 | u16 | 0.001 | pf | 功率因数 |

## PV (slave 2)

| 地址 | 类型 | scale | 键 | 单位 |
|---|---|---|---|---|
| 0 | u16 | 0.01 | active_power_kw | kW |
| 1 | u16 | 0.01 | daily_energy_kwh | kWh（当日） |
| 2-3 | u32_ab | 0.01 | total_energy_kwh | kWh（累计） |
| 4 | u16 | 1 | irradiance_wm2 | W/m²（气象站辐照度） |

## BMS (slave 3)

| 地址 | 类型 | scale | 键 | 单位 |
|---|---|---|---|---|
| 0 | u16 | 0.1 | soc | % 荷电状态 |
| 1 | u16 | 0.1 | soh | % 健康度 |
| 2 | u16 | 0.1 | voltage | V 电池簇总压 |
| 3 | i16 | 0.1 | current | A（正放电/负充电） |
| 4 | i16 | 0.1 | temp | ℃ 最高温度 |

## PCS (slave 4)

| 地址 | 类型 | scale | 键 | 单位 |
|---|---|---|---|---|
| 0 | i16 | 0.01 | p_kw | kW（正放电/负充电） |
| 1 | i16 | 0.01 | q_kvar | kvar 无功 |
| 2 | u16 | 枚举 | run_state | 0待机 1充电 2放电 3故障 |
| 3 | u16 | 枚举 | alarm_code | 0正常 1过温 2直流过压 3交流过流 4绝缘故障 |

> 生产接入新设备：在 `src/ems_collector/registry.py` 的 `REGISTER_MAPS` 中
> 增加/调整 RegDef 即可，无需改动采集与上送逻辑。
