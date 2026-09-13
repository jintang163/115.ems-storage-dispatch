# 联调与测试

## 自动化测试

```bash
# Python：寄存器解码、报文、断线缓存/序号持久化
cd edge-collector && python -m pytest -q

# Java：接入去重/补传统计、分时跨零点、HTTP 兜底→最新值查询（H2，免外部依赖）
cd backend && mvn test
```

## 完整链路（推荐：Docker 基础设施）

```bash
docker compose up -d postgres influxdb emqx
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
cd edge-collector
pip install -r requirements.txt
python -m simulator.modbus_simulator     # 终端 A：Modbus 四设备
python -m simulator.mqtt_simulator       # 终端 B：MQTT 直连电表
python -m ems_collector --config config.example.yaml   # 终端 C
```

打开 http://localhost:5173 看实时大屏；http://localhost:18083 是 EMQX 控制台。

## 无 Docker 环境的本地联调（本仓库采用）

用纯 Python 的 amqtt 替代 EMQX，H2 内存库替代 PostgreSQL，InfluxDB 降级关闭：

```bash
# 1) broker
pip install amqtt
amqtt -c <echo 'listeners:\n  default:\n    type: tcp\n    bind: 0.0.0.0:1883\nauth:\n  allow-anonymous: true\n' > /tmp/amqtt.yaml>
amqtt -c /tmp/amqtt.yaml

# 2) 模拟器与采集器
python -m simulator.modbus_simulator
python -m simulator.mqtt_simulator
PYTHONPATH=src python -m ems_collector --config config.example.yaml

# 3) 后端（H2 + 关闭 Influx，复用 test 作用域的 H2 驱动）
mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="\
--spring.datasource.url=jdbc:h2:mem:ems;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 \
--spring.datasource.driver-class-name=org.h2.Driver \
--spring.datasource.username=sa --spring.datasource.password= \
--spring.jpa.hibernate.ddl-auto=update --ems.influx.enabled=false"

# 4) 前端
npm run dev
```

## 验证断线缓存与补传

```bash
# broker 运行、采集器运行后
pkill -f amqtt                    # 断网：日志出现 disconnected，data/spool.db 行数增长
# 等待若干秒后重启 broker，日志出现：
#   replayed buffered seq=.. (spool=.., left=0)
# 最终 spool 归零；每个 seq 恰好补传一次（不重复）。
```

后端 `GET /api/v1/collector/status` 观察 `acceptedBatches / replayBatches /
duplicateBatches`；前端「采集状态」页可视化。

## 已验证结果（本环境实测）

- 4 台 Modbus 设备 + 1 台 MQTT 直连电表，每秒 5 点统一批次上送；
- 停 broker 后 63 个批次落 SQLite，恢复后逐批补传、spool 归零、零重复；
- 后端 MQTT 订阅实时入库（Influx 关闭时进最新值缓存）；HTTP 兜底同 seq 幂等去重；
- 分时电价、设备台账 REST API 正常；Vue3 前端 `vite build` 通过。
