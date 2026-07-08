<script setup lang="ts">
import { computed } from 'vue'
import type { PassengerRealtimeSnapshotDto } from '../../api/types'
import { barWidth, formatCount, formatDate } from '../../utils/displayFormatters'

const props = defineProps<{
  autoRefresh: boolean
  snapshot: PassengerRealtimeSnapshotDto | null
  loading: boolean
  error: string
}>()

const emit = defineEmits<{
  (event: 'toggleAutoRefresh'): void
}>()

const videoMax = computed(() => maxCount(props.snapshot?.mediaStatistics.videoRanking ?? []))
const musicMax = computed(() => maxCount(props.snapshot?.mediaStatistics.musicRanking ?? []))

function maxCount(items: { count: number }[]): number {
  return items.length > 0 ? Math.max(...items.map((item) => item.count)) : 0
}
</script>

<template>
  <section class="passenger-panel passenger-panel--traffic">
    <header class="panel-title">
      <span></span>
      <h2>乘客影音统计</h2>
    </header>

    <div class="panel-actions">
      <div>
        <strong>播放类型排行</strong>
        <small>更新时间：{{ formatDate(snapshot?.updatedAt) }}</small>
      </div>
      <button class="mini-toggle" @click="emit('toggleAutoRefresh')">
        {{ autoRefresh ? '暂停刷新' : '恢复刷新' }}
      </button>
    </div>

    <section class="media-card">
      <div class="media-card__heading">
        <h3>视频：{{ formatCount(snapshot?.mediaStatistics.videoTotalCount) }}次</h3>
      </div>
      <div class="media-list">
        <div
          v-for="item in snapshot?.mediaStatistics.videoRanking ?? []"
          :key="`video-${item.type}`"
          class="media-row"
        >
          <span>{{ item.type }}</span>
          <i><b :style="{ width: barWidth(item.count, videoMax) }"></b></i>
          <em>{{ formatCount(item.count) }}</em>
        </div>
      </div>
      <div v-if="loading && !snapshot" class="media-state">读取影音统计中</div>
      <div v-else-if="error && !snapshot" class="media-state media-state--error">{{ error }}</div>
      <div v-else-if="!snapshot?.mediaStatistics.videoRanking.length" class="media-state">暂无视频播放数据</div>
    </section>

    <section class="media-card">
      <div class="media-card__heading">
        <h3>音乐：{{ formatCount(snapshot?.mediaStatistics.musicTotalCount) }}次</h3>
      </div>
      <div class="media-list">
        <div
          v-for="item in snapshot?.mediaStatistics.musicRanking ?? []"
          :key="`music-${item.type}`"
          class="media-row"
        >
          <span>{{ item.type }}</span>
          <i><b :style="{ width: barWidth(item.count, musicMax) }"></b></i>
          <em>{{ formatCount(item.count) }}</em>
        </div>
      </div>
      <div v-if="loading && !snapshot" class="media-state">读取影音统计中</div>
      <div v-else-if="error && !snapshot" class="media-state media-state--error">{{ error }}</div>
      <div v-else-if="!snapshot?.mediaStatistics.musicRanking.length" class="media-state">暂无音乐播放数据</div>
    </section>

    <section class="cockpit-card">
      <div class="cockpit-card__heading">
        <h3>驾驶舱实时监控</h3>
        <span>视频流未接入</span>
      </div>
      <div class="cockpit-monitor">
        <span>驾驶舱监控待接入</span>
        <strong>暂不请求视频接口</strong>
      </div>
    </section>
  </section>
</template>
