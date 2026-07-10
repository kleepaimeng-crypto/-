<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authSession } from '../auth/session'
import { ApiClientError } from '../api/http'
import {
  batchDeleteDataRecords,
  deleteDataRecord,
  getDataOptions,
  getDataRecord,
  getDataRecords,
  updateRecordMetadata,
} from '../api/dataRecords'
import type { DataRecordQuery, MetadataUpdatePayload } from '../api/dataRecords'
import { createExport, createImport, getExportHistory, getImportHistory } from '../api/fileJobs'
import type { ExportCreatePayload, ImportCreatePayload } from '../api/fileJobs'
import type {
  DataOptionsDto,
  DataRecordDetailDto,
  DataRecordListItemDto,
  DataTypeCode,
  ExportFormat,
  FileJobSummaryDto,
} from '../api/types'
import FixedCanvasShell from '../components/FixedCanvasShell.vue'
import PlatformBrand from '../components/PlatformBrand.vue'

const router = useRouter()
const route = useRoute()
const EMPTY_OPTIONS: DataOptionsDto = {
  dataTypes: [], airlines: [], aircraftModels: [], aircraftRegistrations: [], devices: [], airports: [], tags: [],
}
const DATA_TYPE_LABELS: Record<DataTypeCode, string> = {
  QAR: 'QAR 飞行数据',
  GROUND_TASK: '仿真任务数据',
  GROUND_TRAFFIC_RECORD: '流量窗口数据',
  GROUND_SESSION_SUMMARY: '会话摘要数据',
  SMART_WINDOW_STATUS: '智能舷窗状态',
  IFE_633_BEHAVIOR: '633 IFE 乘客行为',
  IFE_COCKRELL_BEHAVIOR: '科克瑞尔 IFE 乘客行为',
}
const IMPORT_TYPES: DataTypeCode[] = ['QAR', 'GROUND_TASK', 'GROUND_TRAFFIC_RECORD', 'GROUND_SESSION_SUMMARY']

