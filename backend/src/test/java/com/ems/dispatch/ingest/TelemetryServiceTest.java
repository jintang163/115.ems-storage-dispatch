package com.ems.dispatch.ingest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryServiceTest {

    private TelemetryService newService(LatestValueStore latest) {
        return new TelemetryService(new InfluxWriter(null), latest);
    }

    private BatchEnvelope batch(long seq, boolean buffered) {
        BatchEnvelope b = new BatchEnvelope();
        b.setProtocol(BatchEnvelope.PROTOCOL);
        b.setSiteId("park-a");
        b.setGatewayId("gw-001");
        b.setSeq(seq);
        b.setBuffered(buffered);
        b.setSentAt(Instant.now().toString());
        TelemetryPoint p = new TelemetryPoint();
        p.setDeviceId("bms-001");
        p.setDeviceType("BMS");
        p.setTs(Instant.now().toString());
        p.setValues(new java.util.HashMap<>(Map.of("soc", 62.4, "run_state_text", "X")));
        b.setPoints(List.of(p));
        return b;
    }

    @Test
    void acceptsAndUpdatesLatestCache() {
        LatestValueStore latest = new LatestValueStore();
        TelemetryService svc = newService(latest);

        svc.ingest(batch(1, false));
        svc.ingest(batch(2, true));   // 补传

        assertThat(latest.deviceCount()).isEqualTo(1);
        assertThat(latest.all().get(0).values().get("soc")).isEqualTo(62.4);
        Map<String, Object> stats = svc.stats();
        assertThat(stats.get("acceptedBatches")).isEqualTo(2L);
        assertThat(stats.get("replayBatches")).isEqualTo(1L);
    }

    @Test
    void duplicateGatewaySeqIsIgnored() {
        LatestValueStore latest = new LatestValueStore();
        TelemetryService svc = newService(latest);

        svc.ingest(batch(10, false));
        svc.ingest(batch(10, false));   // 重发/补传重复
        svc.ingest(batch(11, false));

        Map<String, Object> stats = svc.stats();
        assertThat(stats.get("acceptedBatches")).isEqualTo(2L);
        assertThat(stats.get("duplicateBatches")).isEqualTo(1L);
    }

    @Test
    void rejectsBadProtocol() {
        TelemetryService svc = newService(new LatestValueStore());
        BatchEnvelope bad = batch(1, false);
        bad.setProtocol("other/1");
        assertThatThrownBy(() -> svc.ingest(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
