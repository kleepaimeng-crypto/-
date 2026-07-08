import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getPassengerRealtimeSnapshot } from '../api/passengerRealtime'
import { getPassengerSmartWindows } from '../api/smartWindows'
import type {
  PassengerRealtimeSnapshotDto,
  PassengerSmartWindowSnapshotDto,
} from '../api/types'
import { toMessage } from '../utils/displayFormatters'

export function usePassengerRealtime() {
  const snapshot = ref<PassengerRealtimeSnapshotDto | null>(null)
  const windowDisplay = ref<PassengerSmartWindowSnapshotDto | null>(null)
  const snapshotLoading = ref(false)
  const windowLoading = ref(false)
  const snapshotError = ref('')
  const windowError = ref('')
  const autoRefresh = ref(true)
  const cabinScroller = ref<HTMLElement | null>(null)

  let refreshTimer: number | undefined
  let refreshInFlight = false
  let requestSequence = 0

  onMounted(() => {
    void reloadDashboard()
    refreshTimer = window.setInterval(() => {
      if (autoRefresh.value) void reloadDashboard(true)
    }, 5000)
  })

  onBeforeUnmount(() => {
    if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
  })

  async function reloadDashboard(background = false): Promise<void> {
    if (refreshInFlight) return
    refreshInFlight = true
    const sequence = ++requestSequence
    await Promise.all([loadSnapshot(sequence, background), loadWindows(sequence, background)])
    refreshInFlight = false
  }

  async function loadSnapshot(sequence: number, background: boolean): Promise<void> {
    if (!background && snapshot.value === null) snapshotLoading.value = true
    try {
      const result = await getPassengerRealtimeSnapshot()
      if (sequence !== requestSequence) return
      snapshot.value = result
      snapshotError.value = ''
    } catch (error) {
      if (sequence === requestSequence) snapshotError.value = toMessage(error)
    } finally {
      if (sequence === requestSequence) snapshotLoading.value = false
    }
  }

  async function loadWindows(sequence: number, background: boolean): Promise<void> {
    if (!background && windowDisplay.value === null) windowLoading.value = true
    try {
      const result = await getPassengerSmartWindows()
      if (sequence !== requestSequence) return
      windowDisplay.value = result
      windowError.value = ''
    } catch (error) {
      if (sequence === requestSequence) windowError.value = toMessage(error)
    } finally {
      if (sequence === requestSequence) windowLoading.value = false
    }
  }

  function toggleAutoRefresh(): void {
    autoRefresh.value = !autoRefresh.value
  }

  return {
    autoRefresh,
    cabinScroller,
    snapshot,
    snapshotError,
    snapshotLoading,
    toggleAutoRefresh,
    windowDisplay,
    windowError,
    windowLoading,
  }
}
