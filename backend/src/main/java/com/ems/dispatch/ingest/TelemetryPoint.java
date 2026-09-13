package com.ems.dispatch.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryPoint {
    private String deviceId;
    private String deviceType;
    private String ts;
    private String quality = "GOOD";
    /** 量测键 → 数值；枚举文本键(*_text)写库时剔除。 */
    private Map<String, Object> values = new HashMap<>();

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getTs() { return ts; }
    public void setTs(String ts) { this.ts = ts; }
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) { this.values = values; }
}
