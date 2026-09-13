package com.ems.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 电力公司电价接口配置。配置 ems.tariff.remote.url 后启用远程 Provider 与定时拉取。 */
@ConfigurationProperties(prefix = "ems.tariff.remote")
public class TariffRemoteProperties {
    private String url;
    /** 拉取 cron，默认每天 02:17 */
    private String cron = "0 17 2 * * *";

    private String nameField = "name";
    private String effectiveFromField = "effectiveFrom";
    private String effectiveToField = "effectiveTo";
    private String periodsField = "periods";
    private String periodTypeField = "periodType";
    private String startField = "start";
    private String endField = "end";
    private String priceField = "price";

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public String getNameField() { return nameField; }
    public void setNameField(String nameField) { this.nameField = nameField; }
    public String getEffectiveFromField() { return effectiveFromField; }
    public void setEffectiveFromField(String v) { this.effectiveFromField = v; }
    public String getEffectiveToField() { return effectiveToField; }
    public void setEffectiveToField(String v) { this.effectiveToField = v; }
    public String getPeriodsField() { return periodsField; }
    public void setPeriodsField(String v) { this.periodsField = v; }
    public String getPeriodTypeField() { return periodTypeField; }
    public void setPeriodTypeField(String v) { this.periodTypeField = v; }
    public String getStartField() { return startField; }
    public void setStartField(String startField) { this.startField = startField; }
    public String getEndField() { return endField; }
    public void setEndField(String endField) { this.endField = endField; }
    public String getPriceField() { return priceField; }
    public void setPriceField(String priceField) { this.priceField = priceField; }
}
