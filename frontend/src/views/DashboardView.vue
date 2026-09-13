<template>
  <div>
    <!-- KPI -->
    <div class="kpi-grid">
      <div class="card kpi">
        <div class="label">园区总负荷</div>
        <div class="value accent-blue">{{ fmt(load?.power_kw) }}<small>kW</small></div>
        <div class="sub">园区总电表 meter-001</div>
      </div>
      <div class="card kpi">
        <div class="label">光伏出力</div>
        <div class="value accent-yellow">{{ fmt(pv?.active_power_kw) }}<small>kW</small></div>
        <div class="sub">辐照 {{ fmt(pv?.irradiance_wm2, 0) }} W/m²</div>
      </div>
      <div class="card kpi">
        <div class="label">PCS 功率（正放/负充）</div>
        <div class="value" :class="pcsPowerClass">{{ fmt(pcs?.p_kw) }}<small>kW</small></div>
        <div class="sub">{{ pcsStateText }}</div>
      </div>
      <div class="card kpi">
        <div class="label">电池 SOC</div>
        <div class="value accent-aqua">{{ fmt(bms?.soc, 1) }}<small>%</small></div>
        <div class="sub">SOH {{ fmt(bms?.soh, 1) }}% · {{ fmt(bms?.temp, 1) }}℃</div>
      </div>
      <div class="card kpi">
        <div class="label">当前分时电价</div>
        <div class="value accent-orange">{{ price.price ?? '—' }}<small>元/kWh</small></div>
        <div class="sub">
          <span v-if="price.available" :class="['badge', price.periodType]" style="padding:1px 8px">
            {{ periodLabel(price.periodType) }}</span>
        </div>
      </div>
    </div>

    <!-- 曲线 -->
    <div class="grid-2">
      <div class="card">
        <h3>负荷与光伏出力（实时滚动）</h3>
        <div ref="trendEl" class="chart"></div>
      </div>
      <div class="card">
        <h3>PCS 充放电功率（红=放电，蓝=充电）</h3>
        <div ref="pcsEl" class="chart"></div>
      </div>
    </div>

    <div class="grid-2" style="grid-template-columns: 320px 1fr">
      <div class="card">
        <h3>电池荷电状态 SOC</h3>
        <div ref="gaugeEl" class="chart" style="height:230px"></div>
        <div style="display:flex; justify-content:space-around; margin-top:4px">
          <div class="muted" style="font-size:12px">总压 <b style="color:var(--ink)">{{ fmt(bms?.voltage,1) }}</b> V</div>
          <div class="muted" style="font-size:12px">电流 <b style="color:var(--ink)">{{ fmt(bms?.current,1) }}</b> A</div>
          <div class="muted" style="font-size:12px">温度 <b style="color:var(--ink)">{{ fmt(bms?.temp,1) }}</b> ℃</div>
        </div>
      </div>
      <div class="card">
        <h3>设备实时遥测</h3>
        <table>
          <thead><tr>
            <th>设备</th><th>类型</th><th>关键量测</th>
            <th>质量</th><th class="num">更新时间</th>
          </tr></thead>
          <tbody>
            <tr v-for="d in devices" :key="d.deviceId">
              <td>{{ d.deviceId }}</td>
              <td class="muted">{{ d.deviceType }}</td>
              <td class="muted" style="max-width:340px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap">
                {{ summary(d) }}
              </td>
              <td><span :class="['badge', d.quality === 'GOOD' ? 'ok' : 'warn']">
                <span class="ic"></span>{{ d.quality }}
              </span></td>
              <td class="num muted">{{ clock(d.ts) }}</td>
            </tr>
            <tr v-if="!devices.length"><td colspan="5" class="empty">暂无数据，等待边缘采集器上送…</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from '../echarts'
import { api } from '../api'
import { baseGrid, legend, timeAxis, tooltip, valueAxis } from '../use-echarts'

const BLUE = '#3987e5', AQUA = '#199e70', YELLOW = '#c98500', RED = '#e66767'

// 模板 ref
const trendEl = ref(null)
const pcsEl = ref(null)
const gaugeEl = ref(null)

const devices = ref([])
const price = reactive({})
const buffer = []          // 滚动实时缓存
const MAX_POINTS = 300
let timer, priceTimer, trendChart, pcsChart, gaugeChart

const byId = computed(() => Object.fromEntries(devices.value.map((d) => [d.deviceId, d])))
const load = computed(() => byId.value['meter-001']?.values)
const pv = computed(() => byId.value['pv-001']?.values)
const bms = computed(() => byId.value['bms-001']?.values)
const pcs = computed(() => byId.value['pcs-001']?.values)
const pcsPowerClass = computed(() => {
  const p = pcs.value?.p_kw
  if (p == null) return ''
  return p < -0.5 ? 'charge' : p > 0.5 ? 'discharge' : ''
})
const pcsStateText = computed(() => pcs.value?.run_state_text || '待机')

