<script setup lang="ts">
import type { TrafficOverviewDto, TrafficTotalsDto } from '../../api/types'
import type { MediaStatRow } from '../../composables/usePassengerRealtime'
import { barWidth, formatBytes, formatCount, formatMbps } from '../../utils/displayFormatters'

defineProps<{
  autoRefresh: boolean
  trafficOverview: TrafficOverviewDto | null
  trafficTotals: TrafficTotalsDto | null
  trafficLoading: boolean
  trafficError: string
  videoStats: MediaStatRow[]
  musicStats: MediaStatRow[]
  videoTerminalCount: number | null
  musicTerminalCount: number | null
  maxVideoMbps: number
  maxMusicMbps: number
}>()

const emit = defineEmits<{
  (event: 'toggleAutoRefresh'): void
}>()
</script>

<template>
  <section class="passenger-panel passenger-panel--traffic">
    <header class="panel-title">
      <span></span>
      <h2>乘客影音统计</h2>
    </header>

    <div class="panel-actions">
      <div>
        <strong>{{ trafficOverview?.task?.flightNo || '航班待同步' }}</strong>
        <small>{{ trafficOverview?.task?.scenarioName || '等待流量统计接口返回任务信息' }}</small>
      </div>
      <button class="mini-toggle" @click="emit('toggleAutoRefresh')">
        {{ autoRefresh ? '暂停刷新' : '恢复刷新' }}
      </button>
    </div>

    <section class="media-card">
      <div class="media-card__heading">
        <h3>视频：{{ formatCount(videoTerminalCount) }}台</h3>
        <span>{{ formatBytes(trafficTotals?.bytesCount) }}</span>
      </div>
      <div class="media-list">
        <div
          v-for="item in videoStats"
          :key="`video-${item.name}`"
          class="media-row"
          :class="{ 'is-empty': !item.hasData }"
        >
          <span>{{ item.name }}</span>
          <i><b :style="{ width: item.hasData ? barWidth(item.averageThroughputMbps || 0, maxVideoMbps) : '0%' }"></b></i>
          <em>{{ item.hasData ? formatMbps(item.averageThroughputMbps) : '—' }}</em>
        </div>
      </div>
      <div v-if="trafficLoading" class="media-state">读取流量统计中</div>
      <div v-else-if="trafficError" class="media-state media-state--error">{{ trafficError }}</div>
      <div v-else-if="!trafficOverview?.applicationStats.length" class="media-state">暂无流量统计数据</div>
    </section>

    <section class="media-card">
      <div class="media-card__heading">
        <h3>音乐：{{ formatCount(musicTerminalCount) }}台</h3>
        <span>{{ formatMbps(trafficTotals?.averageThroughputMbps) }}</span>
      </div>
      <div class="media-list">
        <div
          v-for="item in musicStats"
          :key="`music-${item.name}`"
          class="media-row"
          :class="{ 'is-empty': !item.hasData }"
        >
          <span>{{ item.name }}</span>
          <i><b :style="{ width: item.hasData ? barWidth(item.averageThroughputMbps || 0, maxMusicMbps) : '0%' }"></b></i>
          <em>{{ item.hasData ? formatMbps(item.averageThroughputMbps) : '—' }}</em>
        </div>
      </div>
      <div v-if="trafficLoading" class="media-state">读取流量统计中</div>
      <div v-else-if="trafficError" class="media-state media-state--error">{{ trafficError }}</div>
      <div v-else-if="!trafficOverview?.applicationStats.length" class="media-state">暂无流量统计数据</div>
    </section>

    <section class="cockpit-card">
      <div class="cockpit-card__heading">
        <h3>驾驶舱实时监控</h3>
        <span>视频流未接入</span>
      </div>
      <div class="cockpit-monitor">
        <span>驾驶舱监控待接入</span>
        <strong>不显示模拟视频内容</strong>
      </div>
    </section>
  </section>
</template>
