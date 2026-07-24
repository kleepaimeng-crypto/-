import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { authSession } from '../auth/session'
import WorkspaceView from './WorkspaceView.vue'

const dataApi = vi.hoisted(() => ({
  getDataOptions: vi.fn(),
  getDataRecords: vi.fn(),
  getDataRecord: vi.fn(),
  updateRecordMetadata: vi.fn(),
  deleteDataRecord: vi.fn(),
  batchDeleteDataRecords: vi.fn(),
}))
const fileApi = vi.hoisted(() => ({
  createExport: vi.fn(),
  createImport: vi.fn(),
  getExportHistory: vi.fn(),
  getImportHistory: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))
vi.mock('../api/dataRecords', () => dataApi)
vi.mock('../api/fileJobs', () => fileApi)

const emptyPage = { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 }
let wrapper: VueWrapper | undefined

describe('WorkspaceView filters', () => {
  beforeEach(() => {
    authSession.state.user = {
      id: 'admin-id',
      username: 'admin',
      email: 'admin@example.com',
      roleCode: 'ADMIN',
    }
    authSession.state.initialized = true
    dataApi.getDataOptions.mockResolvedValue({
      dataTypes: [],
      airlines: [],
      aircraftModels: [],
      aircraftRegistrations: [],
      devices: [],
      airports: ['ZBAA', 'ZSPD'],
      tags: [],
    })
    dataApi.getDataRecords.mockResolvedValue(emptyPage)
    fileApi.getExportHistory.mockResolvedValue(emptyPage)
    fileApi.getImportHistory.mockResolvedValue(emptyPage)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    authSession.state.user = null
  })

  it('keeps route and date pickers collapsed in the single filter row initially', async () => {
    wrapper = mount(WorkspaceView)
    await settleRequests()

    expect(wrapper.find('.filter-bar').exists()).toBe(true)
    expect(wrapper.find('.table-toolbar').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('5 秒自动刷新')
    expect(wrapper.find('[aria-label="起飞机场"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="接收开始日期"]').exists()).toBe(false)
    expect(wrapper.findAll('.filter-trigger').map((button) => button.text())).toEqual(['航段⌄', '日期⌄'])
  })

  it('opens route and date as mutually exclusive anchored popovers', async () => {
    wrapper = mount(WorkspaceView)
    await settleRequests()
    const [routeTrigger, dateTrigger] = wrapper.findAll('.filter-trigger')

    await routeTrigger?.trigger('click')
    expect(wrapper.find('[aria-label="起飞机场"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="接收开始日期"]').exists()).toBe(false)

    await dateTrigger?.trigger('click')
    expect(wrapper.find('[aria-label="起飞机场"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="接收开始日期"]').exists()).toBe(true)
  })

  it('closes an open filter popover with Escape or an outside pointer action', async () => {
    wrapper = mount(WorkspaceView)
    await settleRequests()

    await wrapper.findAll('.filter-trigger')[0]?.trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(wrapper.find('[aria-label="起飞机场"]').exists()).toBe(false)

    await wrapper.findAll('.filter-trigger')[1]?.trigger('click')
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }))
    await nextTick()
    expect(wrapper.find('[aria-label="接收开始日期"]').exists()).toBe(false)
  })

  it('queries automatically when a route endpoint changes and clears both endpoints', async () => {
    wrapper = mount(WorkspaceView)
    await settleRequests()
    dataApi.getDataRecords.mockClear()

    await wrapper.findAll('.filter-trigger')[0]?.trigger('click')
    await wrapper.find<HTMLSelectElement>('[aria-label="起飞机场"]').setValue('ZBAA')
    await settleRequests()
    expect(dataApi.getDataRecords).toHaveBeenLastCalledWith(expect.objectContaining({ origin: 'ZBAA' }))

    await wrapper.find('.popover-heading button').trigger('click')
    await settleRequests()
    const lastQuery = dataApi.getDataRecords.mock.calls.at(-1)?.[0]
    expect(lastQuery?.origin).toBeUndefined()
    expect(lastQuery?.destination).toBeUndefined()
  })

  it('converts the selected end date to the next day at midnight in Asia/Shanghai', async () => {
    wrapper = mount(WorkspaceView)
    await settleRequests()
    dataApi.getDataRecords.mockClear()

    await wrapper.findAll('.filter-trigger')[1]?.trigger('click')
    await wrapper.find<HTMLInputElement>('[aria-label="接收开始日期"]').setValue('2026-07-04')
    await wrapper.find<HTMLInputElement>('[aria-label="接收结束日期"]').setValue('2026-07-06')
    await settleRequests()

    expect(dataApi.getDataRecords).toHaveBeenLastCalledWith(expect.objectContaining({
      receivedFrom: '2026-07-04T00:00:00+08:00',
      receivedTo: '2026-07-07T00:00:00+08:00',
    }))
  })

  it('disables edit and delete actions for normal users', async () => {
    authSession.state.user = {
      id: 'user-id',
      username: 'viewer',
      email: 'viewer@example.com',
      roleCode: 'USER',
    }
    dataApi.getDataRecords.mockResolvedValueOnce({
      items: [recordItem()],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    wrapper = mount(WorkspaceView)
    await settleRequests()

    const actions = wrapper.findAll('.row-action')
    expect(actions).toHaveLength(2)
    expect(actions[0]?.attributes('disabled')).toBeDefined()
    expect(actions[1]?.attributes('disabled')).toBeDefined()

    await actions[0]?.trigger('click')
    await actions[1]?.trigger('click')
    expect(wrapper.find('.dialog-panel').exists()).toBe(false)
    expect(dataApi.updateRecordMetadata).not.toHaveBeenCalled()
    expect(dataApi.deleteDataRecord).not.toHaveBeenCalled()

    await wrapper.find('.selection-cell input[type="checkbox"]').trigger('change')
    await nextTick()
    const batchButton = wrapper.find('.batch-action')
    expect(batchButton.attributes('disabled')).toBeDefined()
    await batchButton.trigger('click')
    expect(wrapper.find('.dialog-panel').exists()).toBe(false)
  })

  it('loads and displays the raw JSON as read-only content in the edit dialog', async () => {
    dataApi.getDataRecords.mockResolvedValueOnce({
      items: [recordItem()],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    dataApi.getDataRecord.mockResolvedValueOnce({
      id: 'record-id',
      metadata: {},
      rawPayload: { messageType: 'qar.frame', frame: 42 },
      rawText: null,
      parsedSummary: {},
      tags: [],
      annotations: [],
      deleted: false,
    })
    wrapper = mount(WorkspaceView)
    await settleRequests()

    await wrapper.findAll('.row-action')[0]?.trigger('click')
    await settleRequests()

    expect(dataApi.getDataRecord).toHaveBeenCalledWith('record-id')
    const payload = wrapper.find('.edit-payload-section')
    expect(payload.exists()).toBe(true)
    expect(payload.attributes('open')).toBeUndefined()
    expect(payload.text()).toContain('原始报文 JSON（只读）')
    expect(payload.text()).toContain('"messageType": "qar.frame"')
  })
})

async function settleRequests(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await nextTick()
}

function recordItem() {
  return {
    id: 'record-id',
    aircraftRegistrationNo: 'B-TEST-001',
    aircraftModel: 'A320',
    airlineCode: 'CA',
    flightNo: 'CA123',
    origin: 'ZBAA',
    destination: 'ZSPD',
    sourceDevice: { code: 'SIM-QAR', name: 'SIM-QAR' },
    dataType: { code: 'QAR', name: 'QAR 飞行数据' },
    sentAt: '2026-07-09T17:29:03+08:00',
    receivedAt: '2026-07-09T17:29:04+08:00',
    payloadCount: 1,
    parseStatus: 'PARSED',
    tags: [],
    deleted: false,
    version: 1,
  }
}
