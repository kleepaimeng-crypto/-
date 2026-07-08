<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import type {
  PassengerActivityDto,
  PassengerSmartWindowItemDto,
  PassengerSmartWindowSnapshotDto,
} from '../../api/types'
import { formatBytes, formatDate, formatMbps } from '../../utils/displayFormatters'
import {
  windowSide,
  windowVisualBrightness,
  windowZone,
} from '../../utils/smartWindowDisplay'

type WatchKind = 'video' | 'music' | 'browsing' | 'other' | 'idle'
type CabinSectionKey = 'front' | 'middle' | 'rear'

interface WatchPreviewRow {
  seat: string
  type: WatchKind
  title: string
  meta: string
  role: string
  detail: string
  active: boolean
}

interface SelectSeatOptions {
  scrollAircraft?: boolean
  scrollWatchList?: boolean
}

interface CabinSection {
  key: CabinSectionKey
  label: string
  anchorSeat: string
  fallbackRatio: number
}

interface WindowLabelView {
  windowId: number
  brightnessLevel: number | null
  connected: boolean
  status: PassengerSmartWindowItemDto['status'] | null
  missing: boolean
  side: 'left' | 'right'
  style: Record<string, string>
}

const seatNodeSelector = 'g[id^="Business-Seat-"], g[id^="Economy-Seat-"]'
const seatIdPattern = /\bid="(?:Business|Economy)-Seat-([A-Z][0-9]+)"/g
const windowNodeSelector = 'g[id^="Window-"]'
const cabinBlueprintUrl = '/assets/CA_332-1%201.svg'
const cabinSections: CabinSection[] = [
  { key: 'front', label: '前舱', anchorSeat: 'C11', fallbackRatio: 0.08 },
  { key: 'middle', label: '中舱', anchorSeat: 'D14', fallbackRatio: 0.34 },
  { key: 'rear', label: '后舱', anchorSeat: 'D43', fallbackRatio: 0.7 },
]

const selectedSeat = ref('D48')
const activeCabinSection = ref<CabinSectionKey>('middle')
const seatOverlaySvg = ref('')
const windowOverlaySvg = ref('')
const cabinScrollElement = ref<HTMLElement | null>(null)
const seatLayerRef = ref<HTMLElement | null>(null)
const windowLayerRef = ref<HTMLElement | null>(null)
const watchListRef = ref<HTMLElement | null>(null)
const selectedSeatLabelStyle = ref<Record<string, string>>({ opacity: '0' })
const windowLabels = ref<WindowLabelView[]>([])
const defaultCabinSectionApplied = ref(false)

const props = defineProps<{
  activities: PassengerActivityDto[]
  activityError: string
  activityLoading: boolean
  cabinScroller: HTMLElement | null
  windowDisplay: PassengerSmartWindowSnapshotDto | null
  windowError: string
  windowLoading: boolean
}>()

const availableSeatIds = computed(() => {
  const seats = new Set<string>()

  for (const match of seatOverlaySvg.value.matchAll(seatIdPattern)) {
    if (match[1]) {
      seats.add(match[1])
    }
  }

  return [...seats]
})

const watchPreviewRows = computed<WatchPreviewRow[]>(() => {
  return props.activities.map((activity) => buildWatchPreviewRow(
    activity,
    activity.seatNo === selectedSeat.value,
  ))
})

const emit = defineEmits<{
  (event: 'update:cabinScroller', element: HTMLElement | null): void
}>()

function setScroller(element: Element | ComponentPublicInstance | null): void {
  const scroller = element instanceof HTMLElement ? element : null

  cabinScrollElement.value = scroller
  emit('update:cabinScroller', scroller)
}

function buildWatchPreviewRow(activity: PassengerActivityDto, active: boolean): WatchPreviewRow {
  const type = watchKind(activity)
  return {
    seat: activity.seatNo,
    type,
    title: activity.title || (type === 'idle' ? '暂无观看或浏览行为' : watchKindLabel(type)),
    meta: activity.types.length > 0 ? activity.types.join(' / ') : (activity.behaviorType || '暂无类型'),
    role: `当前带宽：${formatMbps(activity.bandwidthMbps)} · 窗口流量：${formatBytes(activity.windowBytes)}`,
    detail: activityKindDetail(activity),
    active,
  }
}

function watchKind(activity: PassengerActivityDto): WatchKind {
  if (activity.activityKind === 'VIDEO') return 'video'
  if (activity.activityKind === 'MUSIC') return 'music'
  if (activity.activityKind === 'BROWSING') return 'browsing'
  if (activity.activityKind === 'IDLE') return 'idle'
  return 'other'
}