const PERIOD = { VALLEY: '谷时', FLAT: '平时', PEAK: '峰时', SHOULDER: '尖峰' }
const periodLabel = (t) => PERIOD[t] || '—'
const fmt = (v, digits = 1) => (v == null ? '—' : Number(v).toFixed(digits))
const clock = (ts) => (ts ? new Date(ts).toLocaleTimeString('zh-CN', { hour12: false }) : '—')
const num = (v) => (v == null ? null : Number(v))
function summary(d) {
  return Object.entries(d.values || {})
    .filter(([k]) => !k.endsWith('_text'))
    .map(([k, val]) => `${k}=${typeof val === 'number' ? Number(val.toFixed(2)) : val}`)
    .join('  ')
}

function trendOption() {
  return {
    grid: baseGrid, legend, tooltip,
    xAxis: timeAxis(),
    yAxis: valueAxis('kW'),
    series: [
      {
        name: '总负荷', type: 'line', smooth: true, showSymbol: false,
        lineStyle: { width: 2, color: BLUE }, itemStyle: { color: BLUE },
        data: buffer.map((p) => [p.t, p.load])
      },
      {
        name: '光伏', type: 'line', smooth: true, showSymbol: false,
        lineStyle: { width: 2, color: YELLOW }, itemStyle: { color: YELLOW },
        areaStyle: { color: 'rgba(201,133,0,0.12)' },
        data: buffer.map((p) => [p.t, p.pv])
      }
    ]
  }
}

function pcsOption() {
  return {
    grid: { ...baseGrid, top: 24 },
    tooltip: { ...tooltip, valueFormatter: (v) => (v == null ? '—' : `${Number(v).toFixed(1)} kW`) },
    xAxis: timeAxis(),
    yAxis: valueAxis('kW'),
    // 发散色：放电>0 红，充电≤0 蓝
    visualMap: {
      show: false, dimension: 1, pieces: [
        { gt: 0, color: RED }, { lte: 0, color: BLUE }
      ]
    },
    series: [{
      name: 'PCS 功率', type: 'bar', barWidth: 3,
      data: buffer.map((p) => [p.t, p.pcs])
    }]
  }
}

function gaugeOption(soc) {
  return {
    series: [{
      type: 'gauge', min: 0, max: 100, startAngle: 210, endAngle: -30,
      radius: '92%', center: ['50%', '58%'],
      progress: { show: true, width: 14, roundCap: true, itemStyle: { color: AQUA } },
      axisLine: { lineStyle: { width: 14, color: [[1, '#2c2c2a']] } },
      axisTick: { show: false }, splitLine: { show: false },
      axisLabel: { color: '#898781', fontSize: 10, distance: 16 },
      pointer: { itemStyle: { color: AQUA }, length: '62%', width: 4 },
      anchor: { show: true, size: 10, itemStyle: { color: AQUA } },
      title: { offsetCenter: [0, '34%'], color: '#898781', fontSize: 12 },
      detail: {
        valueAnimation: true, offsetCenter: [0, '8%'],
        formatter: (v) => `{v|${v.toFixed(1)}%}`,
        rich: { v: { fontSize: 30, fontWeight: 700, color: '#fff' } }
      },
      data: [{ value: soc ?? 0, name: 'SOC' }]
    }]
  }
}

async function poll() {
  try {
    devices.value = await api.latest()
    buffer.push({
      t: Date.now(),
      load: num(load.value?.power_kw),
      pv: num(pv.value?.active_power_kw),
      pcs: num(pcs.value?.p_kw),
      soc: num(bms.value?.soc)
    })
    if (buffer.length > MAX_POINTS) buffer.shift()
    trendChart.setOption(trendOption())
    pcsChart.setOption(pcsOption())
    gaugeChart.setOption(gaugeOption(num(bms.value?.soc)))
  } catch { /* 后端未就绪时静默重试 */ }
}

function onResize() {
  trendChart?.resize(); pcsChart?.resize(); gaugeChart?.resize()
}

onMounted(async () => {
  trendChart = echarts.init(trendEl.value)
  pcsChart = echarts.init(pcsEl.value)
  gaugeChart = echarts.init(gaugeEl.value)
  window.addEventListener('resize', onResize)
  try { Object.assign(price, await api.currentPrice()) } catch { /* ignore */ }
  poll()
  timer = setInterval(poll, 2000)
  priceTimer = setInterval(async () => {
    try { Object.assign(price, await api.currentPrice()) } catch { /* ignore */ }
  }, 60000)
})

onBeforeUnmount(() => {
  clearInterval(timer); clearInterval(priceTimer)
  window.removeEventListener('resize', onResize)
  trendChart?.dispose(); pcsChart?.dispose(); gaugeChart?.dispose()
})
</script>
