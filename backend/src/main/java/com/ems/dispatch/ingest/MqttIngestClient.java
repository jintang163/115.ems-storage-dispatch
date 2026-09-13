package com.ems.dispatch.ingest;

import com.ems.dispatch.config.MqttProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订阅 ems/{siteId}/telemetry（通配 ems/+/telemetry）。
 * cleanSession=false + QoS1：后端短暂重启期间 Broker 保留离线消息，重连后继续投递。
 */
@Component
@ConditionalOnProperty(prefix = "ems.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttIngestClient implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestClient.class);

    private final MqttProperties props;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;
    private final CollectorStatus status;
    private MqttClient client;

    public MqttIngestClient(MqttProperties props, TelemetryService telemetryService,
                            ObjectMapper objectMapper, CollectorStatus status) {
        this.props = props;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
        this.status = status;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(props.getBroker(), props.getClientId(), new MemoryPersistence());
            client.setCallback(this);
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(false);
            opts.setConnectionTimeout(props.getConnectionTimeoutSec());
            opts.setKeepAliveInterval(props.getKeepAliveIntervalSec());
            if (!props.getUsername().isBlank()) {
                opts.setUserName(props.getUsername());
                opts.setPassword(props.getPassword().toCharArray());
            }
            client.connect(opts);   // 首次连接；失败由 automaticReconnect 续连
        } catch (Exception e) {
            log.warn("initial MQTT connect failed (auto-reconnect on): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null) {
                client.disconnect();
                client.close();
            }
        } catch (Exception ignore) { }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(props.getTelemetryTopic(), props.getQos());
            status.setBrokerConnected(true);
            log.info("MQTT {} subscribed to {}", reconnect ? "reconnected," : "connected,",
                    props.getTelemetryTopic());
        } catch (Exception e) {
            log.error("subscribe failed: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        status.setBrokerConnected(false);
        log.warn("MQTT connection lost: {}", cause == null ? "" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        status.markMessage();
        try {
            BatchEnvelope batch = objectMapper.readValue(message.getPayload(), BatchEnvelope.class);
            telemetryService.ingest(batch);
            status.updateSeq(batch.getGatewayId(), batch.getSeq(), batch.isBuffered());
        } catch (Exception e) {
            log.warn("invalid telemetry on {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { }
}
