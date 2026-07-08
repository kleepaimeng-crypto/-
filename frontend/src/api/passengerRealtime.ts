import { apiRequest } from './http'
import type { PassengerRealtimeSnapshotDto } from './types'

export function getPassengerRealtimeSnapshot(): Promise<PassengerRealtimeSnapshotDto> {
  return apiRequest<PassengerRealtimeSnapshotDto>('/passenger-realtime/snapshot')
}
