package com.ems.dispatch.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 设备台账：电表 / 光伏 / BMS / PCS。 */
@Entity
@Table(name = "device")
public class Device {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    /** METER / PV / BMS / PCS */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(length = 32)
    private String siteId = "park-a";

    /** MODBUS / MQTT */
    @Column(nullable = false, length = 16)
    private String protocol;

    /** Modbus: host:port#slave；MQTT: 原生上行主题 */
    @Column(length = 256)
    private String endpoint;

    private boolean enabled = true;
    private Instant createdAt = Instant.now();

    protected Device() { }

    public Device(String id, String name, String type, String protocol, String endpoint) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.protocol = protocol;
        this.endpoint = endpoint;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
