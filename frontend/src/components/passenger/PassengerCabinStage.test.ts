import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PassengerCabinStage from './PassengerCabinStage.vue'
import type { PassengerActivityDto, PassengerSmartWindowSnapshotDto } from '../../api/types'

function activity(seatNo: string, activityKind: PassengerActivityDto['activityKind']): PassengerActivityDto {
  return {
    passengerId: `PAX-${seatNo}`,
    seatNo,
    cabinClass: 'ECONOMY',
    behaviorType: activityKind === 'VIDEO' ? 'MOVIE_PLAY' : 'WAP_BROWSING',
    activityKind,
    title: activityKind === 'VIDEO' ? '星海远航' : 'example.com',
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

    await cards[1]?.trigger('click')
    expect(wrapper.find('.watch-detail-card.is-active').attributes('data-watch-seat')).toBe('A47')
  })

  it('shows a non-blocking warning for a partial smart-window snapshot', () => {
    const windowDisplay: PassengerSmartWindowSnapshotDto = {
      hasData: true,
      complete: false,
      expectedCount: 116,
      actualCount: 114,
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

    expect(wrapper.text()).toContain('舷窗数据不完整：114/116')
    expect(wrapper.text()).toContain('缺失 17、68')
  })
})
