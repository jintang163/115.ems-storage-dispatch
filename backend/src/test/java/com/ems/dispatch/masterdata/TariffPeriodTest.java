package com.ems.dispatch.masterdata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TariffPeriodTest {

    @Test
    void normalWindow() {
        TariffPeriod flat = new TariffPeriod("FLAT", 420, 510, 0.78); // 07:00-08:30
        assertThat(flat.contains(7 * 60)).isTrue();
        assertThat(flat.contains(8 * 60 + 29)).isTrue();
        assertThat(flat.contains(8 * 60 + 30)).isFalse(); // 结束不含
    }

    @Test
    void crossMidnightWindow() {
        TariffPeriod valley = new TariffPeriod("VALLEY", 22 * 60, 6 * 60, 0.32);
        assertThat(valley.contains(23 * 60)).isTrue();
        assertThat(valley.contains(3 * 60)).isTrue();
        assertThat(valley.contains(12 * 60)).isFalse();
    }
}
