<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        EMS 储能调度
        <small>ENERGY STORAGE</small>
      </div>
      <RouterLink class="nav-link" to="/dashboard"><span class="dot" style="color:var(--s-blue)"></span>实时监控</RouterLink>
      <RouterLink class="nav-link" to="/devices"><span class="dot" style="color:var(--s-aqua)"></span>设备管理</RouterLink>
      <RouterLink class="nav-link" to="/tariff"><span class="dot" style="color:var(--s-yellow)"></span>分时电价</RouterLink>
      <RouterLink class="nav-link" to="/collector"><span class="dot" style="color:var(--s-violet)"></span>采集状态</RouterLink>
    </aside>

    <div class="main">
      <header class="topbar">
        <h1>{{ $route.meta.title }}</h1>
        <div style="display:flex; align-items:center; gap:16px;">
          <span v-if="price.available" class="badge" :class="price.periodType">
            <span class="ic"></span>{{ periodLabel(price.periodType) }}
            <b style="margin-left:4px">{{ Number(price.price).toFixed(2) }}</b>
            <span class="muted">元/kWh</span>
          </span>
          <span class="badge" :class="status.brokerConnected ? 'ok' : 'off'">
            <span class="ic"></span>{{ status.brokerConnected ? 'MQTT 已连接' : 'MQTT 未连接' }}
          </span>
        </div>
      </header>
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive } from 'vue'
import { api } from './api'

const status = reactive({ brokerConnected: false })
const price = reactive({ available: false })
let timer

const PERIOD = { VALLEY: '谷时', FLAT: '平时', PEAK: '峰时', SHOULDER: '尖峰' }
const periodLabel = (t) => PERIOD[t] || t

async function refresh() {
  try { Object.assign(status, await api.collectorStatus()) } catch { /* 后端未启动时静默 */ }
  try { Object.assign(price, await api.currentPrice()) } catch { /* ignore */ }
}
onMounted(() => { refresh(); timer = setInterval(refresh, 3000) })
onUnmounted(() => clearInterval(timer))
</script>
