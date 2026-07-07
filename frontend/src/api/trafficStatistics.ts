import { apiRequest } from './http'
import type { PageDto, TrafficOverviewDto, TrafficRecordDto } from './types'

export interface TrafficOverviewQuery {
  taskId?: string
  flightNo?: string
  windowFrom?: string
  windowTo?: string
  limit?: number
}

export interface TrafficDetailQuery {
  terminalId?: string
  application?: string
  windowFrom?: string
  windowTo?: string
  page: number
  pageSize: 20 | 50 | 100
}

export function getTrafficOverview(query: TrafficOverviewQuery = {}): Promise<TrafficOverviewDto> {
  const params = toSearchParams(query)
  return apiRequest<TrafficOverviewDto>(`/traffic-statistics/overview${params}`)
}

export function getTaskTraffic(
  taskId: string,
  query: TrafficDetailQuery,
): Promise<PageDto<TrafficRecordDto>> {
  return apiRequest<PageDto<TrafficRecordDto>>(
    `/traffic-statistics/tasks/${encodeURIComponent(taskId)}/traffic${toSearchParams(query)}`,
  )
}

export function getTerminalTraffic(
  taskId: string,
  terminalId: string,
  query: Omit<TrafficDetailQuery, 'terminalId'>,
): Promise<PageDto<TrafficRecordDto>> {
  return apiRequest<PageDto<TrafficRecordDto>>(
    `/traffic-statistics/tasks/${encodeURIComponent(taskId)}/terminals/${encodeURIComponent(terminalId)}/traffic${toSearchParams(query)}`,
  )
}

function toSearchParams(query: object): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (typeof value !== 'string' && typeof value !== 'number') return
    if (value === '') return
    params.set(key, String(value))
  })
  const search = params.toString()
  return search ? `?${search}` : ''
}
