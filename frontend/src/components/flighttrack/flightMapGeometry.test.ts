import { toLonLat } from 'ol/proj'
import { describe, expect, it } from 'vitest'
import type { FlightTrackPointDto } from '../../api/types'
import {
  bearingBetween,
  flownTrackPoints,
  projectTrack,
  trackHeading,
  validTrackPoints,
} from './flightMapGeometry'

function point(latitude: number, longitude: number, overrides: Partial<FlightTrackPointDto> = {}): FlightTrackPointDto {
  return {
    sampleAt: '2026-07-09T10:00:00+08:00',
    sampleTimeText: '10:00:00',
    frameCount: 1,
    latitude,
    longitude,
    altitudeFt: null,
    groundSpeedKt: null,
    computedAirSpeedKt: null,
    trackAngleDeg: null,
    headingDeg: null,
    pitchDeg: null,
    rollDeg: null,
    distanceToGoNm: null,
    destinationEtaText: null,
    ...overrides,
  }
}

describe('flight map geometry', () => {
  it('filters invalid coordinates and consecutive duplicates', () => {
    const duplicate = point(30, 120)
    const result = validTrackPoints([
      duplicate,
      { ...duplicate, frameCount: 2 },
      point(91, 120),
      point(31, 121),
    ])

    expect(result.map(({ latitude, longitude }) => [latitude, longitude])).toEqual([
      [30, 120],
      [31, 121],
    ])
  })

  it('projects longitude and latitude to Web Mercator', () => {
    const projected = projectTrack([point(39.9042, 116.4074)])[0]
    expect(toLonLat(projected)[0]).toBeCloseTo(116.4074, 5)
    expect(toLonLat(projected)[1]).toBeCloseTo(39.9042, 5)
  })

  it('sorts real points and removes every point after the current aircraft position', () => {
    const latest = point(30, 120, {
      sampleAt: '2026-07-09T10:00:00+08:00',
      frameCount: 20,
    })
    const result = flownTrackPoints([
      point(31, 121, { sampleAt: '2026-07-09T10:01:00+08:00', frameCount: 21 }),
      point(29, 119, { sampleAt: '2026-07-09T09:59:00+08:00', frameCount: 19 }),
    ], latest)

    expect(result.map(({ frameCount }) => frameCount)).toEqual([19, 20])
    expect(result.at(-1)).toBe(latest)
  })

  it.each([
    ['north', point(0, 0), point(1, 0), 0],
    ['east', point(0, 0), point(0, 1), 90],
    ['south', point(1, 0), point(0, 0), 180],
    ['west', point(0, 1), point(0, 0), 270],
  ])('calculates a %s heading', (_name, from, to, expected) => {
    expect(bearingBetween(from, to)).toBeCloseTo(expected, 5)
  })

  it('falls back to QAR track angle and then heading for a single point', () => {
    expect(trackHeading([], point(30, 120, { trackAngleDeg: 42, headingDeg: 50 }))).toBe(42)
    expect(trackHeading([], point(30, 120, { headingDeg: 50 }))).toBe(50)
  })
})
