<script setup lang="ts">
import { computed } from 'vue'
import type { FlightHistorySessionDto, FlightTelemetryPointDto, FlightTrackCurrentDto } from '../../api/types'
import { formatDate } from '../../utils/displayFormatters'

const props = defineProps<{
  current?: FlightTrackCurrentDto | null
  historySession?: FlightHistorySessionDto | null
  historyPoint?: FlightTelemetryPointDto | null
}>()

const AIRPORT_NAMES: Record<string, string> = {
  ZBAA: '北京首都国际机场',
  ZSPD: '上海浦东国际机场',
  ZGGG: '广州白云国际机场',
  ZUUU: '成都双流国际机场',
  ZSHC: '杭州萧山国际机场',
}
const AIRLINE_NAMES: Record<string, string> = {
  CA: '中国国际航空',
  MU: '中国东方航空',
  CZ: '中国南方航空',
  HU: '海南航空',
  MF: '厦门航空',
}

const display = computed(() => {
  if (props.current) return { flight: props.current.flight, point: props.current.latestPoint }
  if (!props.historySession || !props.historyPoint) return null
  return {
    flight: {
      flightNo: props.historySession.flightNo,
      airlineCode: props.historySession.airlineCode,
      airlineName: displayName(AIRLINE_NAMES, props.historySession.airlineCode),
      aircraftRegistrationNo: props.historySession.aircraftRegistrationNo,
      aircraftModel: props.historySession.aircraftModel,
      originAirportCode: props.historySession.origin,
      originAirportName: displayName(AIRPORT_NAMES, props.historySession.origin),
      destinationAirportCode: props.historySession.destination,
      destinationAirportName: displayName(AIRPORT_NAMES, props.historySession.destination),
      statusText: '历史回放',
      lastUpdatedAt: props.historyPoint.sampleAt,
    },
    point: props.historyPoint,
  }
})

function valueText(value: string | number | null | undefined, suffix = ''): string {
  if (value === null || value === undefined || value === '') return '--'
  return `${value}${suffix}`
}

function displayName(names: Record<string, string>, code: string | null): string {
  if (!code) return '--'
  return names[code] || code
}
</script>

<template>
  <div class="flight-panel-group flight-status-panel">
    <header class="flight-panel__title">
      <strong>飞行状态信息</strong>
    </header>

    <section class="flight-panel flight-status-card">
    <div v-if="display" class="flight-status-card__body">
      <div class="flight-number-line">
        <strong>{{ display.flight.flightNo }}</strong>
        <span>{{ display.flight.airlineName || display.flight.airlineCode || '--' }}</span>
        <em>{{ display.flight.aircraftRegistrationNo || '--' }}</em>
      </div>

      <div class="route-line">
        <div>
          <strong>{{ display.flight.originAirportCode }}</strong>
          <span>{{ display.flight.originAirportName }}</span>
        </div>
        <i>
          <b>{{ display.flight.statusText }}</b>
        </i>
        <div>
          <strong>{{ display.flight.destinationAirportCode }}</strong>
          <span>{{ display.flight.destinationAirportName }}</span>
        </div>
      </div>

      <dl class="flight-status-grid">
        <div><dt>机型</dt><dd>{{ display.flight.aircraftModel || '--' }}</dd></div>
        <div><dt>地速</dt><dd>{{ valueText(display.point.groundSpeedKt, ' kt') }}</dd></div>
        <div><dt>海拔高度</dt><dd>{{ valueText(display.point.altitudeFt, ' ft') }}</dd></div>
        <div><dt>剩余航程</dt><dd>{{ valueText(display.point.distanceToGoNm, ' NM') }}</dd></div>
        <div><dt>预计到达</dt><dd>{{ display.point.destinationEtaText || '--' }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ formatDate(display.flight.lastUpdatedAt) }}</dd></div>
      </dl>
    </div>

    <div v-else class="flight-status-empty">
      <strong>暂无活跃飞行</strong>
      <span>启动模拟器并等待 QAR 入库后显示当前航班。</span>
    </div>
    </section>
  </div>
</template>
