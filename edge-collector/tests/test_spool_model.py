from ems_collector.model import BatchEnvelope, Point, utc_now_iso
from ems_collector.spool import Spool


def test_envelope_roundtrip():
    b = BatchEnvelope(
        siteId="park-a", gatewayId="gw-001", seq=1, sentAt=utc_now_iso(),
        points=[Point("bms-001", "BMS", utc_now_iso(), {"soc": 62.4})],
    )
    raw = b.to_json()
    b2 = BatchEnvelope.from_json(raw)
    assert b2.protocol == "ems.telemetry.v1"
    assert b2.seq == 1
    assert b2.points[0].values["soc"] == 62.4
    assert b2.points[0].deviceType == "BMS"


def test_spool_fifo_and_delete():
    s = Spool(":memory:")
    for seq in range(1, 6):
        s.put(seq, f'{{"seq":{seq}}}')
    assert s.size() == 5
    batch = s.fetch_oldest(3)
    assert [b[1] for b in batch] == [1, 2, 3]
    s.delete(batch[0][0])
    remaining = [b[1] for b in s.fetch_oldest(10)]
    assert remaining == [2, 3, 4, 5]


def test_spool_seq_monotonic_persistent(tmp_path):
    path = str(tmp_path / "spool.db")
    s1 = Spool(path)
    assert s1.next_seq() == 1
    assert s1.next_seq() == 2
    s1.put(99, '{}')
    s1.close()
    # 重新打开同一文件：序号不回退，缓存不丢
    s2 = Spool(path)
    assert s2.next_seq() == 3
    assert s2.size() == 1
