package com.ems.dispatch.masterdata;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 分时电价方案（TOU tariff），含若干时段。 */
@Entity
@Table(name = "tariff_schedule")
public class TariffSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    /** MANUAL 后台配置 / REMOTE 电力公司接口拉取 */
    @Column(nullable = false, length = 16)
    private String source = "MANUAL";

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @Column(length = 8)
    private String currency = "CNY";

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("startMinute ASC")
    private List<TariffPeriod> periods = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public List<TariffPeriod> getPeriods() { return periods; }

    public void addPeriod(TariffPeriod p) {
        p.setSchedule(this);
        periods.add(p);
    }
}
