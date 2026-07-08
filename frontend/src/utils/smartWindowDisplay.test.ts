import { describe, expect, it } from 'vitest'
import {
  windowSide,
  windowSideSequence,
  windowVisualBrightness,
  windowZone,
} from './smartWindowDisplay'

describe('smartWindowDisplay', () => {
  it('maps brightness levels to the agreed visible range', () => {
    expect(windowVisualBrightness(0)).toBeCloseTo(0.35)
    expect(windowVisualBrightness(5)).toBeCloseTo(0.675)
    expect(windowVisualBrightness(10)).toBeCloseTo(1)
    expect(windowVisualBrightness(99)).toBeCloseTo(1)
  })

  it('maps 116 windows to two sides of 58', () => {
    expect(windowSide(1)).toBe('left')
    expect(windowSideSequence(58)).toBe(58)
    expect(windowSide(59)).toBe('right')
    expect(windowSideSequence(116)).toBe(58)
  })

  it('uses 17, 20 and 21 windows per cabin zone on each side', () => {
    expect(windowZone(17)).toBe(1)
    expect(windowZone(18)).toBe(2)
    expect(windowZone(37)).toBe(2)
    expect(windowZone(38)).toBe(3)
    expect(windowZone(75)).toBe(1)
    expect(windowZone(76)).toBe(2)
    expect(windowZone(96)).toBe(3)
  })
})
