<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFlightHistorySessions, getFlightHistoryTrack } from '../api/flightHistory'
import type { FlightFinishReason, FlightHistorySessionDto } from '../api/types'
import { authSession } from '../auth/session'
import FlightChartPanel from '../components/flighttrack/FlightChartPanel.vue'
import FlightMapStage from '../components/flighttrack/FlightMapStage.vue'
import FlightStatusCard from '../components/flighttrack/FlightStatusCard.vue'
import PlatformBrand from '../components/PlatformBrand.vue'
import { useFlightHistoryPlayback } from '../composables/useFlightHistoryPlayback'
import { calculateFixedCanvasScale } from '../utils/fixedCanvas'
import { toMessage } from '../utils/displayFormatters'

const router = useRouter()
const canvasScale = ref(1)
const sessions = ref<FlightHistorySessionDto[]>([])
const selected = ref<FlightHistorySessionDto | null>(null)
const total = ref(0)
const dropdownOpen = ref(false)
const datePickerOpen = ref(false)
const listLoading = ref(false)
const trackLoading = ref(false)
const listError = ref('')
const trackError = ref('')
const query = reactive({
  flightNo: '',
  endedFrom: localDate(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)),
  endedTo: localDate(new Date()),
  finishReason: '' as '' | FlightFinishReason,
})
const playback = useFlightHistoryPlayback()
const mapStage = ref<InstanceType<typeof FlightMapStage> | null>(null)

const positionSeries = [
  { key: 'latitude' as const, label: '纬度', color: '#8279ff' },
  { key: 'longitude' as const, label: '经度', color: '#ff8d82' },
]
const speedSeries = [
  { key: 'groundSpeedKt' as const, label: '地速', color: '#8279ff' },
  { key: 'altitudeFt' as const, label: '海拔高', color: '#ff8d82' },
]
const headingSeries = [
  { key: 'trackAngleDeg' as const, label: '真航向角', color: '#8279ff' },
  { key: 'headingDeg' as const, label: '磁航向角', color: '#ff8d82' },
]
const rollSeries = [{ key: 'rollDeg' as const, label: '横滚量', color: '#8279ff' }]
const pitchSeries = [{ key: 'pitchDeg' as const, label: '俯仰量', color: '#8279ff' }]
const progressPercent = computed(() => Math.round(playback.progress.value * 1000) / 10)
const dateRangeLabel = computed(() => `${displayDate(query.endedFrom)} — ${displayDate(query.endedTo)}`)

async function loadSessions(): Promise<void> {
  listLoading.value = true
  listError.value = ''
  try {
    const result = await getFlightHistorySessions({
      endedFrom: toStartOfDayIso(query.endedFrom),
      endedTo: toEndOfDayIso(query.endedTo),
      flightNo: query.flightNo || undefined,
      finishReason: query.finishReason || undefined,
      page: 1,
      pageSize: 20,
      sortBy: 'endedAt',
      sortDirection: 'desc',
    })
    sessions.value = result.items
    total.value = result.total
    dropdownOpen.value = true
  } catch (error) {
    listError.value = toMessage(error)
  } finally {
    listLoading.value = false
  }
}

async function selectSession(session: FlightHistorySessionDto): Promise<void> {
  playback.stop()
  selected.value = session
  dropdownOpen.value = false
  trackLoading.value = true
  trackError.value = ''
  try {
    const result = await getFlightHistoryTrack(session.id)
    playback.load(result.track)
  } catch (error) {
    trackError.value = toMessage(error)
  } finally {
    trackLoading.value = false
  }
}

function updateCanvasScale(): void {
  canvasScale.value = calculateFixedCanvasScale(window.innerWidth, window.innerHeight)
}

function localDate(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

function displayDate(value: string): string {
  return value.replaceAll('-', '/')
}

function toStartOfDayIso(value: string): string {
  return `${value}T00:00:00+08:00`
}

function toEndOfDayIso(value: string): string {
  return `${value}T23:59:59.999+08:00`
}

function shiftDate(value: string, days: number): string {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day + days))
  return date.toISOString().slice(0, 10)
}

function handleDateChange(changed: 'from' | 'to'): void {
  if (query.endedTo < query.endedFrom) {
    if (changed === 'from') query.endedTo = query.endedFrom
    else query.endedFrom = query.endedTo
  }
  const rangeDays = Math.round((Date.parse(`${query.endedTo}T00:00:00+08:00`) - Date.parse(`${query.endedFrom}T00:00:00+08:00`)) / 86_400_000)
  if (rangeDays > 30) {
    if (changed === 'from') query.endedTo = shiftDate(query.endedFrom, 30)
    else query.endedFrom = shiftDate(query.endedTo, -30)
  }
  void loadSessions()
}

function reasonLabel(reason: FlightFinishReason): string {
  return { LANDED: '正常落地', TIMEOUT: '断流超时', NEW_FLIGHT: '新航班', FRAME_RESET: '帧号重置' }[reason]
}

function locatePlane(): void { mapStage.value?.locatePlane() }
async function logout(): Promise<void> { authSession.logout(); await router.replace('/login') }

onMounted(() => {
  updateCanvasScale()
  window.addEventListener('resize', updateCanvasScale)
  void loadSessions()
})
onBeforeUnmount(() => {
  playback.stop()
  window.removeEventListener('resize', updateCanvasScale)
})
</script>

