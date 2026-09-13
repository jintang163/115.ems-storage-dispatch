# 遥测报文协议 ems.telemetry.v1

边缘采集器 → 后端，主题 `ems/{siteId}/telemetry`，QoS 1，JSON UTF-8。
同一报文也可通过 HTTP `POST /api/v1/ingest/telemetry` 发送（兜底通道）。

## 批次信封 BatchEnvelope

```json
{
  "protocol": "ems.telemetry.v1",
  "siteId": "park-a",
  "gatewayId": "gw-001",
  "seq": 1024,
  "sentAt": "2026-09-13T08:00:00.500Z",
  "buffered": false,
  "points": [ { "Point": "见下" } ]
}
```

| 字段 | 说明 |
|---|---|
| seq | 网关单调递增批次序号；重连补传保持原序号，后端据此幂等去重 |
| buffered | true 表示该报文为断线期间缓存、现在补传的历史数据 |
| sentAt | 报文发送时间（ISO-8601 UTC） |

## Point

```json
{
  "deviceId": "bms-001",
  "deviceType": "BMS",
  "ts": "2026-09-13T08:00:00.000Z",
  "values": { "soc": 62.4, "soh": 98.1, "voltage": 752.0, "current": -31.5, "temp": 28.2 },
  "quality": "GOOD"
}
```

- `ts`：采样时间（非发送时间），补传时保持原始采样时间。
- `quality`：`GOOD` / `BAD`（读到异常值/超时，仍上报以便监视）/ `STALE`。
- `values` 只放数值量；状态量同时以数值码和 `*_text` 字符串给出（见点表）。

## 设备类型与量测键 measurement keys

| deviceType | keys |
|---|---|
| METER（电表/负荷） | `power_kw`总有功功率, `energy_kwh`累计电量, `voltage`, `current`, `pf` |
| PV（光伏逆变器） | `active_power_kw`, `daily_energy_kwh`, `total_energy_kwh`, `irradiance_wm2`(气象站) |
| BMS | `soc`%(0-100), `soh`%(0-100), `voltage`V(簇总压), `current`A(正放电/负充电), `temp`℃(最高温) |
| PCS | `p_kw`有功功率(正放电/负充电), `q_kvar`无功, `run_state`(0待机 1充电 2放电 3故障), `run_state_text`, `alarm_code`, `alarm_code_text` |

## InfluxDB 映射

- bucket：`telemetry`，measurement：`telemetry`
- tags：`siteId, gatewayId, deviceId, deviceType`
- fields：`values` 中全部键（float；状态文本不入库，状态码入库）
- timestamp：Point.ts（ns）

## 下行（预留）

主题 `ems/{siteId}/cmd/{gatewayId}`，QoS1：
`{"cmdId":"...","deviceId":"pcs-001","action":"SET_POWER","pKw":-50,"ts":"..."}`
