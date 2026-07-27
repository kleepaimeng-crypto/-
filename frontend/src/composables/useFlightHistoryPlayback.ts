import { computed, onBeforeUnmount, ref } from 'vue'
import type { FlightHistoryPointDto } from '../api/types'

const SPEEDS = [1, 2, 5, 10, 20] as const
const CHART_WINDOW_MS = 10 * 60 * 1000
const CHART_POINT_LIMITS: Record<PlaybackSpeed, number> = {
  1: 600,
  2: 300,
  5: 120,
  10: 60,
  20: 30,
}
const CHART_SAMPLE_INTERVALS_MS: Record<PlaybackSpeed, number> = {
  1: 1000,
  2: 2000,
  5: 5000,
  10: 10_000,
  20: 20_000,
}

type PlaybackSpeed = (typeof SPEEDS)[number]

export interface PlaybackChartDomain {
  startAtMs: number
  endAtMs: number
}

export function useFlightHistoryPlayback() {
  const points = ref<FlightHistoryPointDto[]>([])
  const currentIndex = ref(0)
  const segmentProgress = ref(0)
  const playing = ref(false)
  const speed = ref<PlaybackSpeed>(5)
  let timer: number | undefined

  const currentPoint = computed(() => {
    const current = points.value[currentIndex.value]
    const next = points.value[currentIndex.value + 1]
    if (!current || !next || segmentProgress.value === 0) return current ?? null
    return interpolatePoint(current, next, segmentProgress.value)
  })
  const flownPoints = computed(() => {
    const completed = points.value.slice(0, currentIndex.value + 1)
    const moving = currentPoint.value
    if (!moving || segmentProgress.value === 0) return completed
    return [...completed, moving]
  })
  const progress = computed(() => points.value.length < 2
    ? 0
    : (currentIndex.value + segmentProgress.value) / (points.value.length - 1))
  const playbackTimeMs = computed<number | null>(() => {
    const currentTime = pointTime(points.value[currentIndex.value])
    if (currentTime === null) return null
    const nextTime = pointTime(points.value[currentIndex.value + 1])
    if (nextTime === null || nextTime <= currentTime || segmentProgress.value === 0) {
      return currentTime
    }
    return currentTime + (nextTime - currentTime) * segmentProgress.value
  })
  const chartDomain = computed<PlaybackChartDomain | null>(() => {
    const firstTime = firstFiniteTime(points.value)
    const currentTime = playbackTimeMs.value
    if (firstTime === null || currentTime === null) return null
    const endAtMs = Math.max(firstTime + CHART_WINDOW_MS, currentTime)
    return {
      startAtMs: endAtMs - CHART_WINDOW_MS,
      endAtMs,
    }
  })
  const chartPoints = computed(() => {
    const playedPoints = points.value.slice(0, currentIndex.value + 1)
    const domain = chartDomain.value
    const firstTime = firstFiniteTime(points.value)
    const visiblePoints = domain
      ? playedPoints.filter((point) => {
        const time = pointTime(point)
        return time !== null && time >= domain.startAtMs && time <= domain.endAtMs
      })
      : playedPoints
    return samplePointsByTimeBuckets(
      visiblePoints,
      CHART_SAMPLE_INTERVALS_MS[speed.value],
      CHART_POINT_LIMITS[speed.value],
      firstTime ?? 0,
    )
  })

  function load(nextPoints: FlightHistoryPointDto[]): void {
    stop()
    points.value = nextPoints
    currentIndex.value = 0
    segmentProgress.value = 0
  }

  function toggle(): void {
    if (playing.value) stop()
    else play()
  }

  function play(): void {
    if (points.value.length < 2) return
    if (currentIndex.value >= points.value.length - 1) {
      currentIndex.value = 0
      segmentProgress.value = 0
    }
    playing.value = true
    scheduleSegment()
  }

  function stop(): void {
    playing.value = false
    if (timer !== undefined) window.clearTimeout(timer)
    timer = undefined
  }

  function reset(): void {
    stop()
    currentIndex.value = 0
    segmentProgress.value = 0
  }

  function seek(value: number): void {
    stop()
    currentIndex.value = Math.max(0, Math.min(points.value.length - 1, Math.round(value * (points.value.length - 1))))
    segmentProgress.value = 0
  }

  function setSpeed(value: PlaybackSpeed): void {
    speed.value = value
    if (playing.value) {
      if (timer !== undefined) window.clearTimeout(timer)
      scheduleSegment()
    }
  }

  function scheduleSegment(): void {
    if (!playing.value || currentIndex.value >= points.value.length - 1) {
      stop()
      return
    }
    const current = points.value[currentIndex.value]
    const next = points.value[currentIndex.value + 1]
    const actualGap = Date.parse(next.sampleAt) - Date.parse(current.sampleAt)
    const delay = Number.isFinite(actualGap) ? Math.max(60, actualGap / speed.value) : 300
    let elapsed = segmentProgress.value * delay
    let previousTickAt = Date.now()
    const tick = (): void => {
      if (!playing.value) return
      const now = Date.now()
      elapsed += Math.max(16, now - previousTickAt)
      previousTickAt = now
      segmentProgress.value = Math.min(1, elapsed / delay)
      if (segmentProgress.value < 1) {
        timer = window.setTimeout(tick, 16)
        return
      }
      currentIndex.value += 1
      segmentProgress.value = 0
      scheduleSegment()
    }
    timer = window.setTimeout(tick, 0)
  }

  onBeforeUnmount(stop)
  return {
    SPEEDS,
    chartDomain,
    chartPoints,
    currentIndex,
    currentPoint,
    flownPoints,
    load,
    playing,
    playbackTimeMs,
    points,
    progress,
    reset,
    seek,
    setSpeed,
    speed,
    stop,
    toggle,
  }
}

