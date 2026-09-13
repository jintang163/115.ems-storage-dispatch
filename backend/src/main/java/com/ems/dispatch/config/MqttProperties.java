package com.ems.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ems.mqtt")
public class MqttProperties {
    private boolean enabled = true;
    private String broker = "tcp://localhost:1883";
    private String clientId = "ems-backend-ingest";
    private String username = "";
    private String password = "";
    /** 订阅主题，支持通配符 */
    private String telemetryTopic = "ems/+/telemetry";
    private int qos = 1;
    private int connectionTimeoutSec = 10;
    private int keepAliveIntervalSec = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTelemetryTopic() { return telemetryTopic; }
    public void setTelemetryTopic(String telemetryTopic) { this.telemetryTopic = telemetryTopic; }
    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
    public int getConnectionTimeoutSec() { return connectionTimeoutSec; }
    public void setConnectionTimeoutSec(int v) { this.connectionTimeoutSec = v; }
    public int getKeepAliveIntervalSec() { return keepAliveIntervalSec; }
    public void setKeepAliveIntervalSec(int v) { this.keepAliveIntervalSec = v; }
}
