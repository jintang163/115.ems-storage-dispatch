package com.ems.dispatch.tariff;

import com.ems.dispatch.masterdata.TariffPeriod;
import com.ems.dispatch.masterdata.TariffSchedule;
import com.ems.dispatch.masterdata.TariffScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class TariffService {

    private final TariffScheduleRepository repository;

    public TariffService(TariffScheduleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<TariffSchedule> current() {
        LocalDate today = LocalDate.now();
        return repository
                .findFirstByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByEffectiveFromDesc(
                        today, today);
    }

    /** 当前时刻所处时段及其电价。 */
    public Map<String, Object> currentPrice() {
        Optional<TariffSchedule> sch = current();
        Map<String, Object> out = new LinkedHashMap<>();
        if (sch.isEmpty()) {
            out.put("available", false);
            return out;
        }
        TariffSchedule s = sch.get();
        int minute = LocalDateTime.now().getHour() * 60 + LocalDateTime.now().getMinute();
        TariffPeriod hit = s.getPeriods().stream()
                .filter(p -> p.contains(minute))
                .findFirst()
                .orElse(s.getPeriods().isEmpty() ? null : s.getPeriods().get(0));
        out.put("available", true);
        out.put("scheduleId", s.getId());
        out.put("scheduleName", s.getName());
        out.put("source", s.getSource());
        out.put("currency", s.getCurrency());
        if (hit != null) {
            out.put("periodType", hit.getPeriodType());
            out.put("price", hit.getPrice());
        }
        return out;
    }

    @Transactional
    public TariffSchedule save(TariffSchedule schedule) {
        return repository.save(schedule);
    }

    @Transactional(readOnly = true)
    public java.util.List<TariffSchedule> all() {
        return repository.findAll();
    }
}