function samplePointsByTimeBuckets(
  points: FlightHistoryPointDto[],
  intervalMs: number,
  maxPoints: number,
  originTimeMs: number,
): FlightHistoryPointDto[] {
  const sampled: FlightHistoryPointDto[] = []
  let previousBucket: number | null = null
  for (const point of points) {
    const time = pointTime(point)
    if (time === null) continue
    const bucket = Math.floor((time - originTimeMs) / intervalMs)
    if (bucket === previousBucket) {
      if (sampled.length === 1) {
        sampled.push(point)
        continue
      }
      sampled[sampled.length - 1] = point
      continue
    }
    sampled.push(point)
    previousBucket = bucket
  }
  return sampled.length > maxPoints
    ? [sampled[0], ...sampled.slice(-(maxPoints - 1))]
    : sampled
}

function firstFiniteTime(points: FlightHistoryPointDto[]): number | null {
  for (const point of points) {
    const time = pointTime(point)
    if (time !== null) return time
  }
  return null
}

function pointTime(point: FlightHistoryPointDto | undefined): number | null {
  if (!point) return null
  const time = Date.parse(point.sampleAt)
  return Number.isFinite(time) ? time : null
}

function interpolatePoint(
  current: FlightHistoryPointDto,
  next: FlightHistoryPointDto,
  progress: number,
): FlightHistoryPointDto {
  return {
    ...current,
    latitude: interpolateNumber(current.latitude, next.latitude, progress),
    longitude: interpolateNumber(current.longitude, next.longitude, progress),
    altitudeFt: interpolateNumber(current.altitudeFt, next.altitudeFt, progress),
    groundSpeedKt: interpolateNumber(current.groundSpeedKt, next.groundSpeedKt, progress),
    computedAirSpeedKt: interpolateNumber(current.computedAirSpeedKt, next.computedAirSpeedKt, progress),
    trackAngleDeg: interpolateAngle(current.trackAngleDeg, next.trackAngleDeg, progress),
    headingDeg: interpolateAngle(current.headingDeg, next.headingDeg, progress),
    pitchDeg: interpolateNumber(current.pitchDeg, next.pitchDeg, progress),
    rollDeg: interpolateNumber(current.rollDeg, next.rollDeg, progress),
    distanceToGoNm: interpolateNumber(current.distanceToGoNm, next.distanceToGoNm, progress),
  }
}

function interpolateNumber(current: number | null, next: number | null, progress: number): number | null {
  if (current === null || next === null) return progress < 0.5 ? current : next
  return current + (next - current) * progress
}

function interpolateAngle(current: number | null, next: number | null, progress: number): number | null {
  if (current === null || next === null) return interpolateNumber(current, next, progress)
  const delta = ((next - current + 540) % 360) - 180
  return (current + delta * progress + 360) % 360
}