const options = ref<DataOptionsDto>(EMPTY_OPTIONS)
const records = ref<DataRecordListItemDto[]>([])
const exportHistory = ref<FileJobSummaryDto[]>([])
const importHistory = ref<FileJobSummaryDto[]>([])
const selectedIds = ref<string[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const listError = ref('')
const historyError = ref('')
const page = ref(1)
const pageSize = ref<20 | 50 | 100>(20)
const total = ref(0)
const totalPages = ref(0)
const openFilterPanel = ref<'route' | 'date' | null>(null)
const routeFilterRef = ref<HTMLElement | null>(null)
const dateFilterRef = ref<HTMLElement | null>(null)
const appliedFlightNo = ref('')

const filters = reactive({
  tagId: '', airlineCode: '', flightNo: '', sourceDeviceCode: '', aircraftModel: '',
  origin: '', destination: '', dataTypeCode: '', receivedFrom: '', receivedTo: '',
})
const sort = reactive<{ by: DataRecordQuery['sortBy']; direction: DataRecordQuery['sortDirection'] }>({
  by: 'receivedAt', direction: 'desc',
})

const detail = ref<DataRecordDetailDto | null>(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const editOpen = ref(false)
const deleteOpen = ref(false)
const importOpen = ref(false)
const activeRecord = ref<DataRecordListItemDto | null>(null)
const actionError = ref('')
const actionNotice = ref(route.query.denied === 'users' ? '当前账号无权访问用户管理。' : '')
const deleteReason = ref('')
const batchDeleting = ref(false)
const editForm = reactive<MetadataUpdatePayload>({
  aircraftRegistrationNo: '', aircraftModel: null, airlineCode: null, flightNo: null,
  origin: null, destination: null, sourceDeviceCode: '', expectedVersion: 1,
})
const exportForm = reactive<{ dataTypeCode: DataTypeCode; format: ExportFormat }>({
  dataTypeCode: 'QAR', format: 'CSV',
})
const importForm = reactive<{
  dataTypeCode: DataTypeCode
  file: File | null
  sourceDeviceCode: string
  aircraftRegistrationNo: string
}>({ dataTypeCode: 'QAR', file: null, sourceDeviceCode: '', aircraftRegistrationNo: '' })

const allSelected = computed(() => records.value.length > 0 && selectedIds.value.length === records.value.length)
const routeLabel = computed(() => {
  if (!filters.origin && !filters.destination) return '航段'
  return `${filters.origin || '起飞'} → ${filters.destination || '到达'}`
})
const dateLabel = computed(() => {
  if (!filters.receivedFrom && !filters.receivedTo) return '日期'
  return `${displayDate(filters.receivedFrom) || '开始'} — ${displayDate(filters.receivedTo) || '结束'}`
})
const canAutoRefresh = computed(() => page.value === 1 && selectedIds.value.length === 0
  && !detailOpen.value && !editOpen.value && !deleteOpen.value && !importOpen.value)
const canManageData = computed(() => {
  const roleCode = authSession.state.user?.roleCode
  return roleCode === 'SUPER_ADMIN' || roleCode === 'ADMIN'
})
const pageNumbers = computed(() => {
  const start = Math.max(1, page.value - 1)
  const end = Math.min(totalPages.value, start + 2)
  return Array.from({ length: Math.max(0, end - start + 1) }, (_, index) => start + index)
})

let refreshTimer: number | undefined

function buildQuery(): DataRecordQuery {
  return {
    tagIds: filters.tagId ? [filters.tagId] : undefined,
    airlineCode: filters.airlineCode || undefined,
    flightNo: filters.flightNo.trim() || undefined,
    sourceDeviceCode: filters.sourceDeviceCode || undefined,
    aircraftModel: filters.aircraftModel || undefined,
    origin: filters.origin || undefined,
    destination: filters.destination || undefined,
    dataTypeCode: filters.dataTypeCode || undefined,
    receivedFrom: toStartOfDayIso(filters.receivedFrom),
    receivedTo: toExclusiveEndIso(filters.receivedTo),
    page: page.value,
    pageSize: pageSize.value,
    sortBy: sort.by,
    sortDirection: sort.direction,
  }
}

async function loadOptions(): Promise<void> {
  try {
    options.value = await getDataOptions()
  } catch {
    options.value = EMPTY_OPTIONS
  }
}

async function loadRecords(background = false): Promise<void> {
  if (!background) loading.value = true
  listError.value = ''
  try {
    const result = await getDataRecords(buildQuery())
    records.value = result.items
    total.value = result.total
    totalPages.value = result.totalPages
    selectedIds.value = selectedIds.value.filter((id) => result.items.some((record) => record.id === id))
  } catch (error) {
    records.value = []
    total.value = 0
    totalPages.value = 0
    listError.value = errorMessage(error, '数据服务暂未接入，请在后端接口完成后重试。')
  } finally {
    loading.value = false
  }
}

async function loadHistories(): Promise<void> {
  historyError.value = ''
  const [exportsResult, importsResult] = await Promise.allSettled([getExportHistory(), getImportHistory()])
  exportHistory.value = exportsResult.status === 'fulfilled' ? exportsResult.value.items : []
  importHistory.value = importsResult.status === 'fulfilled' ? importsResult.value.items : []
  if (exportsResult.status === 'rejected' || importsResult.status === 'rejected') {
    historyError.value = '任务历史接口待后端接入'
  }
}

async function reloadAll(): Promise<void> {
  await Promise.all([loadOptions(), loadRecords(), loadHistories()])
}

function applyFilters(): void {
  page.value = 1
  selectedIds.value = []
  void loadRecords()
}

function applyFlightFilter(): void {
  const nextValue = filters.flightNo.trim()
  if (nextValue === appliedFlightNo.value) return
  filters.flightNo = nextValue
  appliedFlightNo.value = nextValue
  applyFilters()
}

function toggleFilterPanel(panel: 'route' | 'date'): void {
  openFilterPanel.value = openFilterPanel.value === panel ? null : panel
}

function clearRoute(): void {
  filters.origin = ''
  filters.destination = ''
  applyFilters()
}

function clearDateRange(): void {
  filters.receivedFrom = ''
  filters.receivedTo = ''
  applyFilters()
}

function handleDateChange(changed: 'from' | 'to'): void {
  if (filters.receivedFrom && filters.receivedTo && filters.receivedTo < filters.receivedFrom) {
    if (changed === 'from') filters.receivedTo = filters.receivedFrom
    else filters.receivedFrom = filters.receivedTo
  }
  applyFilters()
}

function handleDocumentPointerDown(event: PointerEvent): void {
  const target = event.target
  if (!(target instanceof Node)) return
  if (routeFilterRef.value?.contains(target) || dateFilterRef.value?.contains(target)) return
  openFilterPanel.value = null
}

function handleDocumentKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape') openFilterPanel.value = null
}

function changeSort(by: DataRecordQuery['sortBy']): void {
  if (sort.by === by) sort.direction = sort.direction === 'asc' ? 'desc' : 'asc'
  else {
    sort.by = by
    sort.direction = 'desc'
  }
  applyFilters()
}

function goToPage(nextPage: number): void {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  selectedIds.value = []
  void loadRecords()
}

function toggleAll(): void {
  selectedIds.value = allSelected.value ? [] : records.value.map((record) => record.id)
}

function toggleRecord(recordId: string): void {
  selectedIds.value = selectedIds.value.includes(recordId)
    ? selectedIds.value.filter((id) => id !== recordId)
    : [...selectedIds.value, recordId]
}

async function openDetail(record: DataRecordListItemDto): Promise<void> {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  actionError.value = ''
  try {
    detail.value = await getDataRecord(record.id)
  } catch (error) {
    actionError.value = errorMessage(error, '详情接口暂不可用')
  } finally {
    detailLoading.value = false
  }
}

function openEdit(record: DataRecordListItemDto): void {
  if (!canManageData.value) return
  activeRecord.value = record
  Object.assign(editForm, {
    aircraftRegistrationNo: record.aircraftRegistrationNo,
    aircraftModel: record.aircraftModel,
    airlineCode: record.airlineCode,
    flightNo: record.flightNo,
    origin: record.origin,
    destination: record.destination,
    sourceDeviceCode: record.sourceDevice.code,
    expectedVersion: record.version,
  })
  actionError.value = ''
  editOpen.value = true
}

async function submitEdit(): Promise<void> {
  if (!canManageData.value || !activeRecord.value || !editForm.aircraftRegistrationNo.trim() || !editForm.sourceDeviceCode.trim()) return
  actionLoading.value = true
  actionError.value = ''
  try {
    await updateRecordMetadata(activeRecord.value.id, { ...editForm })
    editOpen.value = false
    await loadRecords()
  } catch (error) {
    actionError.value = errorMessage(error, '保存失败')
  } finally {
    actionLoading.value = false
  }
}

function openDelete(record: DataRecordListItemDto): void {
  if (!canManageData.value) return
  activeRecord.value = record
  batchDeleting.value = false
  deleteReason.value = ''
  actionError.value = ''
  deleteOpen.value = true
}

function openBatchDelete(): void {
  if (!canManageData.value || selectedIds.value.length === 0) return
  activeRecord.value = null
  batchDeleting.value = true
  deleteReason.value = ''
  actionError.value = ''
  deleteOpen.value = true
}

async function submitDelete(): Promise<void> {
  if (!canManageData.value || (!activeRecord.value && !batchDeleting.value) || !deleteReason.value.trim()) return
  actionLoading.value = true
  actionError.value = ''
  try {
    if (batchDeleting.value) {
      const result = await batchDeleteDataRecords(selectedIds.value, deleteReason.value.trim())
      actionNotice.value = `批量删除完成：成功 ${result.deleted} 条，跳过 ${result.skipped} 条。`
      selectedIds.value = []
    } else if (activeRecord.value) {
      await deleteDataRecord(activeRecord.value.id, deleteReason.value.trim(), activeRecord.value.version)
      actionNotice.value = '记录已移入恢复期。'
    }
    deleteOpen.value = false
    await loadRecords()
  } catch (error) {
    actionError.value = errorMessage(error, '删除失败')
  } finally {
    actionLoading.value = false
  }
}

async function submitExport(): Promise<void> {
  actionLoading.value = true
  actionError.value = ''
  const query = buildQuery()
  const payload: ExportCreatePayload = {
    format: exportForm.format,
    filters: {
      dataTypeCode: exportForm.dataTypeCode,
      tagIds: query.tagIds ?? [],
      airlineCode: query.airlineCode ?? null,
      flightNo: query.flightNo ?? null,
      sourceDeviceCode: query.sourceDeviceCode ?? null,
      aircraftModel: query.aircraftModel ?? null,
      origin: query.origin ?? null,
      destination: query.destination ?? null,
      receivedFrom: query.receivedFrom ?? null,
      receivedTo: query.receivedTo ?? null,
    },
    sortBy: query.sortBy,
    sortDirection: query.sortDirection,
  }
  try {
    await createExport(payload)
    await loadHistories()
  } catch (error) {
    actionError.value = errorMessage(error, '导出任务接口暂不可用')
  } finally {
    actionLoading.value = false
  }
}

function onImportFile(event: Event): void {
  const input = event.target as HTMLInputElement
  importForm.file = input.files?.[0] ?? null
}

async function submitImport(): Promise<void> {
  if (!importForm.file || !importForm.sourceDeviceCode.trim() || !importForm.aircraftRegistrationNo.trim()) return
  actionLoading.value = true
  actionError.value = ''
  const payload: ImportCreatePayload = {
    dataTypeCode: importForm.dataTypeCode,
    file: importForm.file,
    sourceDeviceCode: importForm.sourceDeviceCode.trim(),
    aircraftRegistrationNo: importForm.aircraftRegistrationNo.trim(),
  }
  try {
    await createImport(payload)
    importOpen.value = false
    await loadHistories()
  } catch (error) {
    actionError.value = errorMessage(error, '导入任务接口暂不可用')
  } finally {
    actionLoading.value = false
  }
}

function formatDate(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date).replaceAll('/', '-')
}

