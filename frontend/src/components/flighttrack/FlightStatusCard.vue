<script setup lang="ts">
import type { FlightTrackCurrentDto } from '../../api/types'
import { formatDate } from '../../utils/displayFormatters'

defineProps<{
  current: FlightTrackCurrentDto | null
}>()

function valueText(value: string | number | null | undefined, suffix = ''): string {
  if (value === null || value === undefined || value === '') return '--'
  return `${value}${suffix}`
}
</script>

<template>
  <section class="flight-panel flight-status-card">
    <header class="flight-panel__title">
      <span></span>
      <strong>飞行状态信息</strong>
    </header>

    <div v-if="current" class="flight-status-card__body">
      <div class="flight-number-line">
        <strong>{{ current.flight.flightNo }}</strong>
        <span>{{ current.flight.airlineName || current.flight.airlineCode || '--' }}</span>
        <em>{{ current.flight.aircraftRegistrationNo || '--' }}</em>
      </div>

      <div class="route-line">
        <div>
          <strong>{{ current.flight.originAirportCode }}</strong>
          <span>{{ current.flight.originAirportName }}</span>
        </div>
        <i>
          <b>{{ current.flight.statusText }}</b>
        </i>
        <div>
          <strong>{{ current.flight.destinationAirportCode }}</strong>
          <span>{{ current.flight.destinationAirportName }}</span>
        </div>
      </div>

      <dl class="flight-status-grid">
        <div><dt>机型</dt><dd>{{ current.flight.aircraftModel || '--' }}</dd></div>
        <div><dt>地速</dt><dd>{{ valueText(current.latestPoint.groundSpeedKt, ' kt') }}</dd></div>
        <div><dt>海拔高度</dt><dd>{{ valueText(current.latestPoint.altitudeFt, ' ft') }}</dd></div>
        <div><dt>剩余航程</dt><dd>{{ valueText(current.latestPoint.distanceToGoNm, ' NM') }}</dd></div>
        <div><dt>预计到达</dt><dd>{{ current.latestPoint.destinationEtaText || '--' }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ formatDate(current.flight.lastUpdatedAt) }}</dd></div>
      </dl>
    </div>

    <div v-else class="flight-status-empty">
      <strong>暂无活跃飞行</strong>
      <span>启动模拟器并等待 QAR 入库后显示当前航班。</span>
    </div>
  </section>
</template>
