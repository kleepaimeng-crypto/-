import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
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
}))
vi.mock('../api/dataRecords', () => dataApi)
vi.mock('../api/fileJobs', () => fileApi)

const emptyPage = { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 }
let wrapper: VueWrapper | undefined

describe('WorkspaceView filters', () => {
  beforeEach(() => {
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
})

async function settleRequests(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await nextTick()
}
