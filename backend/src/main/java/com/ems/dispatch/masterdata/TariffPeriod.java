package com.ems.dispatch.masterdata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 单个分时时段：[startMinute, endMinute)，允许跨零点（start>end）。 */
@Entity
@Table(name = "tariff_period")
public class TariffPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** VALLEY 谷 / FLAT 平 / SHOULDER 尖 / PEAK 峰 */
    @Column(nullable = false, length = 16)
    private String periodType;

    /** 一天中的起始分钟 0~1439 */
    @Column(nullable = false)
    private int startMinute;

    /** 结束分钟（不含），小于 startMinute 表示跨零点 */
    @Column(nullable = false)
    private int endMinute;

    /** 电价 元/kWh */
    @Column(nullable = false)
    private double price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    @JsonIgnore
    private TariffSchedule schedule;

    protected TariffPeriod() { }

    public TariffPeriod(String periodType, int startMinute, int endMinute, double price) {
        this.periodType = periodType;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.price = price;
    }

    public boolean contains(int minuteOfDay) {
        if (startMinute <= endMinute) {
            return minuteOfDay >= startMinute && minuteOfDay < endMinute;
        }
        // 跨零点，如 22:00(1320) -> 6:00(360)
        return minuteOfDay >= startMinute || minuteOfDay < endMinute;
    }

    public Long getId() { return id; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }
    public int getEndMinute() { return endMinute; }
    public void setEndMinute(int endMinute) { this.endMinute = endMinute; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public TariffSchedule getSchedule() { return schedule; }
    public void setSchedule(TariffSchedule schedule) { this.schedule = schedule; }
}
