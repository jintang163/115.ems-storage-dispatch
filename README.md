# EMS 储能优化调度系统（能源管理系统）

面向园区 / 工厂的储能 EMS，从 0 到 1 构建。本仓库当前交付 **数据采集与接入层**（需求一），后续阶段在其之上构建负荷 / 光伏预测、储能优化调度、策略下发与收益分析。

## 技术栈

| 层 | 技术 |
|---|---|
| 边缘采集 | Python 3.11 · pymodbus 3.x · paho-mqtt 2.x · SQLite（断线缓存） |
| 设备协议 | Modbus TCP（电表 / 光伏逆变器 / BMS / PCS）、MQTT（直连智能电表） |
| 后端 | Spring Boot 3.3 · Java 17 · 原生 Eclipse Paho MQTT 客户端 · Spring Data JPA |
| 消息 | EMQX（MQTT 3.1.1/5 Broker） |
| 存储 | InfluxDB 2.x（时序遥测）· PostgreSQL 16（设备台账、分时电价等主数据） |
| 前端 | Vue 3 · Vite · ECharts 5 · Vue Router · Axios |

## 目录结构

```
.
├── edge-collector/     # Python 边缘网关采集器（Modbus/MQTT 采集 + 断线缓存补传）
├── backend/            # Spring Boot 3 接入服务（MQTT 订阅、双写存储、REST API）
├── frontend/           # Vue3 + ECharts 监控大屏 / 设备管理 / 电价配置 / 采集状态
├── docs/               # 架构、遥测协议、寄存器点表
└── docker-compose.yml  # PostgreSQL + InfluxDB2 + EMQX 一键启动
```

## 快速开始

### 1. 启动基础设施

```bash
docker compose up -d postgres influxdb emqx
# InfluxDB UI  http://localhost:8086 （admin/admin12345，org=ems，bucket=telemetry）
# EMQX 控制台  http://localhost:18083 （admin/public）
```

### 2. 启动后端

```bash
cd backend
./mvnw spring-boot:run          # 或 mvn spring-boot:run
# REST: http://localhost:8080
```

首次启动自动建表并初始化示例设备台账与一套工商业分时电价。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev                     # http://localhost:5173
```

### 4. 启动边缘采集器（含设备模拟器）

```bash
cd edge-collector
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt

# 终端 A：Modbus 设备模拟器（电表/光伏/BMS/PCS，slave id 1~4）
python -m simulator.modbus_simulator
# 终端 B：MQTT 直连电表模拟器
python -m simulator.mqtt_simulator
# 终端 C：采集器
python -m ems_collector --config config.example.yaml
```

采集器每秒轮询 Modbus 设备、订阅 MQTT 设备主题，统一封装为 `ems.telemetry.v1`
批次报文发布到 `ems/{siteId}/telemetry`；后端订阅 `ems/+/telemetry` 写入 InfluxDB。

### 5. 验证断线缓存与补传

```bash
docker compose stop emqx          # 采集器日志：disconnected → spooled to SQLite
docker compose start emqx         # 恢复后：replayed N buffered batch(es)，无数据丢失
```

## 已交付功能（阶段一：数据采集与接入）

- [x] 园区实时用电负荷接入（Modbus TCP 电表 + MQTT 直连电表两种方式）
- [x] 分时电价接入（后台手动配置 + 可扩展电力公司远程接口 Provider）
- [x] 光伏出力实时数据接入（逆变器 Modbus：功率 / 发电量 / 辐照度）
- [x] BMS 数据接入：SOC、SOH、总电压、电流、温度
- [x] PCS 数据接入：充放电功率（正放电/负充电）、运行状态、告警码
- [x] 数据断线本地缓存（SQLite）与恢复后自动补传（QoS 1 + 序号去重）
- [x] HTTP 补传兜底通道（MQTT 长期不可用时）
- [x] 遥测数据双写：InfluxDB（时序）/ 最新值内存态（API 快查）
- [x] 前端实时监控大屏、设备台账管理、分时电价配置、采集链路状态监视

## 后续阶段

负荷/光伏超短期预测 → 储能优化调度（低谷充、高峰放、需量控制）→ PCS 充放电指令
下发（`ems/{siteId}/cmd/{gatewayId}` 主题已预留）→ 充放电策略与经济性分析。
