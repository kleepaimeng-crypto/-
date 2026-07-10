<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authSession } from '../auth/session'
import type { FlightTrackPointDto } from '../api/types'
import FlightChartPanel from '../components/flighttrack/FlightChartPanel.vue'
import FlightMapStage from '../components/flighttrack/FlightMapStage.vue'
import FlightStatusCard from '../components/flighttrack/FlightStatusCard.vue'
import PlatformBrand from '../components/PlatformBrand.vue'
import { useFlightTrack } from '../composables/useFlightTrack'
import { calculateFixedCanvasScale } from '../utils/fixedCanvas'

const router = useRouter()
const { autoRefresh, current, error, loading, reload, toggleAutoRefresh } = useFlightTrack()
const canvasScale = ref(1)
const mapStage = ref<InstanceType<typeof FlightMapStage> | null>(null)
const chartPoints = computed(() => latestRollingPoints(current.value?.track ?? [], 16))

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

function latestRollingPoints(points: FlightTrackPointDto[], maxCount: number): FlightTrackPointDto[] {
  const ordered = [...points].sort(comparePointTime)
  return ordered.slice(-maxCount)
}

function comparePointTime(left: FlightTrackPointDto, right: FlightTrackPointDto): number {
  const leftTime = Date.parse(left.sampleAt)
  const rightTime = Date.parse(right.sampleAt)
  if (Number.isFinite(leftTime) && Number.isFinite(rightTime) && leftTime !== rightTime) {
    return leftTime - rightTime
  }
  if (left.sampleAt !== right.sampleAt) return left.sampleAt.localeCompare(right.sampleAt)
  return left.frameCount - right.frameCount
}

function updateCanvasScale(): void {
  canvasScale.value = calculateFixedCanvasScale(window.innerWidth, window.innerHeight)
}

function locatePlane(): void {
  mapStage.value?.locatePlane()
}

async function logout(): Promise<void> {
  authSession.logout()
  await router.replace('/login')
}

onMounted(() => {
  updateCanvasScale()
  window.addEventListener('resize', updateCanvasScale)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateCanvasScale)
})
</script>

<template>
  <div class="flight-fixed-viewport">
    <main
      class="workspace-shell flight-shell"
      :style="{ transform: `translate(-50%, -50%) scale(${canvasScale})` }"
    >
      <header class="workspace-header">
        <PlatformBrand compact />
        <nav class="workspace-nav" aria-label="主导航">
          <button class="workspace-nav__item" @click="router.push('/')">数据管理</button>
          <button class="workspace-nav__item is-active">飞机轨迹实时系统</button>
          <button class="workspace-nav__item" disabled>飞机轨迹回放系统</button>
          <button class="workspace-nav__item" disabled>数据统计</button>
          <button
            v-if="authSession.state.user?.roleCode === 'SUPER_ADMIN'"
            class="workspace-nav__item"
            @click="router.push('/users')"
          >用户管理</button>
          <button class="workspace-nav__item" @click="router.push('/passenger-realtime')">乘客实时动态</button>
        </nav>
        <div class="workspace-header__account">
          <span class="account-dot"></span>
          <span>{{ authSession.state.user?.username }}</span>
          <button class="text-action" @click="logout">退出</button>
        </div>
      </header>

      <section class="flight-layout">
        <aside class="flight-left-stack">
          <FlightStatusCard :current="current" />
          <FlightChartPanel
            title="经纬度"
            left-label="纬度"
            right-label="经度"
            :points="chartPoints"
            :series="positionSeries"
          />
          <FlightChartPanel
            title="海拔高与地速"
            left-label="地速(kt)"
            right-label="海拔高(ft)"
            :points="chartPoints"
            :series="speedSeries"
          />
        </aside>

        <FlightMapStage ref="mapStage" :current="current" :loading="loading" :error="error" />

        <aside class="flight-right-stack">
          <FlightChartPanel
            title="航向角"
            :points="chartPoints"
            :series="headingSeries"
            :scale-padding="0.72"
          />
          <FlightChartPanel
            title="横滚角"
            :points="chartPoints"
            :series="rollSeries"
            :scale-padding="0.72"
            :axis-decimals="1"
          />
          <FlightChartPanel
            title="俯仰角"
            :points="chartPoints"
            :series="pitchSeries"
            :scale-padding="0.72"
            :axis-decimals="1"
          />
        </aside>

        <div class="flight-toolbar">
          <button
            class="flight-tool-button flight-tool-button--icon"
            type="button"
            title="定位飞机"
            aria-label="定位飞机并恢复地图跟随"
            @click="locatePlane"
          >
            ⌖
          </button>
          <button class="flight-tool-button" @click="toggleAutoRefresh">
            {{ autoRefresh ? '暂停刷新' : '恢复刷新' }}
          </button>
          <button class="flight-tool-button" :disabled="loading" @click="reload()">
            {{ loading ? '刷新中' : '立即刷新' }}
          </button>
        </div>
      </section>

      <footer class="workspace-footer">
        <span>部件号：XXXXXXXXXXXXXXXXX</span>
        <span>版本号：V0.1</span>
      </footer>
    </main>
  </div>
</template>
