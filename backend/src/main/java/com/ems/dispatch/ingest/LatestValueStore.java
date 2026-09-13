package com.ems.dispatch.ingest;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 设备最新一帧遥测的内存缓存，供监控大屏秒级快查，不打 InfluxDB。 */
@Service
public class LatestValueStore {

    public record Snapshot(String deviceId, String deviceType, String siteId, String gatewayId,
                           String quality, Instant ts, Map<String, Object> values) {}

    private final Map<String, Snapshot> store = new ConcurrentHashMap<>();

    void update(BatchEnvelope batch, TelemetryPoint p, Instant ts) {
        store.put(p.getDeviceId(), new Snapshot(
                p.getDeviceId(), p.getDeviceType(), batch.getSiteId(), batch.getGatewayId(),
                p.getQuality(), ts, Map.copyOf(p.getValues())));
    }

    public List<Snapshot> all() {
        List<Snapshot> list = new ArrayList<>(store.values());
        list.sort(Comparator.comparing(Snapshot::deviceId));
        return list;
    }

    public int deviceCount() {
        return store.size();
    }
}
