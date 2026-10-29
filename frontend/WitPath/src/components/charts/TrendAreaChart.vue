<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, MarkLineComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer])

/** 学习时长趋势（面积折线） */
const props = withDefaults(
  defineProps<{
    dates: string[]
    minutes: number[]
    height?: number
  }>(),
  { height: 220 },
)

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value, undefined, { renderer: 'canvas' })
  chart.setOption(
    {
      grid: { left: 8, right: 12, top: 16, bottom: 4, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255,255,255,0.96)',
        borderColor: 'rgba(33,29,23,0.1)',
        textStyle: { color: '#211d17', fontSize: 12 },
        axisPointer: { type: 'line', lineStyle: { color: 'rgba(185,113,44,0.35)' } },
      },
      xAxis: {
        type: 'category',
        data: props.dates.map((d) => d.slice(5)),
        boundaryGap: false,
        axisLine: { lineStyle: { color: 'rgba(33,29,23,0.12)' } },
        axisTick: { show: false },
        axisLabel: { color: '#968c7e', fontSize: 11, interval: Math.max(0, Math.floor(props.dates.length / 7)) },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: 'rgba(33,29,23,0.06)' } },
        axisLabel: { color: '#968c7e', fontSize: 11 },
      },
      series: [
        {
          type: 'line',
          smooth: 0.35,
          symbol: 'circle',
          symbolSize: 5,
          data: props.minutes,
          lineStyle: { width: 2, color: '#b9712c' },
          itemStyle: { color: '#b9712c' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(185,113,44,0.28)' },
              { offset: 1, color: 'rgba(185,113,44,0.02)' },
            ]),
          },
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
watch(() => [props.dates, props.minutes], render, { deep: true })
</script>

<template>
  <div ref="el" :style="{ height: `${height}px`, width: '100%' }" />
</template>
