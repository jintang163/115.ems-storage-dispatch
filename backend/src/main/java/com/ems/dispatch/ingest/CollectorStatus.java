package com.ems.dispatch.ingest;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** MQTT 链路与各边缘网关在线状态（最近报文心跳判定）。 */
@Component
public class CollectorStatus {

    public record GatewayState(String gatewayId, boolean online, boolean lastBatchBuffered,
                               long lastSeq, Instant lastSeen) {}

    /** 超过此时长无报文判定为离线 */
    private static final long STALE_SECONDS = 15;

    private final AtomicBoolean brokerConnected = new AtomicBoolean(false);
    private final AtomicLong totalMessages = new AtomicLong();
    private final Map<String, GatewayState> gateways = new ConcurrentHashMap<>();

    void setBrokerConnected(boolean ok) {
        brokerConnected.set(ok);
    }

    void markMessage() {
        totalMessages.incrementAndGet();
    }

    /** 由上报处带 seq 更新。 */
    public void updateSeq(String gatewayId, long seq, boolean buffered) {
        GatewayState prev = gateways.get(gatewayId);
        gateways.put(gatewayId, new GatewayState(
                gatewayId, true, buffered, seq, Instant.now()));
    }

    public boolean isBrokerConnected() {
        return brokerConnected.get();
    }

    public long getTotalMessages() {
        return totalMessages.get();
    }

    public Map<String, Object> snapshot() {
        Instant now = Instant.now();
        var list = gateways.values().stream()
                .map(g -> {
                    boolean online = now.getEpochSecond() - g.lastSeen().getEpochSecond() <= STALE_SECONDS;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("gatewayId", g.gatewayId());
                    m.put("online", online);
                    m.put("lastBatchBuffered", g.lastBatchBuffered());
                    m.put("lastSeq", g.lastSeq());
                    m.put("lastSeen", g.lastSeen().toString());
                    m.put("ageSec", now.getEpochSecond() - g.lastSeen().getEpochSecond());
                    return m;
                })
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("brokerConnected", brokerConnected.get());
        out.put("totalMessages", totalMessages.get());
        out.put("now", now.toString());
        out.put("staleThresholdSec", STALE_SECONDS);
        out.put("gateways", list);
        return out;
    }
}
