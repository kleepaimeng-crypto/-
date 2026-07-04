import { apiDownload, apiRequest } from './http'
import type { DataTypeCode, ExportFormat, FileJobSummaryDto, PageDto } from './types'

export interface ImportCreatePayload {
  dataTypeCode: DataTypeCode
  file: File
  sourceDeviceCode: string
  aircraftRegistrationNo: string
  aircraftModel?: string
  airlineCode?: string
  flightNo?: string
  origin?: string
  destination?: string
}

export interface ExportCreatePayload {
  format: ExportFormat
  filters: {
    dataTypeCode: string | null
    tagIds: string[]
    airlineCode: string | null
    flightNo: string | null
    sourceDeviceCode: string | null
    aircraftModel: string | null
    origin: string | null
    destination: string | null
    receivedFrom: string | null
    receivedTo: string | null
  }
  sortBy: 'dataType' | 'sentAt' | 'receivedAt'
  sortDirection: 'asc' | 'desc'
}

export function createImport(payload: ImportCreatePayload): Promise<FileJobSummaryDto> {
  const body = new FormData()
  Object.entries(payload).forEach(([key, value]) => {
    if (value !== undefined && value !== '') body.append(key, value)
  })
  return apiRequest<FileJobSummaryDto>('/imports', { method: 'POST', body })
}

export function getImportHistory(page = 1, pageSize = 20): Promise<PageDto<FileJobSummaryDto>> {
  return apiRequest<PageDto<FileJobSummaryDto>>(`/imports?page=${page}&pageSize=${pageSize}`)
}

export function getImportJob(jobId: string): Promise<FileJobSummaryDto> {
  return apiRequest<FileJobSummaryDto>(`/imports/${encodeURIComponent(jobId)}`)
}

export function downloadImportTemplate(dataTypeCode: DataTypeCode): ReturnType<typeof apiDownload> {
  return apiDownload(`/imports/templates/${encodeURIComponent(dataTypeCode)}`)
}

export function downloadImportErrorFile(jobId: string): ReturnType<typeof apiDownload> {
  return apiDownload(`/imports/${encodeURIComponent(jobId)}/error-file`)
}

export function createExport(payload: ExportCreatePayload): Promise<FileJobSummaryDto> {
  return apiRequest<FileJobSummaryDto>('/exports', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getExportHistory(page = 1, pageSize = 20): Promise<PageDto<FileJobSummaryDto>> {
  return apiRequest<PageDto<FileJobSummaryDto>>(`/exports?page=${page}&pageSize=${pageSize}`)
}

export function getExportJob(jobId: string): Promise<FileJobSummaryDto> {
  return apiRequest<FileJobSummaryDto>(`/exports/${encodeURIComponent(jobId)}`)
}

export function downloadExportFile(jobId: string): ReturnType<typeof apiDownload> {
  return apiDownload(`/exports/${encodeURIComponent(jobId)}/file`)
}