function watchKindLabel(kind: WatchKind): string {
  if (kind === 'video') return '视频'
  if (kind === 'music') return '音乐'
  if (kind === 'browsing') return '浏览'
  if (kind === 'idle') return '空闲'
  return '其他'
}

function activityKindDetail(activity: PassengerActivityDto): string {
  if (activity.activityKind === 'BROWSING') {
    return `${activity.domain || '未知域名'} · ${activity.url || '暂无 URL'} · 累计 ${formatBytes(activity.trafficBytes)}`
  }
  const action = activity.action || '暂无动作'
  return `${action} · 行为时间 ${formatDate(activity.eventAt)}`
}

function missingWindowSummary(snapshot: PassengerSmartWindowSnapshotDto): string {
  const ids = snapshot.missingWindowIds
  if (ids.length === 0) return ''
  const visible = ids.slice(0, 8).join('、')
  return ids.length <= 8 ? `缺失 ${visible}` : `缺失 ${visible} 等 ${ids.length} 个舷窗`
}

function extractSeatFromGroupId(id: string): string | null {
  const match = /^(?:Business|Economy)-Seat-([A-Z][0-9]+)$/.exec(id)
  return match?.[1] ?? null
}

function getSeatFromEventTarget(target: EventTarget | null): string | null {
  if (!(target instanceof Element)) {
    return null
  }

  const group = target.closest(seatNodeSelector)
  return group?.id ? extractSeatFromGroupId(group.id) : null
}

function prepareSeatLayer(): void {
  const root = seatLayerRef.value

  if (!root) {
    return
  }

  root.querySelectorAll<SVGGElement>(seatNodeSelector).forEach((group) => {
    const seat = extractSeatFromGroupId(group.id)

    if (!seat) {
      return
    }

    group.classList.add('seat-svg-node')
    group.setAttribute('tabindex', '0')
    group.setAttribute('role', 'button')
    group.setAttribute('aria-label', `选择座位 ${seat}`)
  })

  syncSelectedSeatClass()
}

function syncSelectedSeatClass(): void {
  const root = seatLayerRef.value

  if (!root) {
    return
  }

  root.querySelectorAll<SVGGElement>(seatNodeSelector).forEach((group) => {
    const seat = extractSeatFromGroupId(group.id)
    const isSelected = seat === selectedSeat.value

    group.classList.toggle('is-selected-seat', isSelected)
    group.setAttribute('aria-pressed', String(isSelected))
  })

  syncSelectedSeatLabelPosition()
}

function syncSelectedSeatLabelPosition(): void {
  const root = seatLayerRef.value

  if (!root) {
    selectedSeatLabelStyle.value = { opacity: '0' }
    return
  }

  const target = [...root.querySelectorAll<SVGGElement>(seatNodeSelector)]
    .find((group) => extractSeatFromGroupId(group.id) === selectedSeat.value)
  const svg = root.querySelector<SVGSVGElement>('svg')

  if (!target || !svg) {
    selectedSeatLabelStyle.value = { opacity: '0' }
    return
  }

  try {
    const box = target.getBBox()
    const viewBox = svg.viewBox.baseVal

    if (viewBox.width <= 0 || viewBox.height <= 0) {
      selectedSeatLabelStyle.value = { opacity: '0' }
      return
    }

    selectedSeatLabelStyle.value = {
      left: `${((box.x + box.width / 2 - viewBox.x) / viewBox.width) * 100}%`,
      top: `${((box.y + box.height / 2 - viewBox.y) / viewBox.height) * 100}%`,
      opacity: '1',
    }
  } catch {
    selectedSeatLabelStyle.value = { opacity: '0' }
  }
}

function refreshSeatLayer(): void {
  void nextTick().then(() => {
    prepareSeatLayer()
    applyDefaultCabinSection()
  })
}

function extractWindowId(groupId: string): number | null {
  const match = /^Window-(\d{3})$/.exec(groupId)
  if (!match?.[1]) return null
  const windowId = Number(match[1])
  return Number.isInteger(windowId) && windowId >= 1 && windowId <= 116 ? windowId : null
}

function refreshWindowLayer(): void {
  void nextTick().then(syncWindowLayer)
}

