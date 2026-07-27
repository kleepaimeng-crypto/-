import { apiRequest } from './http'
import type { FlightFinishReason, FlightHistorySessionDto, FlightHistoryTrackDto, PageDto } from './types'

export interface FlightHistoryQuery {
  endedFrom: string
  endedTo: string
  flightNo?: string
  origin?: string
  destination?: string
  aircraftRegistrationNo?: string
  finishReason?: FlightFinishReason
  page: number
  pageSize: 20 | 50 | 100
  sortBy: 'startedAt' | 'endedAt' | 'flightNo' | 'pointCount'
  sortDirection: 'asc' | 'desc'
}

export function getFlightHistorySessions(query: FlightHistoryQuery): Promise<PageDto<FlightHistorySessionDto>> {
  const params = new URLSearchParams({
    endedFrom: query.endedFrom,
    endedTo: query.endedTo,
    page: String(query.page),
    pageSize: String(query.pageSize),
    sortBy: query.sortBy,
    sortDirection: query.sortDirection,
  })
  for (const key of ['flightNo', 'origin', 'destination', 'aircraftRegistrationNo', 'finishReason'] as const) {
    if (query[key]) params.set(key, query[key])
  }
  return apiRequest<PageDto<FlightHistorySessionDto>>(`/flight-history/sessions?${params}`)
}

export function getFlightHistoryTrack(sessionId: string): Promise<FlightHistoryTrackDto> {
  return apiRequest<FlightHistoryTrackDto>(`/flight-history/sessions/${sessionId}/track?maxPoints=3600`)
}
