import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getFlightTrackCurrent } from '../api/flightTrack'
import type { FlightTrackCurrentDto } from '../api/types'
import { toMessage } from '../utils/displayFormatters'

export function useFlightTrack() {
  const current = ref<FlightTrackCurrentDto | null>(null)
  const loading = ref(false)
  const error = ref('')
  const autoRefresh = ref(true)

  let refreshTimer: number | undefined
  let refreshInFlight = false
  let requestSequence = 0

  onMounted(() => {
    void reload()
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })

  onBeforeUnmount(() => {
    stopTimer()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  async function reload(background = false): Promise<void> {
    if (refreshInFlight) return
    refreshInFlight = true
    const sequence = ++requestSequence
    if (!background && current.value === null) loading.value = true
    try {
      const result = await getFlightTrackCurrent()
      if (sequence !== requestSequence) return
      current.value = result ?? null
      error.value = ''
      scheduleNext(result?.pollIntervalSeconds)
    } catch (caught) {
      if (sequence === requestSequence) {
        error.value = toMessage(caught)
        scheduleNext(current.value?.pollIntervalSeconds)
      }
    } finally {
      if (sequence === requestSequence) {
        loading.value = false
        refreshInFlight = false
      }
    }
  }

  function toggleAutoRefresh(): void {
    autoRefresh.value = !autoRefresh.value
    if (autoRefresh.value) {
      scheduleNext(current.value?.pollIntervalSeconds)
      void reload(true)
    } else {
      stopTimer()
    }
  }

  function scheduleNext(intervalSeconds?: number | null): void {
    stopTimer()
    if (!autoRefresh.value || document.hidden) return
    const interval = Math.max(5, intervalSeconds ?? 5) * 1000
    refreshTimer = window.setInterval(() => {
      if (autoRefresh.value && !document.hidden) void reload(true)
    }, interval)
  }

  function stopTimer(): void {
    if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }

  function handleVisibilityChange(): void {
    if (document.hidden) {
      stopTimer()
      return
    }
    if (autoRefresh.value) {
      void reload(true)
    }
  }

  return {
    autoRefresh,
    current,
    error,
    loading,
    reload,
    toggleAutoRefresh,
  }
}
