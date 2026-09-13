package com.ems.dispatch.web;

import com.ems.dispatch.masterdata.TariffPeriod;
import com.ems.dispatch.masterdata.TariffSchedule;
import com.ems.dispatch.masterdata.TariffScheduleRepository;
import com.ems.dispatch.tariff.TariffService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 分时电价：手动配置 CRUD + 当前生效方案 / 当前电价查询。 */
@RestController
@RequestMapping("/api/v1/tariffs")
public class TariffController {

    private final TariffScheduleRepository repository;
    private final TariffService tariffService;

    public TariffController(TariffScheduleRepository repository, TariffService tariffService) {
        this.repository = repository;
        this.tariffService = tariffService;
    }

    @GetMapping
    public List<TariffSchedule> list() {
        return repository.findAll();
    }

    @GetMapping("/current")
    public ResponseEntity<TariffSchedule> current() {
        return tariffService.current().map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/current-price")
    public Map<String, Object> currentPrice() {
        return tariffService.currentPrice();
    }

    @PostMapping
    public TariffSchedule create(@Valid @RequestBody TariffSchedule body) {
        return repository.save(reassociate(new TariffSchedule(), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TariffSchedule> update(@PathVariable Long id,
                                                 @Valid @RequestBody TariffSchedule body) {
        return repository.findById(id).map(existing -> {
            existing.getPeriods().clear();
            reassociate(existing, body);
            return ResponseEntity.ok(repository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** 把请求体中的时段重新挂到受管方案上（保证 join 列非空）。 */
    private TariffSchedule reassociate(TariffSchedule target, TariffSchedule body) {
        target.setName(body.getName());
        target.setSource(body.getSource() == null ? "MANUAL" : body.getSource());
        target.setEffectiveFrom(body.getEffectiveFrom());
        target.setEffectiveTo(body.getEffectiveTo());
        if (body.getCurrency() != null) {
            target.setCurrency(body.getCurrency());
        }
        for (TariffPeriod p : body.getPeriods()) {
            target.addPeriod(new TariffPeriod(
                    p.getPeriodType(), p.getStartMinute(), p.getEndMinute(), p.getPrice()));
        }
        return target;
    }
}
