package com.ems.dispatch.tariff;

import com.ems.dispatch.masterdata.TariffSchedule;

/**
 * 分时电价来源。
 * MANUAL：使用后台人工配置（PostgreSQL 中的 TariffSchedule）。
 * REMOTE：从电力公司接口拉取并落库（HttpRemoteTariffProvider）。
 */
public interface TariffProvider {

    String code();

    /** 拉取/构建当前生效方案；无数据返回 null。 */
    TariffSchedule fetchCurrent();
}
