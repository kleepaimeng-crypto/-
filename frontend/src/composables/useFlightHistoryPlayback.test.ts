import { afterEach, describe, expect, it, vi } from 'vitest'
import { useFlightHistoryPlayback } from './useFlightHistoryPlayback'

const points = [
  point('2026-07-27T08:00:00+08:00', 1),
  point('2026-07-27T08:00:01+08:00', 2),
  point('2026-07-27T08:00:03+08:00', 3),
]

describe('useFlightHistoryPlayback', () => {
  afterEach(() => vi.useRealTimers())

  it('advances by real sample time and stops at the final point', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-27T08:00:00+08:00'))
    const playback = useFlightHistoryPlayback()
    playback.load(points)
    playback.toggle()

    vi.advanceTimersByTime(1000)
    expect(playback.currentIndex.value).toBe(1)
    vi.advanceTimersByTime(2000)
    expect(playback.currentIndex.value).toBe(2)
    expect(playback.playing.value).toBe(false)
  })

  it('seeks without keeping a previous timer alive', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-27T08:00:00+08:00'))
    const playback = useFlightHistoryPlayback()
    playback.load(points)
    playback.toggle()
    playback.seek(1)
    vi.advanceTimersByTime(5000)
    expect(playback.currentIndex.value).toBe(2)
    expect(playback.playing.value).toBe(false)
  })

  it('interpolates the current point while a segment is playing', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-27T08:00:00+08:00'))
    const playback = useFlightHistoryPlayback()
    playback.load([
      point('2026-07-27T08:00:00+08:00', 1),
      { ...point('2026-07-27T08:00:01+08:00', 2), longitude: 121 },
    ])
    playback.toggle()

    vi.advanceTimersByTime(500)

    expect(playback.currentIndex.value).toBe(0)
    expect(playback.currentPoint.value?.longitude).toBeGreaterThan(120)
    expect(playback.currentPoint.value?.longitude).toBeLessThan(121)
    expect(playback.flownPoints.value).toHaveLength(2)
  })
})

function point(sampleAt: string, frameCount: number) {
  return {
    sampleAt,
    sampleTimeText: sampleAt.slice(11, 19),
    frameCount,
    airGroundStatus: 'AIR',
    latitude: 30,
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
