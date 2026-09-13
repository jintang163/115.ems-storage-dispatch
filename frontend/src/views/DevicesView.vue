<template>
  <div>
    <div class="toolbar">
      <div class="muted">共 {{ devices.length }} 台设备（电表 / 光伏 / BMS / PCS）</div>
      <button class="primary" @click="openCreate">＋ 新增设备</button>
    </div>

    <div class="card">
      <table>
        <thead><tr>
          <th>设备 ID</th><th>名称</th><th>类型</th><th>协议</th>
          <th>站点</th><th>接入点 / 主题</th><th>启用</th><th></th>
        </tr></thead>
        <tbody>
          <tr v-for="d in devices" :key="d.id">
            <td class="mono">{{ d.id }}</td>
            <td>{{ d.name }}</td>
            <td><span class="badge" :style="typeBadge(d.type)"><span class="ic"></span>{{ d.type }}</span></td>
            <td>{{ d.protocol }}</td>
            <td>{{ d.siteId }}</td>
            <td class="mono muted" style="font-size:12px">{{ d.endpoint }}</td>
            <td>
              <span :class="['badge', d.enabled ? 'ok' : 'off']">
                <span class="ic"></span>{{ d.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td class="row-actions">
              <button @click="openEdit(d)">编辑</button>
              <button class="danger" @click="remove(d)">删除</button>
            </td>
          </tr>
          <tr v-if="!devices.length"><td colspan="8" class="empty">暂无设备</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="editing" class="dialog-backdrop" @click.self="editing = null">
      <div class="card dialog">
        <h3>{{ editing.id ? '编辑设备' : '新增设备' }}</h3>
        <div class="form-grid">
          <div><label>设备 ID</label><input v-model="editing.id" :disabled="!!editing.id" placeholder="如 bms-002" /></div>
          <div><label>名称</label><input v-model="editing.name" /></div>
          <div><label>类型</label>
            <select v-model="editing.type">
              <option>METER</option><option>PV</option><option>BMS</option><option>PCS</option>
            </select>
          </div>
          <div><label>协议</label>
            <select v-model="editing.protocol"><option>MODBUS</option><option>MQTT</option></select>
          </div>
          <div><label>站点 ID</label><input v-model="editing.siteId" /></div>
          <div><label>接入点 / 主题</label><input v-model="editing.endpoint"
            placeholder="modbus://host:5020#3 或 devices/x/up" /></div>
        </div>
        <div class="row-actions">
          <button @click="editing = null">取消</button>
          <button class="primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

const devices = ref([])
const editing = ref(null)

const TYPE_COLOR = { METER: 'var(--s-blue)', PV: 'var(--s-yellow)', BMS: 'var(--s-aqua)', PCS: 'var(--s-red)' }
const typeBadge = (t) => ({ color: TYPE_COLOR[t] || 'var(--ink-2)' })

async function load() { devices.value = await api.devices() }
function openCreate() {
  editing.value = { id: '', name: '', type: 'METER', protocol: 'MODBUS', siteId: 'park-a', endpoint: '', enabled: true }
}
function openEdit(d) { editing.value = { ...d } }
async function save() {
  const d = editing.value
  if (!d.id?.trim() || !d.name?.trim()) { alert('设备 ID 与名称必填'); return }
  try {
    if (d.id) await api.updateDevice(d.id, d)
    else await api.createDevice(d)
    editing.value = null
    await load()
  } catch (e) { alert('保存失败：' + (e.response?.data?.error || e.message)) }
}
async function remove(d) {
  if (!confirm(`确认删除设备 ${d.id}？`)) return
  await api.deleteDevice(d.id)
  await load()
}
onMounted(load)
</script>
