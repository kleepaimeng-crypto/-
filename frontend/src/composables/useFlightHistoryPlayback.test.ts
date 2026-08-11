import { afterEach, describe, expect, it, vi } from 'vitest'
import { useFlightHistoryPlayback } from './useFlightHistoryPlayback'

const points = [
  point('2026-07-27T08:00:00+08:00', 1),
  point('2026-07-27T08:00:01+08:00', 2),
  point('2026-07-27T08:00:03+08:00', 3),
]

describe('useFlightHistoryPlayback', () => {
  afterEach(() => vi.useRealTimers())

  it('uses the new speed options and defaults to 5x', () => {
    const playback = useFlightHistoryPlayback()

    expect(playback.SPEEDS).toEqual([1, 2, 5, 10, 20])
    expect(playback.speed.value).toBe(5)

    playback.setSpeed(10)
    playback.load(points)
    expect(playback.speed.value).toBe(10)
  })

  it('advances by real sample time and stops at the final point', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-27T08:00:00+08:00'))
    const playback = useFlightHistoryPlayback()
    playback.load(points)
    playback.setSpeed(1)
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
    playback.setSpeed(1)
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
    playback.setSpeed(1)
    playback.toggle()

    vi.advanceTimersByTime(500)

    expect(playback.currentIndex.value).toBe(0)
    expect(playback.currentPoint.value?.longitude).toBeGreaterThan(120)
    expect(playback.currentPoint.value?.longitude).toBeLessThan(121)
    expect(playback.flownPoints.value).toHaveLength(2)
  })

  it('reschedules the active segment when the speed changes', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-27T08:00:00+08:00'))
    const playback = useFlightHistoryPlayback()
    playback.load(points.slice(0, 2))
    playback.setSpeed(1)
    playback.toggle()

    vi.advanceTimersByTime(400)
    expect(playback.currentIndex.value).toBe(0)

    playback.setSpeed(10)
    vi.advanceTimersByTime(100)
    expect(playback.currentIndex.value).toBe(1)
    expect(playback.playing.value).toBe(false)
  })

  it('keeps a fixed ten-minute chart domain and scrolls it after the first window', () => {
    const playback = useFlightHistoryPlayback()
    const track = [
      point('2026-07-27T08:00:00+08:00', 1),
      point('2026-07-27T08:05:00+08:00', 2),
      point('2026-07-27T08:11:00+08:00', 3),
    ]
    playback.load(track)

    expect(playback.chartDomain.value).toEqual({
      startAtMs: Date.parse('2026-07-27T08:00:00+08:00'),
      endAtMs: Date.parse('2026-07-27T08:10:00+08:00'),
    })
    expect(playback.chartPoints.value).toEqual([track[0]])

    playback.seek(0.5)
    expect(playback.chartDomain.value).toEqual({
      startAtMs: Date.parse('2026-07-27T08:00:00+08:00'),
      endAtMs: Date.parse('2026-07-27T08:10:00+08:00'),
    })
    expect(playback.chartPoints.value).toEqual(track.slice(0, 2))

    playback.seek(1)
    expect(playback.chartDomain.value).toEqual({
      startAtMs: Date.parse('2026-07-27T08:01:00+08:00'),
      endAtMs: Date.parse('2026-07-27T08:11:00+08:00'),
    })
    expect(playback.chartPoints.value).toEqual(track.slice(1))
  })

  it('moves the rolling chart domain continuously between real samples', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-27T08:10:00+08:00'))
    const playback = useFlightHistoryPlayback()
    playback.load([
      point('2026-07-27T08:00:00+08:00', 1),
      point('2026-07-27T08:10:00+08:00', 2),
      point('2026-07-27T08:10:10+08:00', 3),
    ])
    playback.seek(0.5)
    playback.setSpeed(1)
    playback.toggle()

    vi.advanceTimersByTime(5000)

    expect(playback.currentIndex.value).toBe(1)
    expect(playback.playbackTimeMs.value).toBeGreaterThan(Date.parse('2026-07-27T08:10:04+08:00'))
    expect(playback.playbackTimeMs.value).toBeLessThan(Date.parse('2026-07-27T08:10:06+08:00'))
    expect((playback.chartDomain.value?.endAtMs ?? 0) - (playback.chartDomain.value?.startAtMs ?? 0))
      .toBe(10 * 60 * 1000)
  })

  it('downsamples chart points by speed while preserving the window endpoints', () => {
    const playback = useFlightHistoryPlayback()
    const track = Array.from({ length: 600 }, (_, index) => point(
      new Date(Date.parse('2026-07-27T08:00:00+08:00') + index * 1000).toISOString(),
      index,
    ))
    playback.load(track)
    playback.seek(1)

    const expectedLimits = [
      [1, 600],
      [2, 300],
      [5, 120],
      [10, 60],
      [20, 30],
    ] as const

    for (const [speed, limit] of expectedLimits) {
      playback.setSpeed(speed)
      expect(playback.chartPoints.value).toHaveLength(limit)
      expect(playback.chartPoints.value[0].sampleAt).toBe(track[0].sampleAt)
      expect(playback.chartPoints.value.at(-1)?.sampleAt).toBe(track.at(-1)?.sampleAt)
    }
  })

  it('keeps completed time buckets stable as the current bucket receives new points', () => {
    const playback = useFlightHistoryPlayback()
    const track = Array.from({ length: 16 }, (_, index) => point(
      new Date(Date.parse('2026-07-27T08:00:00+08:00') + index * 1000).toISOString(),
      index,
    ))
    playback.load(track)
    playback.setSpeed(5)
    playback.seek(10 / 15)
    const previousBuckets = playback.chartPoints.value.slice(0, -1).map((item) => item.sampleAt)

    playback.seek(11 / 15)

    expect(playback.chartPoints.value.slice(0, -1).map((item) => item.sampleAt))
      .toEqual(previousBuckets)
    expect(playback.chartPoints.value.at(-1)?.sampleAt).toBe(track[11].sampleAt)
  })

  it('handles empty and single-point tracks without exposing future data', () => {
    const playback = useFlightHistoryPlayback()
    expect(playback.chartDomain.value).toBeNull()
    expect(playback.chartPoints.value).toEqual([])

    const singlePoint = point('2026-07-27T08:00:00+08:00', 1)
    playback.load([singlePoint])
    expect(playback.chartPoints.value).toEqual([singlePoint])
    expect((playback.chartDomain.value?.endAtMs ?? 0) - (playback.chartDomain.value?.startAtMs ?? 0))
      .toBe(10 * 60 * 1000)
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
