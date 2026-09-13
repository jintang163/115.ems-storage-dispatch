# 系统架构

```
┌──────────────────────────── 园区 / 工厂现场 ────────────────────────────┐
│                                                                         │
│  智能电表(负荷)   光伏逆变器(+气象站)    电池簇 BMS         储能 PCS     │
│       │                │                    │                  │       │
│   (MQTT 直连)     Modbus TCP ───────┐  Modbus TCP       Modbus TCP      │
│       │                            │      │                  │       │
│       │              ┌─────────────┴──────┴──────────────────┘        │
│       │              │        边缘采集器 edge-collector (Python)        │
│       └──MQTT────────┤  · 轮询调度 · 寄存器点表(YAML) · 质量戳/单位换算  │
│                      │  · SQLite 断线缓存(spool)  · 恢复后按序号补传     │
│                      └───────────────┬───────────────┬───────────────  │
│                          MQTT QoS1   │            HTTP(兜底)            │
└──────────────────────────────────────┼─────────────────────────────────┘
                                       │
                          ems/{site}/telemetry (ems.telemetry.v1)
                                       │
                      ┌────────────────▼─────────────────┐
                      │       EMQX (MQTT Broker)          │
                      └────────────────┬─────────────────┘
                                       │ 订阅 ems/+/telemetry
              ┌────────────────────────▼────────────────────────┐
              │            接入服务 backend (Spring Boot 3)       │
              │  MqttIngestService(Paho)  去重/校验 → InfluxDB2   │
              │  设备台账/电价 JPA → PostgreSQL                   │
              │  TariffProvider: MANUAL | REMOTE(电力公司 API)    │
              │  REST /api/**  + 最新值缓存(CollectorStatus)      │
              └───────┬───────────────────────────────┬──────────┘
                      │                               │
              ┌───────▼────────┐             ┌────────▼────────┐
              │  InfluxDB 2.x  │             │  PostgreSQL 16  │
              │ 遥测时序(measurement)│         │ 设备/电价/网关   │
              └────────────────┘             └─────────────────┘
                      ▲
                      │ REST (Flux 查询/聚合)
              ┌───────┴────────┐
              │ Vue3 + ECharts │  实时大屏 / 设备管理 / 电价配置 / 采集状态
              └────────────────┘
```

## 关键设计

1. **统一遥测信封**：所有设备（无论 Modbus 还是 MQTT）在边缘侧归一化为同一
   `ems.telemetry.v1` 批次 JSON，后端只面向一种格式，新增设备协议只改边缘侧。
2. **不丢数据**：边缘→Broker 用 QoS 1；边缘本地 SQLite spool 保证 Broker/网络中断时
   数据落盘，重连后按 `(gatewayId, seq)` 顺序补传；后端按消息幂等键去重。
   MQTT 长期不可用时自动切换 HTTP POST `/api/v1/ingest/telemetry` 兜底。
3. **时序与主数据分离**：高频遥测进 InfluxDB（保留策略与降采样后续在 bucket 上配置），
   设备台账、点表、分时电价进 PostgreSQL。
4. **电价双来源**：`TariffProvider` 抽象为 `MANUAL`（后台 CRUD）与 `REMOTE`
   （电力公司接口，`HttpRemoteTariffProvider` 可配置 URL/字段映射，定时拉取）。
5. **可扩展到调度**：遥测、电价、设备模型即为优化调度模块的输入；预留
   `ems/{siteId}/cmd/{gatewayId}` 下行主题与 PCS 写寄存器能力。
