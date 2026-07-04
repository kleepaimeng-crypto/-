import { apiRequest } from './http'
import type {
  DataOptionsDto,
  DataRecordDetailDto,
  DataRecordListItemDto,
  PageDto,
  RecordMetadataDto,
} from './types'

export interface DataRecordQuery {
  tagIds?: string[]
  airlineCode?: string
  flightNo?: string
  sourceDeviceCode?: string
  aircraftModel?: string
  origin?: string
  destination?: string
  dataTypeCode?: string
  receivedFrom?: string
  receivedTo?: string
  includeDeleted?: boolean
  page: number
  pageSize: 20 | 50 | 100
  sortBy: 'dataType' | 'sentAt' | 'receivedAt'
  sortDirection: 'asc' | 'desc'
}

export interface MetadataUpdatePayload {
  aircraftRegistrationNo: string
  aircraftModel: string | null
  airlineCode: string | null
  flightNo: string | null
  origin: string | null
  destination: string | null
  sourceDeviceCode: string
  expectedVersion: number
}

export function getDataOptions(): Promise<DataOptionsDto> {
  return apiRequest<DataOptionsDto>('/data-options')
}

export function getDataRecords(query: DataRecordQuery): Promise<PageDto<DataRecordListItemDto>> {
  return apiRequest<PageDto<DataRecordListItemDto>>(`/data-records?${toSearchParams(query)}`)
}

export function getDataRecord(recordId: string): Promise<DataRecordDetailDto> {
  return apiRequest<DataRecordDetailDto>(`/data-records/${encodeURIComponent(recordId)}`)
}

export function updateRecordMetadata(
  recordId: string,
  payload: MetadataUpdatePayload,
): Promise<RecordMetadataDto> {
  return apiRequest<RecordMetadataDto>(`/data-records/${encodeURIComponent(recordId)}/metadata`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function deleteDataRecord(recordId: string, reason: string, expectedVersion: number): Promise<null> {
  return apiRequest<null>(`/data-records/${encodeURIComponent(recordId)}`, {
    method: 'DELETE',
    body: JSON.stringify({ reason, expectedVersion }),
  })
}

export function batchDeleteDataRecords(recordIds: string[], reason: string): Promise<{
  requested: number
  deleted: number
  skipped: number
}> {
  return apiRequest('/data-records/batch-delete', {
    method: 'POST',
    body: JSON.stringify({ recordIds, reason }),
  })
}

function toSearchParams(query: DataRecordQuery): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === '' || value === false) return
    params.set(key, Array.isArray(value) ? value.join(',') : String(value))
  })
  return params.toString()
}
