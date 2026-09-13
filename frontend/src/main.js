import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import Dashboard from './views/DashboardView.vue'
import DevicesView from './views/DevicesView.vue'
import TariffView from './views/TariffView.vue'
import CollectorView from './views/CollectorView.vue'
import './styles.css'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: Dashboard, meta: { title: '实时监控' } },
    { path: '/devices', component: DevicesView, meta: { title: '设备管理' } },
    { path: '/tariff', component: TariffView, meta: { title: '分时电价' } },
    { path: '/collector', component: CollectorView, meta: { title: '采集状态' } }
  ]
})

createApp(App).use(router).mount('#app')