<template>
  <div class="flight-fixed-viewport">
    <main class="workspace-shell flight-shell history-shell" :style="{ transform: `translate(-50%, -50%) scale(${canvasScale})` }">
      <header class="workspace-header">
        <PlatformBrand compact />
        <nav class="workspace-nav" aria-label="主导航">
          <button class="workspace-nav__item" @click="router.push('/')">数据管理</button>
          <button class="workspace-nav__item" @click="router.push('/flight-track')">飞机轨迹实时系统</button>
          <button class="workspace-nav__item is-active">飞机轨迹回放系统</button>
          <button class="workspace-nav__item" disabled>数据统计</button>
          <button v-if="authSession.state.user?.roleCode === 'SUPER_ADMIN'" class="workspace-nav__item" @click="router.push('/users')">用户管理</button>
          <button class="workspace-nav__item" @click="router.push('/passenger-realtime')">乘客实时动态</button>
        </nav>
        <div class="workspace-header__account"><span class="account-dot"></span><span>{{ authSession.state.user?.username }}</span><button class="text-action" @click="logout">退出</button></div>
      </header>

      <section class="flight-layout history-layout">
        <div class="history-filter-bar">
          <div class="history-flight-picker">
            <input v-model="query.flightNo" placeholder="航班号" @focus="dropdownOpen = true" @keyup.enter="loadSessions" />
            <button type="button" @click="loadSessions">查询</button>
            <section v-if="dropdownOpen" class="history-session-dropdown">
              <p v-if="listLoading">正在查询历史航段…</p>
              <p v-else-if="listError" class="is-error">{{ listError }}</p>
              <p v-else-if="sessions.length === 0">当前筛选条件下暂无历史航段</p>
              <button v-for="session in sessions" :key="session.id" type="button" class="history-session-option" @click="selectSession(session)">
                <strong>{{ session.flightNo }}</strong><span>{{ session.origin }} → {{ session.destination }}</span>
                <em>{{ reasonLabel(session.finishReason) }} · {{ session.pointCount }} 点</em>
              </button>
              <small v-if="total > sessions.length">仅显示前 {{ sessions.length }} / {{ total }} 条，请缩小筛选范围</small>
            </section>
          </div>
          <div class="history-date-filter">
            <button class="history-date-trigger" type="button" :aria-expanded="datePickerOpen" @click="datePickerOpen = !datePickerOpen">
              <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M7 2v3M17 2v3M3.5 9h17M5 4h14a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z" /></svg>
              <span>{{ dateRangeLabel }}</span><i>⌄</i>
            </button>
            <section v-if="datePickerOpen" class="history-date-popover" role="dialog" aria-label="选择历史查询日期范围">
              <div><strong>查询日期范围</strong><button type="button" @click="datePickerOpen = false">完成</button></div>
              <div class="history-date-picker">
                <label>开始日期<input v-model="query.endedFrom" type="date" :max="query.endedTo" @change="handleDateChange('from')" /></label>
                <span>—</span>
                <label>结束日期<input v-model="query.endedTo" type="date" :min="query.endedFrom" @change="handleDateChange('to')" /></label>
              </div>
              <p>最多查询连续 31 天，结束日期包含当天。</p>
            </section>
          </div>
          <select v-model="query.finishReason" aria-label="结束原因" @change="loadSessions"><option value="">全部结束原因</option><option value="LANDED">正常落地</option><option value="TIMEOUT">断流超时</option><option value="NEW_FLIGHT">新航班</option><option value="FRAME_RESET">帧号重置</option></select>
        </div>

        <aside class="flight-left-stack history-left-stack">
          <FlightStatusCard :history-session="selected" :history-point="playback.currentPoint.value" />
          <FlightChartPanel title="经纬度" left-label="纬度" right-label="经度" :points="playback.points.value" :series="positionSeries" :current-index="playback.currentIndex.value" />
          <FlightChartPanel title="海拔高与地速" left-label="地速(kt)" right-label="海拔高(ft)" :points="playback.points.value" :series="speedSeries" :current-index="playback.currentIndex.value" />
        </aside>

        <FlightMapStage ref="mapStage" :track="playback.flownPoints.value" :current-point="playback.currentPoint.value" :loading="trackLoading" :error="trackError" />

        <aside class="flight-right-stack history-right-stack">
          <FlightChartPanel title="航向角" :points="playback.points.value" :series="headingSeries" :current-index="playback.currentIndex.value" :scale-padding="0.72" />
          <FlightChartPanel title="横滚角" :points="playback.points.value" :series="rollSeries" :current-index="playback.currentIndex.value" :scale-padding="0.72" :axis-decimals="1" />
          <FlightChartPanel title="俯仰角" :points="playback.points.value" :series="pitchSeries" :current-index="playback.currentIndex.value" :scale-padding="0.72" :axis-decimals="1" />
        </aside>

        <div class="history-playback-bar">
          <button type="button" title="定位飞机" @click="locatePlane">⌖</button>
          <button type="button" @click="playback.reset">|◀</button>
          <button type="button" :disabled="playback.points.value.length < 2" @click="playback.toggle">{{ playback.playing.value ? '暂停' : '播放' }}</button>
          <input type="range" min="0" max="1" step="0.001" :value="playback.progress.value" :disabled="playback.points.value.length < 2" @input="playback.seek(Number(($event.target as HTMLInputElement).value))" />
          <span>{{ progressPercent }}%</span>
          <select :value="playback.speed.value" aria-label="回放倍速" @change="playback.setSpeed(Number(($event.target as HTMLSelectElement).value) as 0.5 | 1 | 2 | 4 | 8)"><option v-for="item in playback.SPEEDS" :key="item" :value="item">{{ item }}×</option></select>
        </div>
        <p v-if="trackError" class="history-track-error">{{ trackError }}</p>
      </section>
      <footer class="workspace-footer"><span>部件号：XXXXXXXXXXXXXXXXX</span><span>版本号：V0.1</span></footer>
    </main>
  </div>
</template>
