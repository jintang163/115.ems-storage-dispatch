<template>
  <div>
    <div class="kpi-grid" style="grid-template-columns: repeat(4,1fr)">
      <div class="card kpi">
        <div class="label">MQTT Broker</div>
        <div class="value" :style="{color: s.brokerConnected ? 'var(--ok)' : 'var(--muted)', fontSize:'20px'}">
          {{ s.brokerConnected ? '● 已连接' : '○ 未连接' }}
        </div>
        <div class="sub">ems/+/telemetry 订阅</div>
      </div>
      <div class="card kpi">
        <div class="label">累计接收报文</div>
        <div class="value accent-blue" style="font-size:24px">{{ s.totalMessages ?? 0 }}</div>
        <div class="sub">含补传 QoS1</div>
      </div>
      <div class="card kpi">
        <div class="label">已接收 / 补传批次</div>
        <div class="value" style="font-size:24px">
          <span class="accent-aqua">{{ ing.acceptedBatches ?? 0 }}</span>
          <small style="color:var(--s-yellow)"> / {{ ing.replayBatches ?? 0 }}</small>
        </div>
        <div class="sub">补传批次（断线恢复）</div>
      </div>
      <div class="card kpi">
        <div class="label">重复丢弃 / 坏报文</div>
        <div class="value" style="font-size:24px">
          <span class="muted">{{ ing.duplicateBatches ?? 0 }}</span>
          <small style="color:var(--s-red)"> / {{ ing.badBatches ?? 0 }}</small>
        </div>
        <div class="sub">幂等去重 (gatewayId+seq)</div>
      </div>
    </div>

    <div class="grid-2" style="grid-template-columns: 1fr 1fr">
      <div class="card">
        <h3>边缘网关在线状态</h3>
        <table>
          <thead><tr>
            <th>网关</th><th>状态</th><th class="num">最新序号</th>
            <th>最近一批</th><th class="num">距今</th>
          </tr></thead>
          <tbody>
            <tr v-for="g in s.gateways || []" :key="g.gatewayId">
              <td class="mono">{{ g.gatewayId }}</td>
              <td><span :class="['badge', g.online ? 'ok' : 'off']">
                <span class="ic"></span>{{ g.online ? '在线' : '离线' }}
              </span></td>
              <td class="num">{{ g.lastSeq }}</td>
              <td>
                <span v-if="g.lastBatchBuffered" class="badge warn" style="padding:1px 8px">补传</span>
                <span v-else class="badge ok" style="padding:1px 8px">实时</span>
              </td>
              <td class="num muted">{{ g.ageSec }}s</td>
            </tr>
            <tr v-if="!(s.gateways || []).length"><td colspan="5" class="empty">暂无网关上送</td></tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <h3>断线缓存与补传链路</h3>
        <ol style="line-height:2;color:var(--ink-2); padding-left:20px; margin:6px 0">
          <li>边缘每秒轮询 Modbus（电表/光伏/BMS/PCS）并订阅 MQTT 直连设备。</li>
          <li>统一封装 ems.telemetry.v1 批次，<b>QoS 1</b> 发布到 ems/{site}/telemetry。</li>
          <li>Broker 不可用时批次写入边缘 <b>SQLite</b>（spool.db），序号持久化。</li>
          <li>恢复连接后按时间顺序补传，<code>buffered=true</code>；后端按 gatewayId+seq 幂等去重。</li>
          <li>MQTT 长期中断（默认 30s）自动切 <b>HTTP</b> <code>/api/v1/ingest/telemetry</code> 兜底。</li>
        </ol>
        <div class="muted" style="font-size:12px; margin-top:8px">
          验证：docker compose stop emqx → 采集器落盘 spool；start emqx → 自动 replay，计数见“补传批次”。
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive } from 'vue'
import { api } from '../api'

const s = reactive({})
const ing = reactive({})
let timer
async function refresh() {
  try {
    const st = await api.collectorStatus()
    Object.assign(s, st)
    Object.assign(ing, st.ingest || {})
  } catch { /* ignore */ }
}
onMounted(() => { refresh(); timer = setInterval(refresh, 2000) })
onUnmounted(() => clearInterval(timer))
</script>
