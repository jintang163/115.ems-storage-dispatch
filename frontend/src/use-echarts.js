// ECharts 深色主题下复用的轴 / 网格 / 提示 / 图例样式（recessive chrome）。
// 图表实例在各视图内通过 echarts.init 创建（见 ./echarts.js 按需注册）。

export const baseGrid = { left: 52, right: 18, top: 36, bottom: 40 }

export function timeAxis(extra = {}) {
  return {
    type: 'time',
    axisLine: { lineStyle: { color: '#383835' } },
    axisTick: { show: false },
    axisLabel: { color: '#898781', fontSize: 11 },
    splitLine: { show: false },
    ...extra
  }
}

export function valueAxis(name, extra = {}) {
  return {
    type: 'value',
    name,
    nameTextStyle: { color: '#898781', fontSize: 11, padding: [0, 0, 0, -20] },
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#898781', fontSize: 11 },
    splitLine: { lineStyle: { color: '#2c2c2a' } },
    ...extra
  }
}

export const tooltip = {
  trigger: 'axis',
  backgroundColor: '#222220',
  borderColor: 'rgba(255,255,255,0.12)',
  textStyle: { color: '#fff', fontSize: 12 },
  axisPointer: { lineStyle: { color: '#555' } }
}

export const legend = {
  top: 4, right: 8, icon: 'roundRect', itemWidth: 12, itemHeight: 3,
  textStyle: { color: '#c3c2b7', fontSize: 12 }
}
