export type PassengerWindowSide = 'left' | 'right'

export function windowVisualBrightness(brightnessLevel: number): number {
  const level = Math.max(0, Math.min(10, brightnessLevel))
  return 0.35 + level * 0.065
}

export function windowSide(windowId: number): PassengerWindowSide {
  return windowId <= 58 ? 'left' : 'right'
}

export function windowSideSequence(windowId: number): number {
  return windowId <= 58 ? windowId : windowId - 58
}

export function windowZone(windowId: number): 1 | 2 | 3 {
  const sequence = windowSideSequence(windowId)
  if (sequence <= 17) return 1
  if (sequence <= 37) return 2
  return 3
}
