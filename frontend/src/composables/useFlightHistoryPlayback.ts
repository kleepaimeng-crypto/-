import { computed, onBeforeUnmount, ref } from 'vue'
import type { FlightHistoryPointDto } from '../api/types'

const SPEEDS = [0.5, 1, 2, 4, 8] as const

export function useFlightHistoryPlayback() {
  const points = ref<FlightHistoryPointDto[]>([])
  const currentIndex = ref(0)
  const segmentProgress = ref(0)
  const playing = ref(false)
  const speed = ref<(typeof SPEEDS)[number]>(1)
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

  function setSpeed(value: (typeof SPEEDS)[number]): void {
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
  return { SPEEDS, currentIndex, currentPoint, flownPoints, load, playing, points, progress, reset, seek, setSpeed, speed, stop, toggle }
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
