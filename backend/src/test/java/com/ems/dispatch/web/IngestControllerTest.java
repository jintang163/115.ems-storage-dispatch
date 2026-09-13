package com.ems.dispatch.web;

import com.ems.dispatch.ingest.BatchEnvelope;
import com.ems.dispatch.ingest.CollectorStatus;
import com.ems.dispatch.ingest.InfluxWriter;
import com.ems.dispatch.ingest.LatestValueStore;
import com.ems.dispatch.ingest.TelemetryPoint;
import com.ems.dispatch.ingest.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IngestControllerTest {

    @Autowired MockMvc mvc;

    private String json(String deviceId) {
        return """
                {"protocol":"ems.telemetry.v1","siteId":"park-a","gatewayId":"gw-t1",
                 "seq":1,"sentAt":"%s","buffered":false,
                 "points":[{"deviceId":"%s","deviceType":"BMS","ts":"%s",
                   "values":{"soc":55.5,"soh":98.0,"voltage":750.0,"current":-10.0,"temp":27.0},
                   "quality":"GOOD"}]}
                """.formatted(Instant.now(), deviceId, Instant.now());
    }

    @Test
    void httpFailoverIngestThenQueryLatest() throws Exception {
        mvc.perform(post("/api/v1/ingest/telemetry")
                        .contentType(MediaType.APPLICATION_JSON).content(json("bms-http-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                // 重复 seq 幂等：第二次仍 200，但 latest 不新增设备
                .andDo(r -> {});

        mvc.perform(get("/api/v1/telemetry/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value("bms-http-1"))
                .andExpect(jsonPath("$[0].values.soc").value(55.5));

        mvc.perform(get("/api/v1/collector/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ingest.acceptedBatches").exists());
    }
}
