import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PassengerTrafficPanel from './PassengerTrafficPanel.vue'
import type { PassengerRealtimeSnapshotDto } from '../../api/types'

const snapshot: PassengerRealtimeSnapshotDto = {
  hasData: true,
  updatedAt: '2026-07-07T10:00:00+08:00',
  mediaStatistics: {
    videoTotalCount: 57,
    videoRanking: [{ type: '奇幻', count: 57 }, { type: '爱情', count: 50 }],
    musicTotalCount: 48,
    musicRanking: [{ type: '民谣', count: 48 }],
  },
  passengerActivities: { total: 237, items: [] },
}

describe('PassengerTrafficPanel', () => {
  it('renders integer media counts without Mbps', async () => {
    const wrapper = mount(PassengerTrafficPanel, {
      props: { autoRefresh: true, snapshot, loading: false, error: '' },
    })

    expect(wrapper.text()).toContain('当前影音类型排行')
    expect(wrapper.text()).toContain('当前观看视频：57人')
    expect(wrapper.text()).toContain('当前收听音乐：48人')
    expect(wrapper.text()).toContain('奇幻')
    expect(wrapper.text()).not.toContain('Mbps')

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('toggleAutoRefresh')).toHaveLength(1)
  })

  it('renders an empty state without fake ranking rows', () => {
    const emptySnapshot: PassengerRealtimeSnapshotDto = {
      ...snapshot,
      hasData: false,
      mediaStatistics: {
        videoTotalCount: 0,
        videoRanking: [],
        musicTotalCount: 0,
        musicRanking: [],
      },
    }
    const wrapper = mount(PassengerTrafficPanel, {
      props: { autoRefresh: false, snapshot: emptySnapshot, loading: false, error: '' },
    })

    expect(wrapper.text()).toContain('当前暂无视频观看乘客')
    expect(wrapper.text()).toContain('当前暂无音乐收听乘客')
  })
})
