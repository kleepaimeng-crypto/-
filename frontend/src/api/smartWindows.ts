import { apiRequest } from './http'
import type { SmartWindowDisplayDto } from './types'

export interface SmartWindowDisplayQuery {
  flightNo?: string
  zoneId?: number
  includeDisabled?: boolean
}

export function getSmartWindowDisplay(query: SmartWindowDisplayQuery = {}): Promise<SmartWindowDisplayDto> {
  const params = new URLSearchParams()
  if (query.flightNo) params.set('flightNo', query.flightNo)
  if (query.zoneId !== undefined) params.set('zoneId', String(query.zoneId))
  if (query.includeDisabled !== undefined) params.set('includeDisabled', String(query.includeDisabled))

  const search = params.toString()
  return apiRequest<SmartWindowDisplayDto>(`/smart-windows/display${search ? `?${search}` : ''}`)
}
