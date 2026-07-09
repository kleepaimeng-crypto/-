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
export type WindowSide = 'LEFT' | 'RIGHT'
export type SmartWindowStatus = 'NORMAL' | 'FAULT' | 'TEST'

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

export interface TrafficTaskDto {
  taskId: string
  flightNo: string
  scenarioName: string
  status: string
  phase: string | null
  terminalCount: number
  startedAt: string
  statisticsWindowSeconds: number
}

export interface TrafficWindowDto {
  from: string
  to: string
}

export interface TrafficTotalsDto {
  bytesCount: number
  packetCount: number
  activeTerminalCount: number
  activeSessionCount: number
  averageThroughputMbps: number
  peakMbps: number
  packetLossRate: number | null
}

export interface TrafficApplicationStatDto {
  application: string
  bytesCount: number
  packetCount: number
  averageThroughputMbps: number
  peakMbps: number
  activeTerminalCount: number
  packetLossRate: number | null
}

export interface TrafficTerminalStatDto {
  terminalId: string
  displayTerminalId: string | null
  seatLabel: string | null
  application: string
  averageThroughputMbps: number
  peakMbps: number
  bytesCount: number
}

export interface TrafficTimelinePointDto {
  windowStart: string
  windowEnd: string
  averageThroughputMbps: number
  peakMbps: number
  bytesCount: number
}

export interface TrafficOverviewDto {
  task: TrafficTaskDto | null
  window: TrafficWindowDto | null
  totals: TrafficTotalsDto
  applicationStats: TrafficApplicationStatDto[]
  topTerminals: TrafficTerminalStatDto[]
  timeline: TrafficTimelinePointDto[]
}

export interface TrafficRecordDto {
  windowStart: string
  windowEnd: string
  taskId: string
  terminalId: string
  displayTerminalId: string | null
  seatLabel: string | null
  application: string
  protocol: string
  direction: string
  bytesCount: number
  packetCount: number
  throughputMbps: number
  peakMbps: number
  recordStatus: string
}

export interface SmartWindowSummaryDto {
  windowCount: number
  connectedCount: number
  faultCount: number
  testCount: number
  averageBrightnessLevel: number | null
}

export interface SmartWindowZoneDto {
  zoneId: number
  name: string
  windowCount: number
  connectedCount: number
  faultCount: number
  averageBrightnessLevel: number | null
}

export interface SmartWindowItemDto {
  windowId: number
  label: string | null
  zoneId: number
  side: WindowSide
  rowNo: number
  positionNo: number
  brightnessLevel: number
  connectStatus: boolean
  status: SmartWindowStatus
  eventAt: string
}

export interface SmartWindowDisplayDto {
  flightNo: string | null
  aircraftRegistrationNo: string | null
  snapshotAt: string | null
  summary: SmartWindowSummaryDto
  zones: SmartWindowZoneDto[]
  windows: SmartWindowItemDto[]
}

export interface PassengerSmartWindowSummaryDto {
  averageBrightness: number | null
  disconnectedCount: number
  faultCount: number
  testCount: number
}

export interface PassengerSmartWindowItemDto {
  windowId: number
  zoneId: number
  brightnessLevel: number
  connected: boolean
  status: SmartWindowStatus
  updatedAt: string
  sourceRecordId: string
}

export interface PassengerSmartWindowSnapshotDto {
  hasData: boolean
  complete: boolean
  expectedCount: number
  actualCount: number
  missingWindowIds: number[]
  sourceRecordId: string | null
  updatedAt: string | null
  summary: PassengerSmartWindowSummaryDto
  windows: PassengerSmartWindowItemDto[]
}

export type PassengerActivityKind = 'VIDEO' | 'MUSIC' | 'BROWSING' | 'OTHER' | 'IDLE'

export interface MediaRankingItemDto {
  type: string
  count: number
}

export interface PassengerMediaStatisticsDto {
  videoTotalCount: number
  videoRanking: MediaRankingItemDto[]
  musicTotalCount: number
  musicRanking: MediaRankingItemDto[]
}

export interface PassengerActivityDto {
  passengerId: string | null
  seatNo: string
  cabinClass: 'BUSINESS' | 'ECONOMY'
  behaviorType: string | null
  activityKind: PassengerActivityKind
  title: string | null
  types: string[]
  action: string | null
  domain: string | null
  url: string | null
  trafficBytes: number | null
  bandwidthMbps: number | null
  windowBytes: number | null
  eventAt: string | null
  bandwidthUpdatedAt: string | null
  sourceRecordId: string | null
}

export interface PassengerActivitiesDto {
  total: number
  items: PassengerActivityDto[]
}

export interface PassengerRealtimeSnapshotDto {
  hasData: boolean
  updatedAt: string | null
  mediaStatistics: PassengerMediaStatisticsDto
  passengerActivities: PassengerActivitiesDto
}

export interface FlightTrackPointDto {
  sampleAt: string
  sampleTimeText: string
  frameCount: number
  latitude: number
  longitude: number
  altitudeFt: number | null
  groundSpeedKt: number | null
  computedAirSpeedKt: number | null
  trackAngleDeg: number | null
  headingDeg: number | null
  pitchDeg: number | null
  rollDeg: number | null
  distanceToGoNm: number | null
  destinationEtaText: string | null
}

export interface FlightTrackInfoDto {
  aircraftRegistrationNo: string | null
  aircraftModel: string | null
  airlineCode: string | null
  airlineName: string | null
  flightNo: string
  originAirportCode: string
  originAirportName: string
  destinationAirportCode: string
  destinationAirportName: string
  statusText: string
  lastUpdatedAt: string
}

export interface FlightTrackCurrentDto {
  flight: FlightTrackInfoDto
  latestPoint: FlightTrackPointDto
  startAt: string
  endAt: string
  pollIntervalSeconds: number
  freshnessSeconds: number
  track: FlightTrackPointDto[]
}
