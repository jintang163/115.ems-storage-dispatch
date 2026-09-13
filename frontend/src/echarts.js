// 按需引入 ECharts，tree-shaking 后体积约为全量的 1/3。
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, GaugeChart, CustomChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, VisualMapComponent
} from 'echarts/components'

use([
  CanvasRenderer,
  LineChart, BarChart, GaugeChart, CustomChart,
  GridComponent, TooltipComponent, LegendComponent, VisualMapComponent
])

export * from 'echarts/core'