function syncWindowLayer(): void {
  const root = windowLayerRef.value
  if (!root) {
    windowLabels.value = []
    return
  }

  const svg = root.querySelector<SVGSVGElement>('svg')
  if (!svg || svg.viewBox.baseVal.width <= 0 || svg.viewBox.baseVal.height <= 0) {
    windowLabels.value = []
    return
  }

  const dataById = new Map(
    (props.windowDisplay?.windows ?? [])
      .map((item) => [item.windowId, item]),
  )
  const missingIds = new Set(props.windowDisplay?.missingWindowIds ?? [])
  const labels: WindowLabelView[] = []
  const viewBox = svg.viewBox.baseVal

  root.querySelectorAll<SVGGElement>(windowNodeSelector).forEach((group) => {
    const windowId = extractWindowId(group.id)
    if (windowId === null) return

    const item = dataById.get(windowId)
    const missing = missingIds.has(windowId)
    group.classList.add('window-svg-node')
    group.classList.toggle('is-disconnected', item !== undefined && !item.connected)
    group.classList.toggle('is-fault', item?.status === 'FAULT')
    group.classList.toggle('is-test', item?.status === 'TEST')
    group.classList.toggle('is-missing', missing)
    group.setAttribute('aria-label', missing
      ? `舷窗 ${windowId}，数据缺失`
      : item
        ? `舷窗 ${windowId}，透光度 ${item.brightnessLevel}`
        : `舷窗 ${windowId}，暂无数据`)
    group.style.filter = item
      ? `brightness(${windowVisualBrightness(item.brightnessLevel)})`
      : 'brightness(0.35) grayscale(1)'

    if (!item && !missing) return

    try {
      const box = group.getBBox()
      const side = windowSide(windowId)
      const anchorX = side === 'left' ? box.x - 10 : box.x + box.width + 10
      labels.push({
        windowId,
        brightnessLevel: item?.brightnessLevel ?? null,
        connected: item?.connected ?? false,
        status: item?.status ?? null,
        missing,
        side,
        style: {
          left: `${((anchorX - viewBox.x) / viewBox.width) * 100}%`,
          top: `${((box.y + box.height / 2 - viewBox.y) / viewBox.height) * 100}%`,
        },
      })
      group.setAttribute('data-zone-id', String(windowZone(windowId)))
    } catch {
      // SVG geometry can be unavailable during the first render; the next refresh retries it.
    }
  })

  windowLabels.value = labels.sort((left, right) => left.windowId - right.windowId)
}

function handleSeatLayerClick(event: MouseEvent): void {
  const seat = getSeatFromEventTarget(event.target)

  if (seat) {
    selectSeat(seat, { scrollWatchList: true })
  }
}

function handleSeatLayerKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' && event.key !== ' ') {
    return
  }

  const seat = getSeatFromEventTarget(event.target)

  if (!seat) {
    return
  }

  event.preventDefault()
  selectSeat(seat, { scrollWatchList: true })
}

function handleWatchCardKeydown(event: KeyboardEvent, seat: string): void {
  if (event.key !== 'Enter' && event.key !== ' ') {
    return
  }

  event.preventDefault()
  selectSeat(seat, { scrollAircraft: true })
}

function selectSeat(seat: string, options: SelectSeatOptions = {}): void {
  selectedSeat.value = seat

  void nextTick().then(() => {
    if (options.scrollAircraft) {
      scrollSeatIntoAircraftView(seat)
    }
    if (options.scrollWatchList) {
      scrollWatchCardIntoView(seat)
    }
  })
}

function scrollWatchCardIntoView(seat: string): void {
  const list = watchListRef.value
  const card = list?.querySelector<HTMLElement>(`[data-watch-seat="${seat}"]`)

  if (!list || !card) {
    return
  }

  const listRect = list.getBoundingClientRect()
  const cardRect = card.getBoundingClientRect()
  const targetTop = list.scrollTop
    + cardRect.top
    - listRect.top
    - Math.max(0, (list.clientHeight - card.offsetHeight) / 2)

  list.scrollTo({
    top: Math.max(0, targetTop),
    behavior: 'smooth',
  })
}

function scrollSeatIntoAircraftView(seat: string, behavior: ScrollBehavior = 'smooth'): boolean {
  const scroller = cabinScrollElement.value
  const root = seatLayerRef.value

  if (!scroller || !root) {
    return false
  }

  const svg = root.querySelector<SVGSVGElement>('svg')
  const target = [...root.querySelectorAll<SVGGElement>(seatNodeSelector)]
    .find((group) => extractSeatFromGroupId(group.id) === seat)

  if (!svg || !target) {
    return false
  }

  try {
    const box = target.getBBox()
    const viewBox = svg.viewBox.baseVal
    const svgHeight = svg.getBoundingClientRect().height

    if (viewBox.height <= 0 || svgHeight <= 0) {
      return false
    }

    const seatCenterY = ((box.y + box.height / 2 - viewBox.y) / viewBox.height) * svgHeight
    const maxScrollTop = Math.max(0, scroller.scrollHeight - scroller.clientHeight)
    const targetScrollTop = Math.max(
      0,
      Math.min(maxScrollTop, seatCenterY - scroller.clientHeight * 0.5),
    )

    scroller.scrollTo({
      top: targetScrollTop,
      behavior,
    })

    return true
  } catch {
    // SVG geometry can be temporarily unavailable while the inline layer is mounting.
    return false
  }
}

