import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PassengerCabinStage from './PassengerCabinStage.vue'
import type { PassengerActivityDto, PassengerSmartWindowSnapshotDto } from '../../api/types'

function activity(seatNo: string, activityKind: PassengerActivityDto['activityKind']): PassengerActivityDto {
  return {
    passengerId: `PAX-${seatNo}`,
    seatNo,
    cabinClass: 'ECONOMY',
    behaviorType: activityKind === 'VIDEO'
      ? 'MOVIE_PLAY'
      : activityKind === 'SHOPPING'
        ? 'SHOPPING'
        : 'WAP_BROWSING',
    activityKind,
    title: activityKind === 'VIDEO' ? '星海远航' : activityKind === 'SHOPPING' ? null : 'example.com',
    types: activityKind === 'VIDEO' ? ['奇幻'] : [],
    action: activityKind === 'VIDEO' ? 'PLAY' : null,
    domain: activityKind === 'BROWSING' ? 'example.com' : null,
    url: activityKind === 'BROWSING' ? 'https://example.com' : null,
    trafficBytes: activityKind === 'BROWSING' ? 1024 : null,
    bandwidthMbps: 8.42,
    windowBytes: 5262500,
    eventAt: '2026-07-08T09:00:00+08:00',
    bandwidthUpdatedAt: '2026-07-08T09:00:01+08:00',
    sourceRecordId: '00000000-0000-4000-8000-000000000001',
    mediaWork: activityKind === 'VIDEO'
      ? {
          workCode: 'MOV-001-2026',
          category: 'VIDEO',
          title: '星海远航',
          types: ['科幻', '传奇'],
          summary: '深空勘探船在返航途中收到一段来自失落殖民站的求救信号。',
          creatorName: '林澈',
          collectionName: '星海纪事',
          durationSeconds: 7680,
          releaseYear: 2026,
          language: '中文',
          region: '中国',
        }
      : null,
    recommendations: activityKind === 'VIDEO'
      ? [
          {
            workCode: 'MOV-005-2026',
            category: 'VIDEO',
            title: '银河竞技场',
            types: ['科幻', '竞技'],
            creatorName: '韩屿',
            currentViewerCount: 12,
            reason: 'SAME_TYPE',
          },
          {
            workCode: 'MOV-019-2026',
            category: 'VIDEO',
            title: '深空来客',
            types: ['科幻', '猎奇'],
            creatorName: '高述',
            currentViewerCount: 8,
            reason: 'SAME_TYPE',
          },
          {
            workCode: 'MOV-002-2026',
            category: 'VIDEO',
            title: '云端恋曲',
            types: ['爱情', '都市'],
            creatorName: '周岚',
            currentViewerCount: 20,
            reason: 'CATEGORY_POPULAR',
          },
        ]
      : [],
  }
}

describe('PassengerCabinStage activity list', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the full supplied list and keeps detail selection local', async () => {
    const wrapper = mount(PassengerCabinStage, {
      props: {
        activities: [activity('A46', 'VIDEO'), activity('A47', 'BROWSING')],
        activityError: '',
        activityLoading: false,
        cabinScroller: null,
        windowDisplay: null,
        windowError: '',
        windowLoading: false,
      },
    })

    const cards = wrapper.findAll('.watch-detail-card')
    expect(cards).toHaveLength(2)
    expect(wrapper.text()).toContain('星海远航')
    expect(wrapper.text()).toContain('https://example.com')
    expect(wrapper.text()).not.toContain('当前带宽')
    expect(wrapper.text()).not.toContain('8.4 Mbps')
    expect(wrapper.text()).toContain('银河竞技场')
    expect(wrapper.text()).toContain('同类型热门')
    expect(wrapper.text()).toContain('同类别热门补位')
    expect(wrapper.text()).not.toContain('当前选中座位')
    expect(wrapper.text()).not.toContain('行为时间')
    expect(wrapper.text()).not.toContain('窗口流量')
    expect(wrapper.find('.watch-thumb').exists()).toBe(false)

    await cards[1]?.trigger('click')
    expect(wrapper.find('.watch-detail-card.is-active').attributes('data-watch-seat')).toBe('A47')
  })

  it('shows a non-blocking warning for a partial smart-window snapshot', () => {
    const windowDisplay: PassengerSmartWindowSnapshotDto = {
      hasData: true,
      complete: false,
      expectedCount: 118,
      actualCount: 116,
      missingWindowIds: [17, 68],
      sourceRecordId: '00000000-0000-4000-8000-000000000002',
      updatedAt: '2026-07-08T09:00:00+08:00',
      summary: { averageBrightness: 5.2, disconnectedCount: 1, faultCount: 0, testCount: 0 },
      windows: [],
    }
    const wrapper = mount(PassengerCabinStage, {
      props: {
        activities: [],
        activityError: '',
        activityLoading: false,
        cabinScroller: null,
        windowDisplay,
        windowError: '',
        windowLoading: false,
      },
    })

    expect(wrapper.text()).toContain('舷窗数据不完整：116/118')
    expect(wrapper.text()).toContain('缺失 17、68')
  })

  it('renders shopping as a known activity instead of other', () => {
    const wrapper = mount(PassengerCabinStage, {
      props: {
        activities: [activity('A48', 'SHOPPING')],
        activityError: '',
        activityLoading: false,
        cabinScroller: null,
        windowDisplay: null,
        windowError: '',
        windowLoading: false,
      },
    })

    expect(wrapper.text()).toContain('购物')
    expect(wrapper.text()).toContain('当前行为：购物')
    expect(wrapper.text()).not.toContain('其他')
    expect(wrapper.find('.watch-kind--shopping').exists()).toBe(true)
  })

  it('keeps rendering during a rolling update when the backend still returns the old activity shape', () => {
    const legacyActivity = activity('A46', 'VIDEO') as Partial<PassengerActivityDto>
    delete legacyActivity.mediaWork
    delete legacyActivity.recommendations

    const wrapper = mount(PassengerCabinStage, {
      props: {
        activities: [legacyActivity as PassengerActivityDto],
        activityError: '',
        activityLoading: false,
        cabinScroller: null,
        windowDisplay: null,
        windowError: '',
        windowLoading: false,
      },
    })

    expect(wrapper.text()).toContain('星海远航')
    expect(wrapper.text()).toContain('作品资料待收录')
    expect(wrapper.text()).toContain('暂无更多同类作品')
  })
})
