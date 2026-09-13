package com.ems.dispatch.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/** 边缘上送批次信封 ems.telemetry.v1。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchEnvelope {
    public static final String PROTOCOL = "ems.telemetry.v1";

    private String protocol;
    private String siteId;
    private String gatewayId;
    private long seq;
    private String sentAt;
    private boolean buffered;
    private List<TelemetryPoint> points = new ArrayList<>();

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
    public boolean isBuffered() { return buffered; }
    public void setBuffered(boolean buffered) { this.buffered = buffered; }
    public List<TelemetryPoint> getPoints() { return points; }
    public void setPoints(List<TelemetryPoint> points) { this.points = points; }
}
