import axios from 'axios'

const http = axios.create({ baseURL: '/api/v1', timeout: 8000 })

http.interceptors.response.use(
  (r) => r,
  (err) => {
    console.error(err.config?.url, err.response?.data || err.message)
    return Promise.reject(err)
  }
)

export const api = {
  latest: () => http.get('/telemetry/latest').then((r) => r.data),
  history: (deviceId, field, range = '-1h') =>
    http.get('/telemetry/history', { params: { deviceId, field, range } }).then((r) => r.data),
  devices: () => http.get('/devices').then((r) => r.data),
  createDevice: (d) => http.post('/devices', d).then((r) => r.data),
  updateDevice: (id, d) => http.put(`/devices/${id}`, d).then((r) => r.data),
  deleteDevice: (id) => http.delete(`/devices/${id}`),
  tariffs: () => http.get('/tariffs').then((r) => r.data),
  currentTariff: () => http.get('/tariffs/current').then((r) => r.data),
  currentPrice: () => http.get('/tariffs/current-price').then((r) => r.data),
  saveTariff: (t) =>
    t.id ? http.put(`/tariffs/${t.id}`, t).then((r) => r.data)
         : http.post('/tariffs', t).then((r) => r.data),
  deleteTariff: (id) => http.delete(`/tariffs/${id}`),
  collectorStatus: () => http.get('/collector/status').then((r) => r.data)
}
