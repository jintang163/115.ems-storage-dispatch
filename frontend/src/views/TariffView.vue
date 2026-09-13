<template>
  <div>
    <div class="toolbar">
      <div class="muted">分时电价（TOU）：手动配置；配置电力公司接口 URL 后可由后端定时拉取。</div>
      <button class="primary" @click="openCreate">＋ 新建电价方案</button>
    </div>

    <div class="card" style="margin-bottom:14px">
      <table>
        <thead><tr>
          <th>ID</th><th>方案名称</th><th>来源</th><th>生效区间</th>
          <th class="num">时段数</th><th></th>
        </tr></thead>
        <tbody>
          <tr v-for="t in tariffs" :key="t.id">
            <td class="mono">{{ t.id }}</td>
            <td>{{ t.name }}</td>
            <td><span class="badge" :class="t.source === 'REMOTE' ? 'warn' : 'ok'">
              <span class="ic"></span>{{ t.source === 'REMOTE' ? '电力公司接口' : '手动配置' }}
            </span></td>
            <td class="muted">{{ t.effectiveFrom }} ~ {{ t.effectiveTo }}</td>
            <td class="num">{{ t.periods?.length || 0 }}</td>
            <td class="row-actions">
              <button @click="openEdit(t)">编辑时段</button>
              <button class="danger" @click="remove(t)">删除</button>
            </td>
          </tr>
          <tr v-if="!tariffs.length"><td colspan="6" class="empty">暂无电价方案</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="editing" class="dialog-backdrop" @click.self="editing = null">
      <div class="card dialog">
        <h3>{{ editing.id ? '编辑电价方案' : '新建电价方案' }}</h3>
        <div class="form-grid">
          <div style="grid-column:1/3"><label>方案名称</label><input v-model="editing.name" /></div>
          <div><label>生效起</label><input type="date" v-model="editing.effectiveFrom" /></div>
          <div><label>生效止</label><input type="date" v-model="editing.effectiveTo" /></div>
        </div>

        <div ref="timelineEl" style="height:90px; margin:6px 0 14px"></div>

        <table>
          <thead><tr>
            <th>时段类型</th><th>开始</th><th>结束</th><th class="num">电价(元/kWh)</th><th></th>
          </tr></thead>
          <tbody>
            <tr v-for="(p, i) in editing.periods" :key="i">
              <td>
                <select v-model="p.periodType">
                  <option value="VALLEY">谷 VALLEY</option>
                  <option value="FLAT">平 FLAT</option>
                  <option value="SHOULDER">尖 SHOULDER</option>
                  <option value="PEAK">峰 PEAK</option>
                </select>
              </td>
              <td><input type="time" :value="toInput(p.startMinute)"
                  @input="p.startMinute = hhmmToMinute($event.target.value)" /></td>
              <td><input type="time" :value="toInput(p.endMinute)"
                  @input="p.endMinute = hhmmToMinute($event.target.value)" /></td>
              <td class="num"><input type="number" step="0.01" v-model.number="p.price" style="text-align:right" /></td>
              <td class="row-actions"><button class="danger" @click="editing.periods.splice(i,1)">删</button></td>
            </tr>
          </tbody>
        </table>
        <div style="margin-top:10px"><button @click="addPeriod">＋ 添加时段</button></div>

        <div class="row-actions" style="margin-top:16px">
          <button @click="editing = null">取消</button>
          <button class="primary" @click="save">保存方案</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import * as echarts from '../echarts'
import { api } from '../api'

const tariffs = ref([])
const editing = ref(null)
const timelineEl = ref(null)
let timeline

const TYPE_COLOR = { VALLEY: '#199e70', FLAT: '#898781', PEAK: '#e66767', SHOULDER: '#c98500' }
const TYPE_NAME = { VALLEY: '谷', FLAT: '平', PEAK: '峰', SHOULDER: '尖' }

async function load() { tariffs.value = await api.tariffs() }
function openCreate() {
  editing.value = {
    name: '', source: 'MANUAL', effectiveFrom: '2026-01-01', effectiveTo: '2026-12-31',
    periods: [{ periodType: 'VALLEY', startMinute: 0, endMinute: 420, price: 0.32 }]
  }
}
function openEdit(t) { editing.value = JSON.parse(JSON.stringify(t)) }
function addPeriod() {
  const last = editing.value.periods.at(-1)
  editing.value.periods.push({
    periodType: 'FLAT',
    startMinute: last ? last.endMinute : 0,
    endMinute: last ? Math.min(1440, last.endMinute + 120) : 120,
    price: 0.78
  })
}
async function save() {
  const t = editing.value
  if (!t.name?.trim()) { alert('方案名称必填'); return }
  if (!t.periods.length) { alert('至少配置一个时段'); return }
  await api.saveTariff(t)
  editing.value = null
  await load()
}
async function remove(t) {
  if (!confirm(`删除电价方案「${t.name}」？`)) return
  await api.deleteTariff(t.id)
  await load()
}

const hhmmToMinute = (s) => { if (!s) return 0; const [h, m] = s.split(':').map(Number); return h * 60 + m }
const minuteToHHMM = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
// HTML time 控件最大 23:59；内部 1440(=24:00 次日零点) 仅显示钳制，模型不变
const toInput = (m) => (m >= 1440 ? '23:59' : minuteToHHMM(m))

function renderTimeline() {
  if (!timeline) timeline = echarts.init(timelineEl.value)
  const periods = editing.value.periods
  // 跨零点时段展开为两段绘制
  const rects = []
  for (const p of periods) {
    if (p.startMinute <= p.endMinute) {
      rects.push({ start: p.startMinute, end: p.endMinute, type: p.periodType })
    } else {
      rects.push({ start: p.startMinute, end: 1440, type: p.periodType })
      rects.push({ start: 0, end: p.endMinute, type: p.periodType })
    }
  }
  timeline.setOption({
    tooltip: {
      backgroundColor: '#222220', borderColor: 'rgba(255,255,255,.12)',
      textStyle: { color: '#fff', fontSize: 12 },
      formatter: (p) => {
        const d = p.data
        return `${TYPE_NAME[d.type]}时 ${minuteToHHMM(d.start)}–${minuteToHHMM(d.end)}`
      }
    },
    grid: { left: 8, right: 12, top: 10, bottom: 28 },
    xAxis: {
      type: 'value', min: 0, max: 1440, interval: 180,
      axisLine: { lineStyle: { color: '#383835' } },
      axisTick: { show: false },
      axisLabel: { color: '#898781', fontSize: 10, formatter: (v) => minuteToHHMM(v) },
      splitLine: { lineStyle: { color: '#2c2c2a' } }
    },
    yAxis: { show: false, min: 0, max: 1 },
    series: [{
      type: 'custom',
      renderItem: (params, api) => {
        const type = rects[params.dataIndex].type
        const start = api.coord([api.value(0), 0.15])
        const end = api.coord([api.value(1), 0.85])
        return {
          type: 'rect',
          shape: { x: start[0], y: start[1], width: Math.max(2, end[0] - start[0] - 2), height: end[1] - start[1], r: 3 },
          style: { fill: TYPE_COLOR[type] || '#888' }
        }
      },
      encode: { x: [0, 1] },
      data: rects.map((r) => ({ value: [r.start, r.end], start: r.start, end: r.end, type: r.type }))
    }]
  })
}

watch(editing, async (v) => {
  if (v) { await nextTick(); renderTimeline() }
}, { deep: true })

onMounted(load)
</script>
