import { fromLonLat } from 'ol/proj'
import type { Coordinate } from 'ol/coordinate'
import type { FlightTrackPointDto } from '../../api/types'

export function flownTrackPoints(
  track: FlightTrackPointDto[],
  latestPoint: FlightTrackPointDto,
): FlightTrackPointDto[] {
  const ordered = [...track, latestPoint]
    .filter((point) => compareTrackPoints(point, latestPoint) <= 0)
    .sort(compareTrackPoints)
  return validTrackPoints(ordered)
}

export function validTrackPoints(track: FlightTrackPointDto[]): FlightTrackPointDto[] {
  const valid: FlightTrackPointDto[] = []
  for (const point of track) {
    if (
      !Number.isFinite(point.longitude)
      || !Number.isFinite(point.latitude)
      || point.longitude < -180
      || point.longitude > 180
      || point.latitude < -90
      || point.latitude > 90
    ) {
      continue
    }
    const previous = valid.at(-1)
    if (previous?.longitude === point.longitude && previous.latitude === point.latitude) {
      continue
    }
    valid.push(point)
  }
  return valid
}

export function projectTrack(points: FlightTrackPointDto[]): Coordinate[] {
  return points.map((point) => fromLonLat([point.longitude, point.latitude]))
}

export function trackHeading(points: FlightTrackPointDto[], fallbackPoint?: FlightTrackPointDto): number {
  const latest = points.at(-1)
  const previous = points.at(-2)
  if (latest && previous) return bearingBetween(previous, latest)
  return firstFiniteNumber(fallbackPoint?.trackAngleDeg, fallbackPoint?.headingDeg) ?? 0
}

export function bearingBetween(from: FlightTrackPointDto, to: FlightTrackPointDto): number {
  const fromLat = toRadians(from.latitude)
  const toLat = toRadians(to.latitude)
  const deltaLon = toRadians(to.longitude - from.longitude)
  const y = Math.sin(deltaLon) * Math.cos(toLat)
  const x = Math.cos(fromLat) * Math.sin(toLat)
    - Math.sin(fromLat) * Math.cos(toLat) * Math.cos(deltaLon)
  return (toDegrees(Math.atan2(y, x)) + 360) % 360
}

function compareTrackPoints(left: FlightTrackPointDto, right: FlightTrackPointDto): number {
  const leftTime = Date.parse(left.sampleAt)
  const rightTime = Date.parse(right.sampleAt)
  if (Number.isFinite(leftTime) && Number.isFinite(rightTime) && leftTime !== rightTime) {
    return leftTime - rightTime
  }
  if (left.sampleAt !== right.sampleAt) {
    return left.sampleAt.localeCompare(right.sampleAt)
  }
  return left.frameCount - right.frameCount
}

function firstFiniteNumber(...values: Array<number | null | undefined>): number | null {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) return value
  }
  return null
}

function toRadians(value: number): number {
  return value * Math.PI / 180
}

function toDegrees(value: number): number {
  return value * 180 / Math.PI
}
