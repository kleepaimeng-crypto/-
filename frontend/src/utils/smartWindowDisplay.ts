export type PassengerWindowSide = 'left' | 'right'

export const C929_WINDOWS_PER_SIDE = 59
export const C929_WINDOW_COUNT = C929_WINDOWS_PER_SIDE * 2

export function windowVisualBrightness(brightnessLevel: number): number {
  const level = Math.max(0, Math.min(10, brightnessLevel))
  return 0.35 + level * 0.065
}

export function windowSide(windowId: number): PassengerWindowSide {
  return windowId <= C929_WINDOWS_PER_SIDE ? 'left' : 'right'
}

export function windowSideSequence(windowId: number): number {
  return windowId <= C929_WINDOWS_PER_SIDE ? windowId : windowId - C929_WINDOWS_PER_SIDE
}

export function isFirstRowWindow(windowId: number): boolean {
  return windowSideSequence(windowId) === 1
}

export function windowZone(windowId: number): 1 | 2 | 3 {
  const sequence = windowSideSequence(windowId)
  if (sequence <= 20) return 1
  if (sequence <= 39) return 2
  return 3
}
