package com.ems.dispatch.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface TariffScheduleRepository extends JpaRepository<TariffSchedule, Long> {
    /** 取指定日期生效的方案（上限一条），按生效日倒序。 */
    Optional<TariffSchedule> findFirstByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByEffectiveFromDesc(
            LocalDate from, LocalDate to);
}
