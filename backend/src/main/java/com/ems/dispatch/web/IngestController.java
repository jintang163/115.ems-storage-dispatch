package com.ems.dispatch.web;

import com.ems.dispatch.ingest.BatchEnvelope;
import com.ems.dispatch.ingest.TelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** MQTT 不可用时边缘采集器的 HTTP 兜底上送通道。 */
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

    private final TelemetryService telemetryService;

    public IngestController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody BatchEnvelope batch) {
        telemetryService.ingest(batch);
        return ResponseEntity.ok(Map.of("accepted", true, "seq", batch.getSeq()));
    }
}
