import type { SmartWindowDisplayDto, SmartWindowItemDto, SmartWindowZoneDto, WindowSide } from '../api/types'

export type CabinZoneId = 1 | 2 | 3
export type CabinZoneSelection = 'ALL' | CabinZoneId

export interface CabinZoneLayout {
  zoneId: CabinZoneId
  name: string
  rangeStart: number
  rangeEnd: number
  seatGroups: number[]
  cabinClass: 'premium' | 'mixed' | 'economy'
  servicePositions: number[]
}

export interface CabinSlot {
  positionNo: number
  rowLabel: string
  serviceLabel: string | null
  seatGroups: number[]
  leftWindow: CabinWindowSlot
  rightWindow: CabinWindowSlot
}

export interface CabinWindowSlot {
  side: WindowSide
  positionNo: number
  label: string
  data: SmartWindowItemDto | null
}

export interface CabinZoneView extends CabinZoneLayout {
  summary: SmartWindowZoneDto | null
  slots: CabinSlot[]
}

export const CABIN_ZONES: CabinZoneLayout[] = [
  {
    zoneId: 1,
    name: '前舱',
    rangeStart: 1,
    rangeEnd: 34,
    seatGroups: [2, 2, 2],
    cabinClass: 'premium',
    servicePositions: [1, 2, 15, 16, 33, 34],
  },
  {
    zoneId: 2,
    name: '中舱',
    rangeStart: 35,
    rangeEnd: 72,
    seatGroups: [3, 4, 3],
    cabinClass: 'mixed',
    servicePositions: [35, 36, 55, 56, 71, 72],
  },
  {
    zoneId: 3,
    name: '后舱',
    rangeStart: 73,
    rangeEnd: 100,
    seatGroups: [3, 4, 3],
    cabinClass: 'economy',
    servicePositions: [73, 74, 99, 100],
  },
]

export function buildCabinSections(display: SmartWindowDisplayDto | null): CabinZoneView[] {
  const windowMap = new Map<string, SmartWindowItemDto>()
  display?.windows.forEach((item) => {
    windowMap.set(`${item.side}:${item.positionNo}`, item)
  })

  return CABIN_ZONES.map((zone) => ({
    ...zone,
    summary: display?.zones.find((item) => item.zoneId === zone.zoneId) ?? null,
    slots: Array.from({ length: zone.rangeEnd - zone.rangeStart + 1 }, (_, index) => {
      const positionNo = zone.rangeStart + index
      return {
        positionNo,
        rowLabel: positionNo.toString().padStart(2, '0'),
        serviceLabel: serviceLabel(zone, positionNo),
        seatGroups: zone.seatGroups,
        leftWindow: buildWindowSlot('LEFT', positionNo, windowMap),
        rightWindow: buildWindowSlot('RIGHT', positionNo, windowMap),
      }
    }),
  }))
}

function buildWindowSlot(
  side: WindowSide,
  positionNo: number,
  windowMap: Map<string, SmartWindowItemDto>,
): CabinWindowSlot {
  const sidePrefix = side === 'LEFT' ? 'L' : 'R'
  return {
    side,
    positionNo,
    label: `${sidePrefix}${positionNo.toString().padStart(2, '0')}`,
    data: windowMap.get(`${side}:${positionNo}`) ?? null,
  }
}

function serviceLabel(zone: CabinZoneLayout, positionNo: number): string | null {
  if (!zone.servicePositions.includes(positionNo)) return null
  if (positionNo === zone.rangeStart || positionNo === zone.rangeStart + 1) return '舱门 / 服务区'
  if (positionNo === zone.rangeEnd || positionNo === zone.rangeEnd - 1) return '隔断 / 盥洗区'
  return '厨房 / 通道区'
}
