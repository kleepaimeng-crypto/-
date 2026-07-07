import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getPassengerSmartWindows } from '../api/smartWindows'
import { getTrafficOverview } from '../api/trafficStatistics'
import type {
  PassengerSmartWindowSnapshotDto,
  TrafficApplicationStatDto,
  TrafficOverviewDto,
} from '../api/types'
import { toMessage } from '../utils/displayFormatters'

export interface MediaStatRow {
  name: string
  averageThroughputMbps: number | null
  activeTerminalCount: number | null
  hasData: boolean
}

const VIDEO_CATEGORIES = ['爱情', '都市', '青春', '奇幻', '武侠', '古装', '科幻', '猎奇', '竞技', '传奇', '逆袭']
const MUSIC_CATEGORIES = ['流行', '摇滚', '民谣', '电子', '舞曲', '说唱', '轻音乐', '爵士', '乡村', 'R&B/Soul', '古典']
const VIDEO_KEYWORDS = ['视频', '电影', '影视', '剧', '动漫', '短片']
const MUSIC_KEYWORDS = ['音乐', '歌曲', '音频', '歌', 'r&b', 'soul', ...MUSIC_CATEGORIES.map((item) => item.toLowerCase())]

export function usePassengerRealtime() {
  const trafficOverview = ref<TrafficOverviewDto | null>(null)
  const windowDisplay = ref<PassengerSmartWindowSnapshotDto | null>(null)
  const trafficLoading = ref(false)
  const windowLoading = ref(false)
  const trafficError = ref('')
  const windowError = ref('')
  const autoRefresh = ref(true)
  const cabinScroller = ref<HTMLElement | null>(null)

  let refreshTimer: number | undefined
  let windowRequestInFlight = false

  const trafficTotals = computed(() => trafficOverview.value?.totals ?? null)
  const maxApplicationMbps = computed(() => {
    const values = trafficOverview.value?.applicationStats.map((item) => item.averageThroughputMbps) ?? []
    return values.length ? Math.max(...values) : 0
  })
  const videoStats = computed(() => buildMediaRows('video', trafficOverview.value?.applicationStats ?? []))
  const musicStats = computed(() => buildMediaRows('music', trafficOverview.value?.applicationStats ?? []))
  const videoTerminalCount = computed(() => sumMediaTerminals('video', trafficOverview.value?.applicationStats ?? []))
  const musicTerminalCount = computed(() => sumMediaTerminals('music', trafficOverview.value?.applicationStats ?? []))
  const maxVideoMbps = computed(() => maxMediaMbps(videoStats.value))
  const maxMusicMbps = computed(() => maxMediaMbps(musicStats.value))

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
    await Promise.all([loadTraffic(background), loadWindows(background)])
  }

  async function loadTraffic(background = false): Promise<void> {
    if (!background) trafficLoading.value = true
    trafficError.value = ''
    try {
      trafficOverview.value = await getTrafficOverview({ limit: 10 })
    } catch (error) {
      trafficError.value = toMessage(error)
    } finally {
      trafficLoading.value = false
    }
  }

  async function loadWindows(background = false): Promise<void> {
    if (windowRequestInFlight) return
    windowRequestInFlight = true
    if (!background && windowDisplay.value === null) windowLoading.value = true
    try {
      windowDisplay.value = await getPassengerSmartWindows()
      windowError.value = ''
    } catch (error) {
      windowError.value = toMessage(error)
    } finally {
      windowLoading.value = false
      windowRequestInFlight = false
    }
  }

  function toggleAutoRefresh(): void {
    autoRefresh.value = !autoRefresh.value
  }

  return {
    autoRefresh,
    cabinScroller,
    maxApplicationMbps,
    trafficError,
    trafficLoading,
    trafficOverview,
    trafficTotals,
    videoStats,
    videoTerminalCount,
    windowDisplay,
    windowError,
    windowLoading,
    maxMusicMbps,
    maxVideoMbps,
    musicStats,
    musicTerminalCount,
    toggleAutoRefresh,
  }
}

function buildMediaRows(kind: 'video' | 'music', stats: TrafficApplicationStatDto[]): MediaStatRow[] {
  const labels = kind === 'video' ? VIDEO_CATEGORIES : MUSIC_CATEGORIES
  const mediaStats = stats.filter((item) => mediaKind(item.application) === kind)
  const used = new Set<TrafficApplicationStatDto>()
  const rows = labels.map((label) => {
    const matched = mediaStats.find((item) => !used.has(item) && includesLabel(item.application, label))
    if (matched) {
      used.add(matched)
      return toMediaRow(label, matched)
    }
    return emptyMediaRow(label)
  })

  const emptyIndexes = rows
    .map((row, index) => (row.hasData ? -1 : index))
    .filter((index) => index >= 0)
  mediaStats
    .filter((item) => !used.has(item))
    .slice(0, emptyIndexes.length)
    .forEach((item, index) => {
      rows[emptyIndexes[index]] = toMediaRow(item.application, item)
    })

  return rows
}

function toMediaRow(name: string, stat: TrafficApplicationStatDto): MediaStatRow {
  return {
    name,
    averageThroughputMbps: stat.averageThroughputMbps,
    activeTerminalCount: stat.activeTerminalCount,
    hasData: true,
  }
}

function emptyMediaRow(name: string): MediaStatRow {
  return {
    name,
    averageThroughputMbps: null,
    activeTerminalCount: null,
    hasData: false,
  }
}

function mediaKind(application: string): 'video' | 'music' | null {
  const normalized = application.toLowerCase()
  if (MUSIC_KEYWORDS.some((keyword) => normalized.includes(keyword))) return 'music'
  if (VIDEO_KEYWORDS.some((keyword) => normalized.includes(keyword))) return 'video'
  return null
}

function includesLabel(application: string, label: string): boolean {
  return application.toLowerCase().includes(label.toLowerCase())
}

function sumMediaTerminals(kind: 'video' | 'music', stats: TrafficApplicationStatDto[]): number | null {
  const mediaStats = stats.filter((item) => mediaKind(item.application) === kind)
  if (mediaStats.length === 0) return null
  return mediaStats.reduce((sum, item) => sum + item.activeTerminalCount, 0)
}

function maxMediaMbps(rows: MediaStatRow[]): number {
  const values = rows
    .map((row) => row.averageThroughputMbps)
    .filter((value): value is number => value !== null)
  return values.length ? Math.max(...values) : 0
}