function jumpToCabinSection(sectionKey: CabinSectionKey, behavior: ScrollBehavior = 'smooth'): void {
  const section = cabinSections.find((item) => item.key === sectionKey)

  if (!section) {
    return
  }

  activeCabinSection.value = section.key

  void nextTick().then(() => {
    if (!scrollSeatToSectionStart(section.anchorSeat, behavior)) {
      scrollAircraftToRatio(section.fallbackRatio, behavior)
    }
  })
}

function scrollSeatToSectionStart(seat: string, behavior: ScrollBehavior): boolean {
  const scroller = cabinScrollElement.value
  const root = seatLayerRef.value

  if (!scroller || !root) {
    return false
  }

  const svg = root.querySelector<SVGSVGElement>('svg')
  const target = [...root.querySelectorAll<SVGGElement>(seatNodeSelector)]
    .find((group) => extractSeatFromGroupId(group.id) === seat)

  if (!svg || !target) {
    return false
  }

  try {
    const box = target.getBBox()
    const viewBox = svg.viewBox.baseVal
    const svgHeight = svg.getBoundingClientRect().height

    if (viewBox.height <= 0 || svgHeight <= 0) {
      return false
    }

    const seatTopY = ((box.y - viewBox.y) / viewBox.height) * svgHeight
    const maxScrollTop = Math.max(0, scroller.scrollHeight - scroller.clientHeight)
    const targetScrollTop = Math.max(
      0,
      Math.min(maxScrollTop, seatTopY - scroller.clientHeight * 0.12),
    )

    scroller.scrollTo({
      top: targetScrollTop,
      behavior,
    })

    return true
  } catch {
    return false
  }
}

function scrollAircraftToRatio(ratio: number, behavior: ScrollBehavior): void {
  const scroller = cabinScrollElement.value

  if (!scroller) {
    return
  }

  const maxScrollTop = Math.max(0, scroller.scrollHeight - scroller.clientHeight)

  scroller.scrollTo({
    top: maxScrollTop * ratio,
    behavior,
  })
}

function applyDefaultCabinSection(): void {
  if (defaultCabinSectionApplied.value) {
    return
  }

  defaultCabinSectionApplied.value = true
  jumpToCabinSection('middle', 'auto')
}

async function loadSeatOverlaySvg(): Promise<void> {
  try {
    const response = await fetch('/assets/plane-seat-overlay-renamed.svg', { cache: 'force-cache' })

    if (!response.ok) {
      return
    }

    seatOverlaySvg.value = await response.text()
  } catch {
    seatOverlaySvg.value = ''
  }
}

async function loadWindowOverlaySvg(): Promise<void> {
  try {
    const response = await fetch('/assets/airplane_windows.svg', { cache: 'force-cache' })
    if (!response.ok) return
    windowOverlaySvg.value = await response.text()
  } catch {
    windowOverlaySvg.value = ''
  }
}

watch(availableSeatIds, (seats) => {
  if (seats.length > 0 && !seats.includes(selectedSeat.value)) {
    selectedSeat.value = seats[0]
  }
})

watch(seatOverlaySvg, refreshSeatLayer)
watch(windowOverlaySvg, refreshWindowLayer)
watch(() => props.windowDisplay, refreshWindowLayer)

watch(selectedSeat, () => {
  void nextTick().then(() => {
    syncSelectedSeatClass()
  })
})

onMounted(() => {
  void loadSeatOverlaySvg()
  void loadWindowOverlaySvg()
})
</script>

