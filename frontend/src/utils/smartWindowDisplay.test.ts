import { describe, expect, it } from 'vitest'
import {
  isFirstRowWindow,
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

  it('maps 118 windows to two sides of 59', () => {
    expect(windowSide(1)).toBe('left')
    expect(windowSideSequence(59)).toBe(59)
    expect(windowSide(60)).toBe('right')
    expect(windowSideSequence(118)).toBe(59)
  })

  it('identifies only the first window on each side as the first row', () => {
    expect(isFirstRowWindow(1)).toBe(true)
    expect(isFirstRowWindow(60)).toBe(true)
    expect(isFirstRowWindow(2)).toBe(false)
    expect(isFirstRowWindow(61)).toBe(false)
  })

  it('uses 20, 19 and 20 windows per cabin zone on each side', () => {
    expect(windowZone(20)).toBe(1)
    expect(windowZone(21)).toBe(2)
    expect(windowZone(39)).toBe(2)
    expect(windowZone(40)).toBe(3)
    expect(windowZone(79)).toBe(1)
    expect(windowZone(80)).toBe(2)
    expect(windowZone(99)).toBe(3)
  })
})