function toStartOfDayIso(value: string): string | undefined {
  if (!value) return undefined
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00+08:00` : undefined
}

function toExclusiveEndIso(value: string): string | undefined {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined
  const [year, month, day] = value.split('-').map(Number)
  const nextDay = new Date(Date.UTC(year, month - 1, day + 1))
  return `${nextDay.getUTCFullYear()}-${padDatePart(nextDay.getUTCMonth() + 1)}-${padDatePart(nextDay.getUTCDate())}T00:00:00+08:00`
}

function displayDate(value: string): string {
  return value ? value.replaceAll('-', '/') : ''
}

function padDatePart(value: number): string {
  return String(value).padStart(2, '0')
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiClientError ? `${error.message}（${error.code}）` : fallback
}

function statusLabel(status: FileJobSummaryDto['status']): string {
  return { PENDING: '等待中', RUNNING: '处理中', SUCCEEDED: '已完成', PARTIAL: '部分完成', FAILED: '失败' }[status]
}

async function logout(): Promise<void> {
  authSession.logout()
  await router.replace('/login')
}

onMounted(() => {
  void reloadAll()
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeyDown)
  refreshTimer = window.setInterval(() => {
    if (canAutoRefresh.value) void loadRecords(true)
  }, 5000)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeyDown)
})
</script>

<template>
  <FixedCanvasShell shell-class="workspace-canvas-shell">
    <header class="workspace-header">
      <PlatformBrand compact />
      <nav class="workspace-nav" aria-label="主导航">
        <button class="workspace-nav__item is-active">数据管理</button>
        <button class="workspace-nav__item" @click="router.push('/flight-track')">飞机轨迹实时系统</button>
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

    <section class="workspace-layout">
      <div class="workspace-primary">
        <section class="filter-bar" aria-label="数据筛选">
          <select v-model="filters.tagId" aria-label="标签" @change="applyFilters">
            <option value="">全部标签</option>
            <option v-for="tag in options.tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
          </select>
          <select v-model="filters.airlineCode" aria-label="航司" @change="applyFilters">
            <option value="">全部航司</option>
            <option v-for="airline in options.airlines" :key="airline.code" :value="airline.code">{{ airline.name }}</option>
          </select>
          <input v-model="filters.flightNo" placeholder="航班号" aria-label="航班号" @keyup.enter="applyFlightFilter" @blur="applyFlightFilter" />
          <select v-model="filters.sourceDeviceCode" aria-label="设备" @change="applyFilters">
            <option value="">全部设备</option>
            <option v-for="device in options.devices" :key="device.code" :value="device.code">{{ device.name }}</option>
          </select>
          <select v-model="filters.aircraftModel" aria-label="机型" @change="applyFilters">
            <option value="">全部机型</option>
            <option v-for="model in options.aircraftModels" :key="model" :value="model">{{ model }}</option>
          </select>
          <div ref="routeFilterRef" class="filter-control">
            <button class="filter-trigger" type="button" :class="{ 'has-value': filters.origin || filters.destination }" :aria-expanded="openFilterPanel === 'route'" @click="toggleFilterPanel('route')">
              <span>{{ routeLabel }}</span><span class="filter-chevron">⌄</span>
            </button>
            <Transition name="filter-popover">
              <div v-if="openFilterPanel === 'route'" class="filter-popover route-popover" role="dialog" aria-label="选择航段">
                <div class="popover-heading"><strong>选择航段</strong><button type="button" @click="clearRoute">清空</button></div>
                <div class="route-picker">
                  <label>起飞机场<select v-model="filters.origin" aria-label="起飞机场" @change="applyFilters"><option value="">全部机场</option><option v-for="airport in options.airports" :key="`o-${airport}`" :value="airport">{{ airport }}</option></select></label>
                  <span>→</span>
                  <label>到达机场<select v-model="filters.destination" aria-label="到达机场" @change="applyFilters"><option value="">全部机场</option><option v-for="airport in options.airports" :key="`d-${airport}`" :value="airport">{{ airport }}</option></select></label>
                </div>
              </div>
            </Transition>
          </div>
          <div ref="dateFilterRef" class="filter-control">
            <button class="filter-trigger" type="button" :class="{ 'has-value': filters.receivedFrom || filters.receivedTo }" :aria-expanded="openFilterPanel === 'date'" @click="toggleFilterPanel('date')">
              <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M7 2v3M17 2v3M3.5 9h17M5 4h14a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z" /></svg>
              <span>{{ dateLabel }}</span><span class="filter-chevron">⌄</span>
            </button>
            <Transition name="filter-popover">
              <div v-if="openFilterPanel === 'date'" class="filter-popover date-popover" role="dialog" aria-label="选择日期范围">
                <div class="popover-heading"><strong>接收日期范围</strong><button type="button" @click="clearDateRange">清空</button></div>
                <div class="date-picker">
                  <label>开始日期<input v-model="filters.receivedFrom" type="date" aria-label="接收开始日期" :max="filters.receivedTo || undefined" @change="handleDateChange('from')" /></label>
                  <span>—</span>
                  <label>结束日期<input v-model="filters.receivedTo" type="date" aria-label="接收结束日期" :min="filters.receivedFrom || undefined" @change="handleDateChange('to')" /></label>
                </div>
                <p>结束日期包含全天，查询边界自动延伸至次日 00:00。</p>
              </div>
            </Transition>
          </div>
          <button class="button button--import" @click="importOpen = true">CSV 数据导入</button>
        </section>

        <section class="data-table-wrap" :class="{ 'is-loading': loading, 'is-extended-page': pageSize > 20 }">
          <table class="data-table">
            <colgroup>
              <col class="data-table__selection-column" />
              <col span="5" />
            </colgroup>
            <thead>
              <tr>
                <th class="selection-cell"><input type="checkbox" :checked="allSelected" @change="toggleAll" /></th>
                <th>飞机 ID</th>
                <th><button class="sort-button" @click="changeSort('dataType')">数据类型 <span>{{ sort.by === 'dataType' ? (sort.direction === 'asc' ? '↑' : '↓') : '↕' }}</span></button></th>
                <th><button class="sort-button" @click="changeSort('sentAt')">数据发送时间 <span>{{ sort.by === 'sentAt' ? (sort.direction === 'asc' ? '↑' : '↓') : '↕' }}</span></button></th>
                <th><button class="sort-button" @click="changeSort('receivedAt')">数据接收时间 <span>{{ sort.by === 'receivedAt' ? (sort.direction === 'asc' ? '↑' : '↓') : '↕' }}</span></button></th>
                <th class="actions-cell">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in records" :key="record.id" :class="{ 'is-selected': selectedIds.includes(record.id) }">
                <td class="selection-cell"><input type="checkbox" :checked="selectedIds.includes(record.id)" @change="toggleRecord(record.id)" /></td>
                <td><button class="record-link" @click="openDetail(record)">{{ record.aircraftRegistrationNo }}</button></td>
                <td><span class="type-name">{{ record.dataType.name }}</span></td>
                <td>{{ formatDate(record.sentAt) }}</td>
                <td>{{ formatDate(record.receivedAt) }}</td>
                <td class="actions-cell">
                  <button class="row-action" :disabled="!canManageData" @click="openEdit(record)">编辑</button>
                  <button class="row-action row-action--danger" :disabled="!canManageData" @click="openDelete(record)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>

          <div v-if="loading" class="table-state"><span class="loader"></span><strong>正在同步数据目录</strong><p>读取筛选结果与分页信息</p></div>
          <div v-else-if="listError" class="table-state table-state--error"><span class="state-code">接口待接入</span><strong>暂时无法读取数据</strong><p>{{ listError }}</p><button class="button button--ghost" @click="loadRecords()">重新请求</button></div>
          <div v-else-if="records.length === 0" class="table-state"><span class="state-code">NO RECORDS</span><strong>当前条件下没有数据</strong><p>启动 UDP 数据接入或调整筛选条件后再试。</p></div>
        </section>

        <footer class="table-footer">
          <div class="table-footer__summary">
            <span>总计 <strong>{{ total }}</strong> 条</span>
            <span v-if="selectedIds.length" class="selection-count">已选择 {{ selectedIds.length }} 条</span>
            <button v-if="selectedIds.length" class="batch-action" :disabled="!canManageData" @click="openBatchDelete">批量删除</button>
            <span v-if="actionNotice" class="action-notice">{{ actionNotice }}</span>
          </div>
          <select v-model="pageSize" @change="applyFilters">
            <option :value="20">20 条/页</option><option :value="50">50 条/页</option><option :value="100">100 条/页</option>
          </select>
          <div class="pagination">
            <button :disabled="page <= 1" @click="goToPage(page - 1)">‹</button>
            <button v-for="number in pageNumbers" :key="number" :class="{ 'is-current': number === page }" @click="goToPage(number)">{{ number }}</button>
            <button :disabled="page >= totalPages" @click="goToPage(page + 1)">›</button>
          </div>
        </footer>
      </div>

      <aside class="workspace-inspector">
        <div class="inspector-section-group">
          <header><h2>数据导出</h2></header>
          <section class="inspector-section export-config">
          <div class="export-block">
            <label>数据类型</label>
            <div class="radio-grid">
              <label v-for="(label, code) in DATA_TYPE_LABELS" :key="code"><input v-model="exportForm.dataTypeCode" type="radio" :value="code" /><span></span>{{ label }}</label>
            </div>
          </div>
          <div class="export-block export-format">
            <label>数据格式</label>
            <label><input v-model="exportForm.format" type="radio" value="CSV" /><span></span>CSV</label>
            <label><input v-model="exportForm.format" type="radio" value="PDF" /><span></span>PDF</label>
          </div>
          <button class="export-button" :disabled="actionLoading" @click="submitExport">{{ actionLoading ? '提交中…' : '创建导出任务' }}</button>
          <p v-if="actionError" class="inline-error">{{ actionError }}</p>
          </section>
        </div>

        <div class="inspector-section-group">
          <header><h2>导出历史</h2><button @click="loadHistories">刷新</button></header>
          <section class="inspector-section history-section">
          <div class="history-head"><span>创建时间</span><span>数据类型</span><span>状态</span></div>
          <div v-if="exportHistory.length" class="history-list">
            <div v-for="job in exportHistory.slice(0, 6)" :key="job.id" class="history-row">
              <span>{{ formatDate(job.createdAt) }}</span><span>{{ DATA_TYPE_LABELS[job.dataTypeCode] || job.dataTypeCode }}</span><span :class="`job-${job.status.toLowerCase()}`">{{ statusLabel(job.status) }}</span>
            </div>
          </div>
          <div v-else class="history-empty">{{ historyError || '暂无导出记录' }}</div>
          </section>
        </div>

        <div class="inspector-section-group">
          <header><h2>导入历史</h2></header>
          <section class="inspector-section history-section">
          <div class="history-head"><span>创建时间</span><span>数据类型</span><span>状态</span></div>
          <div v-if="importHistory.length" class="history-list">
            <div v-for="job in importHistory.slice(0, 6)" :key="job.id" class="history-row">
              <span>{{ formatDate(job.createdAt) }}</span><span>{{ DATA_TYPE_LABELS[job.dataTypeCode] || job.dataTypeCode }}</span><span :class="`job-${job.status.toLowerCase()}`">{{ statusLabel(job.status) }}</span>
            </div>
          </div>
          <div v-else class="history-empty">{{ historyError || '暂无导入记录' }}</div>
          </section>
        </div>
      </aside>
    </section>

    <footer class="workspace-footer"><span>平台状态：前端工作区已就绪 · 数据接口等待联调</span><span>版本号：V0.1</span></footer>

    <div v-if="detailOpen" class="overlay" @click.self="detailOpen = false">
      <aside class="detail-drawer">
        <header><div><span class="section-kicker">RECORD DETAIL</span><h2>数据详情</h2></div><button class="close-button" @click="detailOpen = false">×</button></header>
        <div v-if="detailLoading" class="dialog-state"><span class="loader"></span>读取详情中</div>
        <div v-else-if="actionError" class="dialog-state dialog-state--error">{{ actionError }}</div>
        <template v-else-if="detail">
          <dl class="metadata-grid">
            <div><dt>飞机 ID</dt><dd>{{ detail.metadata.aircraftRegistrationNo }}</dd></div>
            <div><dt>航班号</dt><dd>{{ detail.metadata.flightNo || '—' }}</dd></div>
            <div><dt>来源设备</dt><dd>{{ detail.metadata.sourceDeviceCode }}</dd></div>
            <div><dt>解析状态</dt><dd>{{ detail.metadata.parseStatus }}</dd></div>
            <div><dt>发送时间</dt><dd>{{ formatDate(detail.metadata.sentAt) }}</dd></div>
            <div><dt>接收时间</dt><dd>{{ formatDate(detail.metadata.receivedAt) }}</dd></div>
          </dl>
          <section class="payload-section"><h3>解析摘要</h3><pre>{{ JSON.stringify(detail.parsedSummary, null, 2) }}</pre></section>
          <section class="payload-section"><h3>原始报文 · 只读</h3><pre>{{ detail.rawPayload ? JSON.stringify(detail.rawPayload, null, 2) : detail.rawText }}</pre></section>
        </template>
      </aside>
    </div>

    <div v-if="editOpen" class="overlay" @click.self="editOpen = false">
      <section class="dialog-panel">
        <header><div><span class="section-kicker">METADATA</span><h2>编辑管理信息</h2></div><button class="close-button" @click="editOpen = false">×</button></header>
        <p class="dialog-hint">仅修改目录管理字段，原始报文与解析摘要保持只读。</p>
        <div class="form-grid">
          <label>飞机 ID<input v-model="editForm.aircraftRegistrationNo" /></label>
          <label>机型<input v-model="editForm.aircraftModel" /></label>
          <label>航司代码<input v-model="editForm.airlineCode" /></label>
          <label>航班号<input v-model="editForm.flightNo" /></label>
          <label>起飞机场<input v-model="editForm.origin" maxlength="4" /></label>
          <label>到达机场<input v-model="editForm.destination" maxlength="4" /></label>
          <label class="is-wide">来源设备<input v-model="editForm.sourceDeviceCode" /></label>
        </div>
        <p v-if="actionError" class="inline-error">{{ actionError }}</p>
        <footer><button class="button button--ghost" @click="editOpen = false">取消</button><button class="button button--accent" :disabled="actionLoading" @click="submitEdit">保存修改</button></footer>
      </section>
    </div>

    <div v-if="deleteOpen" class="overlay" @click.self="deleteOpen = false">
      <section class="dialog-panel dialog-panel--small">
        <header><div><span class="section-kicker">SOFT DELETE</span><h2>{{ batchDeleting ? `确认删除 ${selectedIds.length} 条数据` : '确认删除数据' }}</h2></div><button class="close-button" @click="deleteOpen = false">×</button></header>
        <p class="dialog-hint">{{ batchDeleting ? '将对当前选中记录执行批量软删除。' : '记录将进入恢复期，不会立即物理清理。' }}</p>
        <label class="field-label">删除原因<textarea v-model="deleteReason" rows="4" placeholder="请填写删除原因"></textarea></label>
        <p v-if="actionError" class="inline-error">{{ actionError }}</p>
        <footer><button class="button button--ghost" @click="deleteOpen = false">取消</button><button class="button button--danger" :disabled="!deleteReason.trim() || actionLoading" @click="submitDelete">确认删除</button></footer>
      </section>
    </div>

    <div v-if="importOpen" class="overlay" @click.self="importOpen = false">
      <section class="dialog-panel">
        <header><div><span class="section-kicker">CSV IMPORT</span><h2>创建数据导入任务</h2></div><button class="close-button" @click="importOpen = false">×</button></header>
        <div class="form-grid">
          <label>数据类型<select v-model="importForm.dataTypeCode"><option v-for="code in IMPORT_TYPES" :key="code" :value="code">{{ DATA_TYPE_LABELS[code] }}</option></select></label>
          <label>来源设备<input v-model="importForm.sourceDeviceCode" placeholder="例如 SIM-QAR" /></label>
          <label>飞机 ID<input v-model="importForm.aircraftRegistrationNo" placeholder="例如 B-TEST-001" /></label>
          <label class="is-wide file-field">CSV 文件<input type="file" accept=".csv,text/csv" @change="onImportFile" /><span>{{ importForm.file?.name || '选择不超过 50 MB 的 CSV 文件' }}</span></label>
        </div>
        <p v-if="actionError" class="inline-error">{{ actionError }}</p>
        <footer><button class="button button--ghost" @click="importOpen = false">取消</button><button class="button button--accent" :disabled="!importForm.file || actionLoading" @click="submitImport">创建任务</button></footer>
      </section>
    </div>
  </FixedCanvasShell>
</template>
