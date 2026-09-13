"""端到端联调用订阅器：打印收到的批次摘要，写到 /tmp/sub.log。"""
import json
import sys

import paho.mqtt.client as mqtt

got = {"n": 0}


def on_connect(c, u, flags, reason, props=None):
    c.subscribe("ems/+/telemetry", qos=1)
    print("subscribed", flush=True)


def on_message(c, u, msg):
    b = json.loads(msg.payload)
    got["n"] += 1
    pts = {p["deviceId"]: p.get("values", {}) for p in b["points"]}
    print(json.dumps({
        "seq": b["seq"], "buffered": b["buffered"], "gw": b["gatewayId"],
        "nPoints": len(b["points"]),
        "soc": pts.get("bms-001", {}).get("soc"),
        "pcs_p": pts.get("pcs-001", {}).get("p_kw"),
        "pcs_state": pts.get("pcs-001", {}).get("run_state_text"),
        "load": pts.get("meter-001", {}).get("power_kw"),
        "pv": pts.get("pv-001", {}).get("active_power_kw"),
    }, ensure_ascii=False), flush=True)
    if got["n"] >= int(sys.argv[1] if len(sys.argv) > 1 else 3):
        c.disconnect()


cli = mqtt.Client(callback_api_version=mqtt.CallbackAPIVersion.VERSION2, client_id="e2e-sub")
cli.on_connect = on_connect
cli.on_message = on_message
cli.connect("127.0.0.1", 1883)
cli.loop_forever()
