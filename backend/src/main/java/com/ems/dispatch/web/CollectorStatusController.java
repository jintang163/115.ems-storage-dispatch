package com.ems.dispatch.web;

import com.ems.dispatch.ingest.CollectorStatus;
import com.ems.dispatch.ingest.TelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 采集链路状态：Broker 连接、网关在线/补传、接入计数。 */
@RestController
@RequestMapping("/api/v1/collector")
public class CollectorStatusController {

    private final CollectorStatus status;
    private final TelemetryService telemetryService;

    public CollectorStatusController(CollectorStatus status, TelemetryService telemetryService) {
        this.status = status;
        this.telemetryService = telemetryService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>(status.snapshot());
        m.put("ingest", telemetryService.stats());
        return m;
    }
}
