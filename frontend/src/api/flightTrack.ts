import { apiRequest } from './http'
import type { FlightTrackCurrentDto } from './types'

export function getFlightTrackCurrent(): Promise<FlightTrackCurrentDto | null | undefined> {
  return apiRequest<FlightTrackCurrentDto | null | undefined>('/flight-track/current')
}
