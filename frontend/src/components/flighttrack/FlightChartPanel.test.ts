import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FlightChartPanel from './FlightChartPanel.vue'

const series = [{ key: 'latitude' as const, label: '纬度', color: '#8279ff' }]
const startAtMs = Date.parse('2026-07-27T08:00:00+08:00')
const points = [
  point(startAtMs, 30),
  point(startAtMs + 5 * 60 * 1000, 31),
]

describe('FlightChartPanel', () => {
  it('uses the provided fixed time domain for plotting and labels', () => {
    const wrapper = mount(FlightChartPanel, {
      props: {
        title: '经纬度',
        points,
        series,
        currentIndex: 1,
        cursorAtMs: startAtMs + 7.5 * 60 * 1000,
        timeDomain: {
          startAtMs,
          endAtMs: startAtMs + 10 * 60 * 1000,
        },
      },
    })

    expect(wrapper.find('.chart-series-line').attributes('d')).toContain('M 44.0')
    expect(wrapper.find('.chart-series-line').attributes('d')).toContain('L 226.0')
    expect(wrapper.findAll('.chart-x-label')).toHaveLength(4)
    expect(wrapper.find('.chart-playback-cursor').attributes('d')).toContain('M 317.0')
  })

  it('keeps the existing automatic point range when no time domain is provided', () => {
    const wrapper = mount(FlightChartPanel, {
      props: {
        title: '经纬度',
        points,
        series,
      },
    })

    expect(wrapper.find('.chart-series-line').attributes('d')).toContain('M 44.0')
    expect(wrapper.find('.chart-series-line').attributes('d')).toContain('L 408.0')
  })

  it('moves existing points left when the fixed time domain advances', async () => {
    const wrapper = mount(FlightChartPanel, {
      props: {
        title: '经纬度',
        points: [points[1]],
        series,
        timeDomain: {
          startAtMs,
          endAtMs: startAtMs + 10 * 60 * 1000,
        },
      },
    })

    expect(wrapper.find('.chart-series-line').attributes('d')).toContain('M 226.0')

    await wrapper.setProps({
      timeDomain: {
        startAtMs: startAtMs + 60 * 1000,
        endAtMs: startAtMs + 11 * 60 * 1000,
      },
    })

    expect(wrapper.find('.chart-series-line').attributes('d')).toContain('M 189.6')
  })

  it('keeps the fixed domain when telemetry values are missing', () => {
    const wrapper = mount(FlightChartPanel, {
      props: {
        title: '经纬度',
        points: points.map((item) => ({ ...item, latitude: null })),
        series,
        timeDomain: {
          startAtMs,
          endAtMs: startAtMs + 10 * 60 * 1000,
        },
      },
    })

    expect(wrapper.find('.chart-series-line').attributes('d')).toBe('')
    expect(wrapper.findAll('.chart-x-label')).toHaveLength(4)
  })
})

function point(time: number, latitude: number) {
  const sampleAt = new Date(time).toISOString()
  return {
    sampleAt,
    sampleTimeText: sampleAt.slice(11, 19),
    frameCount: latitude,
    latitude,
    longitude: 120,
    altitudeFt: 35000,
    groundSpeedKt: 450,
    computedAirSpeedKt: 280,
    trackAngleDeg: 90,
    headingDeg: 90,
    pitchDeg: 1,
    rollDeg: 0,
    distanceToGoNm: 100,
    destinationEtaText: '00:20',
  }
}
