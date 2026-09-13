package com.ems.dispatch.tariff;

import com.ems.dispatch.config.TariffRemoteProperties;
import com.ems.dispatch.masterdata.TariffPeriod;
import com.ems.dispatch.masterdata.TariffSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 电力公司远程电价接口 Provider。
 *
 * 约定远端返回 JSON：
 * {
 *   "name": "2026 工商业分时电价",
 *   "effectiveFrom": "2026-01-01", "effectiveTo": "2026-12-31",
 *   "periods": [
 *     {"periodType":"VALLEY","start":"00:00","end":"07:00","price":0.32}, ...
 *   ]
 * }
 * 字段名可通过 ems.tariff.remote.* 配置适配不同公司接口。
 */
@Component
@ConditionalOnProperty(prefix = "ems.tariff.remote", name = "url")
public class HttpRemoteTariffProvider implements TariffProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpRemoteTariffProvider.class);

    private final TariffRemoteProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    public HttpRemoteTariffProvider(TariffRemoteProperties props) {
        this.props = props;
    }

    @Override
    public String code() {
        return "REMOTE";
    }

    @Override
    @SuppressWarnings("unchecked")
    public TariffSchedule fetchCurrent() {
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(props.getUrl(), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return null;
            }
            TariffSchedule sch = new TariffSchedule();
            sch.setName(String.valueOf(body.getOrDefault(props.getNameField(), "远程分时电价")));
            sch.setSource("REMOTE");
            sch.setEffectiveFrom(LocalDate.parse(String.valueOf(body.get(props.getEffectiveFromField()))));
            sch.setEffectiveTo(LocalDate.parse(String.valueOf(body.get(props.getEffectiveToField()))));

            for (Map<String, Object> raw : (List<Map<String, Object>>) body.get(props.getPeriodsField())) {
                int start = toMinute(String.valueOf(raw.get(props.getStartField())));
                int end = toMinute(String.valueOf(raw.get(props.getEndField())));
                String type = String.valueOf(raw.get(props.getPeriodTypeField()));
                double price = ((Number) raw.get(props.getPriceField())).doubleValue();
                sch.addPeriod(new TariffPeriod(type, start, end, price));
            }
            log.info("fetched remote tariff '{}' with {} periods", sch.getName(), sch.getPeriods().size());
            return sch;
        } catch (Exception e) {
            log.warn("fetch remote tariff failed: {}", e.getMessage());
            return null;
        }
    }

    /** "07:30" -> 450；兼容已是分钟数的情况。 */
    private int toMinute(String hhmm) {
        if (hhmm.contains(":")) {
            String[] a = hhmm.split(":");
            return Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]);
        }
        return Integer.parseInt(hhmm);
    }
}
