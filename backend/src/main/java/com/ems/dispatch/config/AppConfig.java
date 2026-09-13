package com.ems.dispatch.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@Configuration
@EnableConfigurationProperties({MqttProperties.class, InfluxProperties.class})
public class AppConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "ems.influx", name = "enabled", havingValue = "true")
    public InfluxDBClient influxDBClient(InfluxProperties props) {
        return InfluxDBClientFactory.create(props.getUrl(), props.getToken().toCharArray(),
                props.getOrg(), props.getBucket());
    }
}