<template>
  <section class="cabin-stage">
    <div class="cabin-section-nav-frame">
      <nav class="cabin-section-nav" aria-label="舱段跳转">
        <button
          v-for="section in cabinSections"
          :key="section.key"
          type="button"
          :class="{ 'is-active': activeCabinSection === section.key }"
          @click="jumpToCabinSection(section.key)"
        >
          {{ section.label }}
        </button>
      </nav>
    </div>

    <div :ref="setScroller" class="cabin-scroll">
      <div class="cabin-map">
        <div class="cabin-blueprint-frame" aria-hidden="true">
          <img class="cabin-blueprint" :src="cabinBlueprintUrl" alt="" />
        </div>
        <div
          v-if="windowOverlaySvg"
          ref="windowLayerRef"
          class="window-svg-layer"
          role="group"
          aria-label="智慧舷窗透光度"
        >
          <div class="window-svg-inline" v-html="windowOverlaySvg"></div>
          <span
            v-for="label in windowLabels"
            :key="label.windowId"
            class="window-brightness-label"
            :class="[
              `window-brightness-label--${label.side}`,
              {
                'is-disconnected': !label.connected,
                'is-fault': label.status === 'FAULT',
                'is-test': label.status === 'TEST',
                'is-missing': label.missing,
              },
            ]"
            :style="label.style"
          >
            <i></i>{{ label.missing ? '舷窗数据缺失' : `舷窗透光度：${label.brightnessLevel}` }}
          </span>
        </div>
        <div
          v-if="seatOverlaySvg"
          ref="seatLayerRef"
          class="seat-svg-layer"
          role="group"
          aria-label="飞机座位交互层"
          @click="handleSeatLayerClick"
          @keydown="handleSeatLayerKeydown"
        >
          <div class="seat-svg-inline" v-html="seatOverlaySvg"></div>
          <span class="seat-selected-label" :style="selectedSeatLabelStyle">{{ selectedSeat }}</span>
        </div>
      </div>
    </div>

    <div class="cabin-fixed-cloth" aria-hidden="true"></div>

    <aside class="watch-detail-window" aria-label="乘客观看详情">
      <header class="floating-panel-title">
        <span></span>
        <strong>乘客观看详情</strong>
      </header>

      <div ref="watchListRef" class="watch-detail-list">
        <div v-if="activityLoading && activities.length === 0" class="watch-list-state">
          读取乘客详情中
        </div>
        <div v-else-if="activityError && activities.length === 0" class="watch-list-state watch-list-state--error">
          {{ activityError }}
        </div>
        <article
          v-for="item in watchPreviewRows"
          :key="item.seat"
          class="watch-detail-card"
          :class="{ 'is-active': item.active }"
          :data-watch-seat="item.seat"
          role="button"
          tabindex="0"
          @click="selectSeat(item.seat, { scrollAircraft: true })"
          @keydown="handleWatchCardKeydown($event, item.seat)"
        >
          <header class="watch-seat-line">
            <span class="seat-icon"></span>
            <strong>{{ item.seat }}</strong>
            <i :class="`watch-kind watch-kind--${item.type}`">{{ watchKindLabel(item.type) }}</i>
          </header>
          <div class="watch-detail-body">
            <div class="watch-thumb">{{ item.active ? '当前选中座位' : watchKindLabel(item.type) }}</div>
            <div class="watch-copy">
              <strong>{{ item.title }}</strong>
              <span>{{ item.meta }}</span>
              <p class="watch-role">{{ item.role }}</p>
              <p>{{ item.detail }}</p>
            </div>
          </div>
        </article>
        <div v-if="activityError && activities.length > 0" class="watch-refresh-warning">
          刷新失败，当前显示上一次成功结果
        </div>
      </div>
    </aside>

    <div v-if="windowLoading && !windowDisplay" class="cabin-aircraft-state">
      <div class="cabin-overlay">读取智慧舷窗状态中</div>
    </div>
    <div v-else-if="windowError && !windowDisplay?.hasData" class="cabin-aircraft-state">
      <div class="cabin-overlay cabin-overlay--error">
        <strong>舷窗数据读取失败</strong>
        <span>{{ windowError }}</span>
      </div>
    </div>
    <div v-else-if="windowDisplay && windowDisplay.actualCount === 0" class="cabin-aircraft-state">
      <div class="cabin-overlay">
        暂无可用的舷窗数据
      </div>
    </div>
    <div v-else-if="windowError && windowDisplay?.hasData" class="cabin-aircraft-state">
      <div class="cabin-overlay cabin-overlay--stale">
        舷窗数据刷新失败，当前显示上一次成功结果
      </div>
    </div>
    <div v-else-if="windowDisplay && !windowDisplay.complete" class="cabin-aircraft-state">
      <div class="cabin-overlay cabin-overlay--incomplete">
        <strong>舷窗数据不完整：{{ windowDisplay.actualCount }}/{{ windowDisplay.expectedCount }}</strong>
        <span>{{ missingWindowSummary(windowDisplay) }}</span>
      </div>
    </div>

  </section>
</template>
