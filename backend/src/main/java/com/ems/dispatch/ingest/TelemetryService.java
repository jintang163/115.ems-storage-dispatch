package com.ems.dispatch.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 遥测接入核心：报文校验 → 幂等去重(gatewayId+seq) → 最新值缓存 → InfluxDB。
 * 去重窗口为有界 LRU，防止长期运行内存膨胀；窗口远大于单网关断线补传量。
 */
@Service
public class TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);
    private static final int DEDUP_CAPACITY = 50_000;

    private final InfluxWriter influxWriter;
    private final LatestValueStore latest;

    private final Map<String, LinkedHashMap<Long, Boolean>> seenByGateway = new ConcurrentHashMap<>();
    private final AtomicLong acceptedBatches = new AtomicLong();
    private final AtomicLong duplicateBatches = new AtomicLong();
    private final AtomicLong replayBatches = new AtomicLong();
    private final AtomicLong badBatches = new AtomicLong();

    public TelemetryService(InfluxWriter influxWriter, LatestValueStore latest) {
        this.influxWriter = influxWriter;
        this.latest = latest;
    }

    public void ingest(BatchEnvelope batch) {
        if (batch == null || !BatchEnvelope.PROTOCOL.equals(batch.getProtocol())) {
            badBatches.incrementAndGet();
            log.warn("reject batch: bad protocol: {}", batch == null ? null : batch.getProtocol());
            throw new IllegalArgumentException("unsupported protocol");
        }
        if (isBlank(batch.getGatewayId()) || isBlank(batch.getSiteId())
                || batch.getPoints() == null || batch.getPoints().isEmpty()) {
            badBatches.incrementAndGet();
            throw new IllegalArgumentException("gatewayId/siteId/points required");
        }

        synchronized (gatewayWindow(batch.getGatewayId())) {
            if (isDuplicate(batch.getGatewayId(), batch.getSeq())) {
                duplicateBatches.incrementAndGet();
                log.debug("duplicate batch gw={} seq={} ignored", batch.getGatewayId(), batch.getSeq());
                return;
            }
        }
        if (batch.isBuffered()) {
            replayBatches.incrementAndGet();
        }
        acceptedBatches.incrementAndGet();

        for (TelemetryPoint p : batch.getPoints()) {
            if (isBlank(p.getDeviceId()) || isBlank(p.getDeviceType())) {
                continue;
            }
            Instant ts = parseTs(p.getTs());
            influxWriter.write(batch, p, ts);
            latest.update(batch, p, ts);
        }
    }

    private boolean isDuplicate(String gatewayId, long seq) {
        LinkedHashMap<Long, Boolean> window = gatewayWindow(gatewayId);
        if (window.containsKey(seq)) {
            return true;
        }
        window.put(seq, Boolean.TRUE);
        if (window.size() > DEDUP_CAPACITY) {
            Iterator<Long> it = window.keySet().iterator();
            it.next();
            it.remove();
        }
        return false;
    }

    private LinkedHashMap<Long, Boolean> gatewayWindow(String gatewayId) {
        return seenByGateway.computeIfAbsent(gatewayId,
                k -> new LinkedHashMap<>(1024, 0.75f, false));
    }

    private static Instant parseTs(String ts) {
        try {
            return Instant.parse(ts);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("acceptedBatches", acceptedBatches.get());
        m.put("replayBatches", replayBatches.get());
        m.put("duplicateBatches", duplicateBatches.get());
        m.put("badBatches", badBatches.get());
        m.put("devicesWithData", latest.deviceCount());
        return m;
    }
}
