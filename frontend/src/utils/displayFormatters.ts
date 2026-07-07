import { ApiClientError } from '../api/http'
import type { SmartWindowItemDto } from '../api/types'

export function statusLabel(windowItem: SmartWindowItemDto): string {
  if (!windowItem.connectStatus) return '断连'
  if (windowItem.status === 'FAULT') return '故障'
  if (windowItem.status === 'TEST') return '测试'
  return '正常'
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

export function formatCount(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return new Intl.NumberFormat('zh-CN').format(value)
}

export function formatBytes(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  if (value >= 1024 ** 4) return `${(value / 1024 ** 4).toFixed(2)} TB`
  if (value >= 1024 ** 3) return `${(value / 1024 ** 3).toFixed(2)} GB`
  if (value >= 1024 ** 2) return `${(value / 1024 ** 2).toFixed(2)} MB`
  return `${formatCount(value)} B`
}

export function formatMbps(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value.toFixed(1)} Mbps`
}

export function formatBrightness(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return value.toFixed(1)
}

export function barWidth(value: number, max: number): string {
  if (max <= 0) return '0%'
  return `${Math.max(4, Math.min(100, (value / max) * 100)).toFixed(1)}%`
}

export function toMessage(error: unknown): string {
  if (error instanceof ApiClientError) return error.message
  if (error instanceof Error) return error.message
  return '请求失败'
}
