import { apiRequest } from './http'
import type { CockpitVideoConfigDto, PassengerRealtimeSnapshotDto } from './types'

export function getPassengerRealtimeSnapshot(): Promise<PassengerRealtimeSnapshotDto> {
  return apiRequest<PassengerRealtimeSnapshotDto>('/passenger-realtime/snapshot')
}

export function getCockpitVideoConfig(): Promise<CockpitVideoConfigDto> {
  return apiRequest<CockpitVideoConfigDto>('/passenger-realtime/cockpit-video')
}
