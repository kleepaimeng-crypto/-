import { computed, onBeforeUnmount, ref } from 'vue'
import type { FlightHistoryPointDto } from '../api/types'

const SPEEDS = [0.5, 1, 2, 4, 8] as const

export function useFlightHistoryPlayback() {
  const points = ref<FlightHistoryPointDto[]>([])
  const currentIndex = ref(0)
  const playing = ref(false)
  const speed = ref<(typeof SPEEDS)[number]>(1)
  let timer: number | undefined

  const currentPoint = computed(() => points.value[currentIndex.value] ?? null)
  const flownPoints = computed(() => points.value.slice(0, currentIndex.value + 1))
  const progress = computed(() => points.value.length < 2 ? 0 : currentIndex.value / (points.value.length - 1))

  function load(nextPoints: FlightHistoryPointDto[]): void {
    stop()
    points.value = nextPoints
    currentIndex.value = 0
  }

  function toggle(): void {
    if (playing.value) stop()
    else play()
  }

  function play(): void {
    if (points.value.length < 2) return
    if (currentIndex.value >= points.value.length - 1) currentIndex.value = 0
    playing.value = true
    scheduleNext()
  }

  function stop(): void {
    playing.value = false
    if (timer !== undefined) window.clearTimeout(timer)
    timer = undefined
  }

  function reset(): void {
    stop()
    currentIndex.value = 0
  }

  function seek(value: number): void {
    stop()
    currentIndex.value = Math.max(0, Math.min(points.value.length - 1, Math.round(value * (points.value.length - 1))))
  }

  function setSpeed(value: (typeof SPEEDS)[number]): void {
    speed.value = value
    if (playing.value) {
      if (timer !== undefined) window.clearTimeout(timer)
      scheduleNext()
    }
  }

  function scheduleNext(): void {
    if (!playing.value || currentIndex.value >= points.value.length - 1) {
      stop()
      return
    }
    const current = points.value[currentIndex.value]
    const next = points.value[currentIndex.value + 1]
    const actualGap = Date.parse(next.sampleAt) - Date.parse(current.sampleAt)
    const delay = Number.isFinite(actualGap) ? Math.max(60, actualGap / speed.value) : 300
    timer = window.setTimeout(() => {
      currentIndex.value += 1
      scheduleNext()
    }, delay)
  }

  onBeforeUnmount(stop)
  return { SPEEDS, currentIndex, currentPoint, flownPoints, load, playing, points, progress, reset, seek, setSpeed, speed, stop, toggle }
}
