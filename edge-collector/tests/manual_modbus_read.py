import sys, time
sys.path.insert(0, 'src')
from pymodbus.client import ModbusTcpClient
from ems_collector.registry import REGISTER_MAPS, decode_registers

c = ModbusTcpClient('127.0.0.1', port=5020, timeout=3)
assert c.connect(), 'connect failed'
time.sleep(1.5)
for slave, typ in [(1, 'METER'), (2, 'PV'), (3, 'BMS'), (4, 'PCS')]:
    rr = c.read_holding_registers(address=0, count=8, slave=slave)
    assert not rr.isError(), rr
    v = decode_registers(rr.registers, REGISTER_MAPS[typ])
    print(typ, {k: x for k, x in v.items()})
c.close()
