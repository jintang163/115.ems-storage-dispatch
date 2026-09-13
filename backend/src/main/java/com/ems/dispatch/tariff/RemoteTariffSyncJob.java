package com.ems.dispatch.tariff;

import com.ems.dispatch.config.TariffRemoteProperties;
import com.ems.dispatch.masterdata.TariffSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 配置了电力公司接口 URL 后，定时拉取远程分时电价并落库。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(TariffRemoteProperties.class)
@ConditionalOnProperty(prefix = "ems.tariff.remote", name = "url")
public class RemoteTariffSyncJob {

    private static final Logger log = LoggerFactory.getLogger(RemoteTariffSyncJob.class);

    private final TariffProvider provider;
    private final TariffService tariffService;
    private final TariffRemoteProperties props;

    public RemoteTariffSyncJob(HttpRemoteTariffProvider provider,
                               TariffService tariffService, TariffRemoteProperties props) {
        this.provider = provider;
        this.tariffService = tariffService;
        this.props = props;
    }

    /** 固定延迟 + 启动后 20s 首跑；cron 可配置。 */
    @Scheduled(fixedDelayString = "#{${ems.tariff.sync-fixed-delay-ms:21600000}}")
    public void sync() {
        try {
            TariffSchedule remote = provider.fetchCurrent();
            if (remote != null) {
                tariffService.save(remote);
                log.info("remote tariff persisted ({} periods)", remote.getPeriods().size());
            }
        } catch (Exception e) {
            log.warn("remote tariff sync error: {}", e.getMessage());
        }
    }
}
