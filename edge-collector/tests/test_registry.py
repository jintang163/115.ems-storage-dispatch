from ems_collector.registry import REGISTER_MAPS, decode_registers, PCS_RUN_STATE


def test_decode_bms():
    # soc 62.4%(addr0), soh 98.1%(addr1), voltage 752.0V(addr2),
    # current -31.5A(addr3), temp 28.2C(addr4)
    regs = [624, 981, 7520, (-315) & 0xFFFF, 282, 0, 0, 0]
    v = decode_registers(regs, REGISTER_MAPS["BMS"])
    assert v["soc"] == 62.4
    assert v["soh"] == 98.1
    assert v["voltage"] == 752.0
    assert v["current"] == -31.5
    assert v["temp"] == 28.2


def test_decode_u32_meter_energy():
    # energy_kwh = 10234.56 kWh → u32 raw 1023456 = 0x000F_9E40
    raw = int(1023456)
    hi, lo = (raw >> 16) & 0xFFFF, raw & 0xFFFF
    regs = [12345, hi, lo, 3801, 100, 970, 0, 0]
    v = decode_registers(regs, REGISTER_MAPS["METER"])
    assert v["power_kw"] == 123.45
    assert v["energy_kwh"] == 10234.56
    assert v["voltage"] == 380.1
    assert v["pf"] == 0.97


def test_decode_pcs_enum_and_negative_power():
    # 放电 +200kW，状态 2=DISCHARGING，告警 1=OVER_TEMP
    regs = [20000, 0, 2, 1, 0, 0, 0, 0]
    v = decode_registers(regs, REGISTER_MAPS["PCS"])
    assert v["p_kw"] == 200.0
    assert v["run_state"] == 2
    assert v["run_state_text"] == "DISCHARGING"
    assert v["alarm_code_text"] == "OVER_TEMP"

    # 充电 -120kW
    regs[0] = (-12000) & 0xFFFF
    v = decode_registers(regs, REGISTER_MAPS["PCS"])
    assert v["p_kw"] == -120.0
    assert PCS_RUN_STATE[0] == "STANDBY"
