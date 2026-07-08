export const FIXED_CANVAS_WIDTH = 1920
export const FIXED_CANVAS_HEIGHT = 1080

export function calculateFixedCanvasScale(
  viewportWidth: number,
  viewportHeight: number,
): number {
  if (viewportWidth <= 0 || viewportHeight <= 0) {
    return 1
  }

  return Math.min(
    viewportWidth / FIXED_CANVAS_WIDTH,
    viewportHeight / FIXED_CANVAS_HEIGHT,
  )
}
