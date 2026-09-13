package com.ems.dispatch.ingest;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * InfluxDB 写入。InfluxDBClient bean 不存在（ems.influx.enabled=false）时
 * 自动降级为 no-op，便于在无 InfluxDB 环境下开发 / 单测。
 */
@Service
public class InfluxWriter {
    private static final Logger log = LoggerFactory.getLogger(InfluxWriter.class);

    private final WriteApi writeApi;   // 可为 null

    @Autowired
    public InfluxWriter(@Autowired(required = false) InfluxDBClient client) {
        if (client != null) {
            this.writeApi = client.makeWriteApi();
            log.info("InfluxDB writer ready");
        } else {
            this.writeApi = null;
            log.warn("InfluxDB disabled (ems.influx.enabled=false), telemetry kept only in latest cache");
        }
    }

    void write(BatchEnvelope batch, TelemetryPoint p, Instant ts) {
        if (writeApi == null) {
            return;
        }
        Point point = Point.measurement("telemetry")
                .addTag("siteId", batch.getSiteId())
                .addTag("gatewayId", batch.getGatewayId())
                .addTag("deviceId", p.getDeviceId())
                .addTag("deviceType", p.getDeviceType())
                .time(ts, WritePrecision.NS);

        for (Map.Entry<String, Object> e : p.getValues().entrySet()) {
            String key = e.getKey();
            if (key.endsWith("_text")) {
                continue;   // 枚举文本不入库
            }
            Object v = e.getValue();
            if (v instanceof Number n) {
                point.addField(key, n.doubleValue());
            } else if (v instanceof Boolean b) {
                point.addField(key, b);
            } else if (v != null) {
                try {
                    point.addField(key, Double.parseDouble(v.toString()));
                } catch (NumberFormatException ignore) {
                    // 非数值字段跳过
                }
            }
        }
        writeApi.writePoint(point);
    }
}
