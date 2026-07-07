import { apiRequest } from './http'
import type { PassengerSmartWindowSnapshotDto } from './types'

export function getPassengerSmartWindows(): Promise<PassengerSmartWindowSnapshotDto> {
  return apiRequest<PassengerSmartWindowSnapshotDto>('/passenger-realtime/smart-windows')
}
