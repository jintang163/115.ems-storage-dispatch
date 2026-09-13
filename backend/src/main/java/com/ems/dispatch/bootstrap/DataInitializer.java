package com.ems.dispatch.bootstrap;

import com.ems.dispatch.masterdata.Device;
import com.ems.dispatch.masterdata.DeviceRepository;
import com.ems.dispatch.masterdata.TariffPeriod;
import com.ems.dispatch.masterdata.TariffSchedule;
import com.ems.dispatch.masterdata.TariffScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 首次启动初始化示例设备台账与一套典型工商业分时电价。 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final DeviceRepository devices;
    private final TariffScheduleRepository tariffs;

    public DataInitializer(DeviceRepository devices, TariffScheduleRepository tariffs) {
        this.devices = devices;
        this.tariffs = tariffs;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (devices.count() == 0) {
            List<Device> seed = List.of(
                    new Device("meter-001", "园区总电表", "METER", "MODBUS",
                            "modbus://localhost:5020#1"),
                    new Device("meter-002", "2# 厂房智能电表", "METER", "MQTT",
                            "devices/meter-002/up"),
                    new Device("pv-001", "屋顶光伏 500kWp", "PV", "MODBUS",
                            "modbus://localhost:5020#2"),
                    new Device("bms-001", "1MWh 电池簇 BMS", "BMS", "MODBUS",
                            "modbus://localhost:5020#3"),
                    new Device("pcs-001", "250kW 储能变流器", "PCS", "MODBUS",
                            "modbus://localhost:5020#4"));
            devices.saveAll(seed);
            log.info("seeded {} devices", seed.size());
        }

        if (tariffs.count() == 0) {
            TariffSchedule sch = new TariffSchedule();
            sch.setName("工商业一般工商业分时电价（示例）");
            sch.setSource("MANUAL");
            sch.setEffectiveFrom(LocalDate.of(2026, 1, 1));
            sch.setEffectiveTo(LocalDate.of(2026, 12, 31));
            // 典型三段（谷/平/峰，尖峰在夏尖月另配）
            sch.addPeriod(new TariffPeriod("VALLEY", 0, 420, 0.32));     // 00:00-07:00
            sch.addPeriod(new TariffPeriod("FLAT", 420, 510, 0.78));     // 07:00-08:30
            sch.addPeriod(new TariffPeriod("PEAK", 510, 690, 1.18));     // 08:30-11:30
            sch.addPeriod(new TariffPeriod("FLAT", 690, 1050, 0.78));    // 11:30-17:30
            sch.addPeriod(new TariffPeriod("PEAK", 1050, 1260, 1.18));   // 17:30-21:00
            sch.addPeriod(new TariffPeriod("FLAT", 1260, 1320, 0.78));   // 21:00-22:00
            sch.addPeriod(new TariffPeriod("VALLEY", 1320, 1440, 0.32)); // 22:00-24:00
            tariffs.save(sch);
            log.info("seeded sample TOU tariff with {} periods", sch.getPeriods().size());
        }
    }
}
