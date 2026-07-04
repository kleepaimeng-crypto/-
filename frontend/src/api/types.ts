export interface ApiEnvelope<T> {
  code: 'OK'
  message: string
  data: T
  traceId: string
}

export interface ApiErrorDetail {
  field: string
  reason: string
}

export interface ApiErrorEnvelope {
  code: string
  message: string
  details: ApiErrorDetail[]
  traceId: string
}

export interface UserDto {
  id: string
  username: string
  email: string | null
  roleCode: 'ADMIN'
}

export interface LoginResponseDto {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  user: UserDto
}

export interface PageDto<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export type DataTypeCode =
  | 'QAR'
  | 'GROUND_TASK'
  | 'GROUND_TRAFFIC_RECORD'
  | 'GROUND_SESSION_SUMMARY'
  | 'SMART_WINDOW_STATUS'
  | 'IFE_633_BEHAVIOR'
  | 'IFE_COCKRELL_BEHAVIOR'

export type ParseStatus = 'RECEIVED' | 'PARSED' | 'PARTIAL' | 'FAILED'
export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED'
export type ExportFormat = 'CSV' | 'PDF'

export interface CodeNameOption {
  code: string
  name: string
}

export interface TagDto {
  id: string
  name: string
  color: string
}

export interface DataOptionsDto {
  dataTypes: CodeNameOption[]
  airlines: CodeNameOption[]
  aircraftModels: string[]
  aircraftRegistrations: string[]
  devices: CodeNameOption[]
  airports: string[]
  tags: TagDto[]
}

export interface DataRecordListItemDto {
  id: string
  aircraftRegistrationNo: string
  aircraftModel: string | null
  airlineCode: string | null
  flightNo: string | null
  origin: string | null
  destination: string | null
  sourceDevice: CodeNameOption
  dataType: CodeNameOption
  sentAt: string
  receivedAt: string
  payloadCount: number
  parseStatus: ParseStatus
  tags: TagDto[]
  deleted: boolean
  version: number
}

export interface RecordMetadataDto {
  aircraftRegistrationNo: string
  aircraftModel?: string | null
  airlineCode?: string | null
  flightNo?: string | null
  origin?: string | null
  destination?: string | null
  dataTypeCode?: string
  sourceDeviceCode: string
  sourceSystemCode?: string
  sentAt?: string
  receivedAt?: string
  parseStatus?: ParseStatus
  parseError?: string | null
  version: number
}

export interface AnnotationDto {
  id: string
  content: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface DataRecordDetailDto {
  id: string
  metadata: RecordMetadataDto
  rawPayload: Record<string, unknown> | null
  rawText: string | null
  parsedSummary: Record<string, unknown>
  tags: TagDto[]
  annotations: AnnotationDto[]
  deleted: boolean
}

export interface FileJobSummaryDto {
  id: string
  status: JobStatus
  dataTypeCode: DataTypeCode
  fileName?: string | null
  format?: ExportFormat
  totalRows?: number
  successRows?: number
  failedRows?: number
  createdAt: string
  completedAt?: string | null
  requestedBy?: UserDto | null
}
