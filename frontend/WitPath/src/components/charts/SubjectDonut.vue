<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

/** 学科分布环形图 */
const props = withDefaults(
  defineProps<{ items: Array<{ subject: string; minutes: number }>; height?: number }>(),
  { height: 220 },
)

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
const palette = ['#b9712c', '#4a6b8a', '#4e7d5b', '#8a6a48', '#6c6089', '#7a8f6d', '#a08a6a']

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption(
    {
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(255,255,255,0.96)',
        borderColor: 'rgba(33,29,23,0.1)',
        textStyle: { color: '#211d17', fontSize: 12 },
        formatter: (p: { name: string; value: number; percent: number }) =>
          `${p.name}<br/>${p.value} 分钟（${p.percent}%）`,
      },
      legend: {
        orient: 'vertical',
        right: 0,
        top: 'center',
        itemWidth: 8,
        itemHeight: 8,
        icon: 'circle',
        textStyle: { color: '#5f574c', fontSize: 12 },
      },
      series: [
        {
          type: 'pie',
          radius: ['52%', '76%'],
          center: ['34%', '50%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
          label: { show: false },
          data: props.items.map((item) => ({ name: item.subject, value: item.minutes })),
          color: palette,
        },
      ],
    },
    true,
  )
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
watch(() => props.items, render, { deep: true })
</script>

<template>
  <div ref="el" :style="{ height: `${height}px`, width: '100%' }" />
</template>
