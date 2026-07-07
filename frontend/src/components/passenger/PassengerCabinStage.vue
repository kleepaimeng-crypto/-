<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import type { SmartWindowDisplayDto } from '../../api/types'

type WatchKind = 'video' | 'music'
type CabinSectionKey = 'front' | 'middle' | 'rear'

interface WatchPreviewRow {
  seat: string
  type: WatchKind
  title: string
  meta: string
  role: string
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

const seatNodeSelector = 'g[id^="Business-Seat-"], g[id^="Economy-Seat-"]'
const seatIdPattern = /\bid="(?:Business|Economy)-Seat-([A-Z][0-9]+)"/g
const cabinSections: CabinSection[] = [
  { key: 'front', label: '前舱', anchorSeat: 'C11', fallbackRatio: 0.08 },
  { key: 'middle', label: '中舱', anchorSeat: 'D14', fallbackRatio: 0.34 },
  { key: 'rear', label: '后舱', anchorSeat: 'D43', fallbackRatio: 0.7 },
]

const selectedSeat = ref('D48')
const activeCabinSection = ref<CabinSectionKey>('middle')
const seatOverlaySvg = ref('')
const cabinScrollElement = ref<HTMLElement | null>(null)
const seatLayerRef = ref<HTMLElement | null>(null)
const watchListRef = ref<HTMLElement | null>(null)
const selectedSeatLabelStyle = ref<Record<string, string>>({ opacity: '0' })
const defaultCabinSectionApplied = ref(false)

const knownWatchPreviewRows: Omit<WatchPreviewRow, 'active'>[] = [
  { seat: 'A46', type: 'video', title: '视频标题', meta: '标签', role: '演员 1   演员 2   演员 3' },
  { seat: 'A47', type: 'music', title: '音乐标题', meta: '标签', role: '歌手' },
  { seat: 'D48', type: 'video', title: '视频标题', meta: '标签', role: '演员 1   演员 2   演员 3' },
  { seat: 'A49', type: 'music', title: '音乐标题', meta: '标签', role: '歌手' },
]

const knownWatchPreviewMap = new Map(knownWatchPreviewRows.map((item) => [item.seat, item]))

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
  const listedSeats = availableSeatIds.value.length > 0
    ? availableSeatIds.value
    : knownWatchPreviewRows.map((item) => item.seat)
  const prioritySeats = knownWatchPreviewRows.map((item) => item.seat)
  const rows = [...new Set([...prioritySeats, ...listedSeats])]

  return rows.map((seat) => buildWatchPreviewRow(seat, seat === selectedSeat.value))
})

defineProps<{
  cabinScroller: HTMLElement | null
  windowDisplay: SmartWindowDisplayDto | null
  windowError: string
  windowLoading: boolean
}>()

const emit = defineEmits<{
  (event: 'update:cabinScroller', element: HTMLElement | null): void
}>()

function setScroller(element: Element | ComponentPublicInstance | null): void {
  const scroller = element instanceof HTMLElement ? element : null

  cabinScrollElement.value = scroller
  emit('update:cabinScroller', scroller)
}

function buildWatchPreviewRow(seat: string, active: boolean): WatchPreviewRow {
  const knownRow = knownWatchPreviewMap.get(seat)

  if (knownRow) {
    return {
      ...knownRow,
      active,
    }
  }

  return {
    seat,
    type: 'video',
    title: '观看内容待接入',
    meta: '占位信息',
    role: '详情接口接入后展示',
    active,
  }
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

watch(availableSeatIds, (seats) => {
  if (seats.length > 0 && !seats.includes(selectedSeat.value)) {
    selectedSeat.value = seats[0]
  }
})

watch(seatOverlaySvg, refreshSeatLayer)

watch(selectedSeat, () => {
  void nextTick().then(() => {
    syncSelectedSeatClass()
  })
})

onMounted(() => {
  void loadSeatOverlaySvg()
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
          <img class="cabin-blueprint" src="/assets/CA_332-1%201.svg" alt="" />
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
            <i :class="`watch-kind watch-kind--${item.type}`">{{ item.type === 'video' ? '视频' : '音乐' }}</i>
          </header>
          <div class="watch-detail-body">
            <div class="watch-thumb">{{ item.active ? '当前选中座位' : '封面待接入' }}</div>
            <div class="watch-copy">
              <strong>{{ item.title }}</strong>
              <span>{{ item.meta }}</span>
              <p class="watch-role">{{ item.role }}</p>
              <p>简介内容待详情接口接入后展示。</p>
            </div>
          </div>
        </article>
      </div>
    </aside>

    <div v-if="windowLoading" class="cabin-aircraft-state">
      <div class="cabin-overlay">读取智慧舷窗状态中</div>
    </div>
    <div v-else-if="windowError" class="cabin-aircraft-state">
      <div class="cabin-overlay cabin-overlay--error">
        <strong>舷窗接口未就绪</strong>
        <span>{{ windowError }}</span>
      </div>
    </div>
    <div v-else-if="windowDisplay && windowDisplay.windows.length === 0" class="cabin-aircraft-state">
      <div class="cabin-overlay">
        暂无智慧舷窗状态数据
      </div>
    </div>

  </section>
</template>
