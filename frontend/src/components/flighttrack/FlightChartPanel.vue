<script setup lang="ts">
import { computed } from 'vue'
import type { FlightTrackPointDto } from '../../api/types'

type PointKey = keyof Pick<
  FlightTrackPointDto,
  'latitude' | 'longitude' | 'altitudeFt' | 'groundSpeedKt' | 'trackAngleDeg' | 'headingDeg' | 'pitchDeg' | 'rollDeg'
>

interface ChartSeries {
  key: PointKey
  label: string
  color: string
}

const props = defineProps<{
  title: string
  leftLabel?: string
  rightLabel?: string
  points: FlightTrackPointDto[]
  series: ChartSeries[]
}>()

const chart = computed(() => {
  const values = props.series.flatMap((serie) => props.points
    .map((point, index) => ({ value: point[serie.key], index }))
    .filter((item): item is { value: number; index: number } => typeof item.value === 'number'))
  const rawMin = values.length ? Math.min(...values.map((item) => item.value)) : 0
  const rawMax = values.length ? Math.max(...values.map((item) => item.value)) : 1
  const spread = rawMax - rawMin || 1
  const min = rawMin - spread * 0.12
  const max = rawMax + spread * 0.12
  const width = 360
  const height = 172
  const left = 26
  const right = 10
  const top = 12
  const bottom = 28
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const lastIndex = Math.max(1, props.points.length - 1)

  return {
    width,
    height,
    paths: props.series.map((serie) => {
      const coords = props.points
        .map((point, index) => {
          const value = point[serie.key]
          if (typeof value !== 'number') return null
          const x = left + (index / lastIndex) * plotWidth
          const y = top + (1 - (value - min) / (max - min)) * plotHeight
          return { x, y }
        })
        .filter((item): item is { x: number; y: number } => item !== null)
      const commands = coords
        .map((coord, index) => `${index === 0 ? 'M' : 'L'} ${coord.x.toFixed(1)} ${coord.y.toFixed(1)}`)
        .join(' ')
      const end = coords.at(-1)
      return { ...serie, commands, end }
    }),
    labels: props.points.map((point, index) => ({
      text: point.sampleTimeText,
      x: left + (index / lastIndex) * plotWidth,
    })).filter((_, index) => index % Math.max(1, Math.ceil(props.points.length / 8)) === 0),
  }
})
</script>

<template>
  <section class="flight-panel flight-chart">
    <header class="flight-panel__title">
      <span></span>
      <strong>{{ title }}</strong>
      <div class="flight-chart__legend">
        <i v-for="item in series" :key="item.key" :style="{ color: item.color }">{{ item.label }}</i>
      </div>
    </header>
    <div class="flight-chart__body">
      <span class="flight-chart__axis flight-chart__axis--left">{{ leftLabel }}</span>
      <span class="flight-chart__axis flight-chart__axis--right">{{ rightLabel }}</span>
      <svg :viewBox="`0 0 ${chart.width} ${chart.height}`" role="img" :aria-label="title">
        <path v-for="line in [36, 72, 108, 144]" :key="line" class="chart-grid-line" :d="`M 26 ${line} H 350`" />
        <path
          v-for="path in chart.paths"
          :key="path.key"
          class="chart-series-line"
          :d="path.commands"
          :stroke="path.color"
        />
        <circle
          v-for="path in chart.paths"
          :key="`${path.key}-end`"
          v-show="path.end"
          r="3.5"
          :fill="path.color"
          :cx="path.end?.x || 0"
          :cy="path.end?.y || 0"
        />
        <text v-for="label in chart.labels" :key="`${title}-${label.text}-${label.x}`" :x="label.x" y="164">
          {{ label.text }}
        </text>
      </svg>
      <div v-if="points.length === 0" class="flight-chart__empty">等待 QAR 轨迹点</div>
    </div>
  </section>
</template>
