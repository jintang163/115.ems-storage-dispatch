package com.ems.dispatch.web;

import com.ems.dispatch.config.InfluxProperties;
import com.ems.dispatch.ingest.LatestValueStore;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxTable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 遥测查询：设备最新值（内存）+ 历史曲线（InfluxDB Flux）。 */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final LatestValueStore latest;
    private final ObjectProvider<InfluxDBClient> influxClient;
    private final InfluxProperties influxProps;

    public TelemetryController(LatestValueStore latest, ObjectProvider<InfluxDBClient> influxClient,
                               InfluxProperties influxProps) {
        this.latest = latest;
        this.influxClient = influxClient;
        this.influxProps = influxProps;
    }

    @GetMapping("/latest")
    public List<Map<String, Object>> latest() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LatestValueStore.Snapshot s : latest.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", s.deviceId());
            m.put("deviceType", s.deviceType());
            m.put("siteId", s.siteId());
            m.put("gatewayId", s.gatewayId());
            m.put("quality", s.quality());
            m.put("ts", s.ts() == null ? null : s.ts().toString());
            m.put("values", s.values());
            out.add(m);
        }
        return out;
    }

    /**
     * 历史曲线：GET /api/v1/telemetry/history?deviceId=bms-001&field=soc&range=-1h
     * range 为 Flux 区间，如 -15m / -1h / -24h。
     */
    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam String deviceId,
                                       @RequestParam String field,
                                       @RequestParam(defaultValue = "-1h") String range) {
        InfluxDBClient client = influxClient.getIfAvailable();
        if (client == null) {
            return Map.of("deviceId", deviceId, "field", field, "points", List.of(),
                    "note", "InfluxDB disabled");
        }
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s)
                  |> filter(fn: (r) => r._measurement == "telemetry"
                       and r.deviceId == "%s" and r._field == "%s")
                  |> sort(columns: ["_time"], desc: false)
                """.formatted(influxProps.getBucket(), safeRange(range), deviceId, field);

        QueryApi queryApi = client.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux);
        List<Map<String, Object>> points = new ArrayList<>();
        for (FluxTable t : tables) {
            t.getRecords().forEach(r -> points.add(Map.of(
                    "ts", r.getTime() == null ? "" : r.getTime().toString(),
                    "value", r.getValue() == null ? 0.0 : r.getValue())));
        }
        return Map.of("deviceId", deviceId, "field", field, "points", points);
    }

    /** 白名单式防注入：只允许 -数字+(m|h|d|w)。 */
    private String safeRange(String range) {
        if (range != null && range.matches("-\\d+[mhdw]")) {
            return range;
        }
        return "-1h";
    }
}
