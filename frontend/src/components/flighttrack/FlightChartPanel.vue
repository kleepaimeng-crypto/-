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

interface AxisScale {
  min: number
  max: number
  ticks: Array<{ value: number; y: number; text: string }>
}

const props = defineProps<{
  title: string
  leftLabel?: string
  rightLabel?: string
  points: FlightTrackPointDto[]
  series: ChartSeries[]
  scalePadding?: number
  axisDecimals?: number
}>()

const chart = computed(() => {
  const axisPadding = props.scalePadding ?? 0.12
  const width = 420
  const height = 246
  const left = 44
  const right = props.series.length > 1 ? 42 : 12
  const top = 12
  const bottom = 34
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const firstPointTime = firstFiniteTime(props.points)
  const lastPointTime = lastFiniteTime(props.points)
  const startTime = firstPointTime ?? 0
  const endTime = lastPointTime && lastPointTime > startTime ? lastPointTime : startTime + 1
  const primarySeries = props.series[0]
  const secondarySeries = props.series[1]
  const primaryScale = buildScale(valuesFor(primarySeries?.key), top, plotHeight, axisPadding)
  const secondaryScale = secondarySeries
    ? buildScale(valuesFor(secondarySeries.key), top, plotHeight, axisPadding)
    : primaryScale

  function xFor(point: FlightTrackPointDto): number {
    const pointTime = Date.parse(point.sampleAt)
    if (!Number.isFinite(pointTime)) return left
    return left + ((pointTime - startTime) / (endTime - startTime)) * plotWidth
  }

  function yFor(value: number, scale: AxisScale): number {
    return top + (1 - (value - scale.min) / (scale.max - scale.min)) * plotHeight
  }

  return {
    width,
    height,
    xLabelY: height - 10,
    plot: {
      left,
      right: width - right,
      top,
      bottom: top + plotHeight,
    },
    axes: {
      left: primaryScale,
      right: secondarySeries ? secondaryScale : null,
    },
    paths: props.series.map((serie, serieIndex) => {
      const scale = serieIndex === 1 ? secondaryScale : primaryScale
      const coords = props.points
        .map((point) => {
          const value = point[serie.key]
          if (typeof value !== 'number') return null
          return { x: xFor(point), y: yFor(value, scale), value }
        })
        .filter((item): item is { x: number; y: number; value: number } => item !== null)
      const commands = coords
        .map((coord, index) => `${index === 0 ? 'M' : 'L'} ${coord.x.toFixed(1)} ${coord.y.toFixed(1)}`)
        .join(' ')
      const end = coords.at(-1)
      return { ...serie, commands, end, points: coords }
    }),
    labels: timeLabels(props.points, xFor, 4, left + 10, width - right - 10),
  }
})

function valuesFor(key: PointKey | undefined): number[] {
  if (!key) return []
  return props.points
    .map((point) => point[key])
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
}

function buildScale(values: number[], top: number, plotHeight: number, paddingRatio: number): AxisScale {
  const rawMin = values.length ? Math.min(...values) : 0
  const rawMax = values.length ? Math.max(...values) : 1
  const spread = rawMax - rawMin || Math.max(Math.abs(rawMax) * 0.08, 1)
  const padding = spread * paddingRatio
  const min = rawMin - padding
  const max = rawMax + padding
  const tickCount = 4
  const ticks = Array.from({ length: tickCount }, (_, index) => {
    const value = max - ((max - min) * index) / (tickCount - 1)
    const y = top + (index / (tickCount - 1)) * plotHeight
    return { value, y, text: formatAxisNumber(value, props.axisDecimals) }
  })
  return { min, max, ticks }
}

function firstFiniteTime(points: FlightTrackPointDto[]): number | null {
  for (const point of points) {
    const time = Date.parse(point.sampleAt)
    if (Number.isFinite(time)) return time
  }
  return null
}

function lastFiniteTime(points: FlightTrackPointDto[]): number | null {
  for (let index = points.length - 1; index >= 0; index -= 1) {
    const time = Date.parse(points[index].sampleAt)
    if (Number.isFinite(time)) return time
  }
  return null
}

function timeLabels(
  points: FlightTrackPointDto[],
  xFor: (point: FlightTrackPointDto) => number,
  maxCount: number,
  minX: number,
  maxX: number,
): Array<{ text: string; x: number }> {
  if (points.length === 0) return []
  const step = Math.max(1, Math.ceil(points.length / maxCount))
  return points
    .filter((_, index) => index % step === 0)
    .map((point) => ({ text: compactTimeText(point.sampleTimeText), x: clamp(xFor(point), minX, maxX) }))
}

function compactTimeText(value: string): string {
  const parts = value.split(':')
  if (parts.length >= 2) return `${parts.at(-2)}:${parts.at(-1)}`
  return value
}

function formatAxisNumber(value: number, fixedDecimals?: number): string {
  if (fixedDecimals !== undefined) return value.toFixed(fixedDecimals)
  const absolute = Math.abs(value)
  if (absolute >= 1000) return value.toFixed(0)
  if (absolute >= 100) return value.toFixed(1)
  if (absolute >= 10) return value.toFixed(2)
  return value.toFixed(3)
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}
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
        <path
          v-for="tick in chart.axes.left.ticks"
          :key="`${title}-grid-${tick.text}`"
          class="chart-grid-line"
          :d="`M ${chart.plot.left} ${tick.y.toFixed(1)} H ${chart.plot.right}`"
        />
        <path
          class="chart-axis-line"
          :d="`M ${chart.plot.left} ${chart.plot.top} V ${chart.plot.bottom} H ${chart.plot.right}`"
        />
        <path
          v-if="chart.axes.right"
          class="chart-axis-line"
          :d="`M ${chart.plot.right} ${chart.plot.top} V ${chart.plot.bottom}`"
        />
        <text
          v-for="tick in chart.axes.left.ticks"
          :key="`${title}-left-${tick.text}`"
          class="chart-y-label chart-y-label--left"
          :x="chart.plot.left - 7"
          :y="tick.y + 3"
        >
          {{ tick.text }}
        </text>
        <text
          v-for="tick in chart.axes.right?.ticks ?? []"
          :key="`${title}-right-${tick.text}`"
          class="chart-y-label chart-y-label--right"
          :x="chart.plot.right + 7"
          :y="tick.y + 3"
        >
          {{ tick.text }}
        </text>
        <path
          v-for="path in chart.paths"
          :key="path.key"
          class="chart-series-line"
          :d="path.commands"
          :stroke="path.color"
        />
        <g v-for="path in chart.paths" :key="`${path.key}-points`">
          <circle
            v-for="(point, index) in path.points"
            :key="`${path.key}-${index}`"
            class="chart-series-point"
            r="2"
            :fill="path.color"
            :cx="point.x"
            :cy="point.y"
          />
        </g>
        <circle
          v-for="path in chart.paths"
          :key="`${path.key}-end`"
          v-show="path.end"
          r="3.5"
          :fill="path.color"
          :cx="path.end?.x || 0"
          :cy="path.end?.y || 0"
        />
        <text
          v-for="label in chart.labels"
          :key="`${title}-${label.text}-${label.x}`"
          class="chart-x-label"
          :x="label.x"
          :y="chart.xLabelY"
        >
          {{ label.text }}
        </text>
      </svg>
      <div v-if="points.length === 0" class="flight-chart__empty">等待 QAR 轨迹点</div>
    </div>
  </section>
</template>
