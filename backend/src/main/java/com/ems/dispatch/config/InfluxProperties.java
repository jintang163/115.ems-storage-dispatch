package com.ems.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ems.influx")
public class InfluxProperties {
    private boolean enabled = true;
    private String url = "http://localhost:8086";
    private String org = "ems";
    private String bucket = "telemetry";
    private String token = "ems-dev-token";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
