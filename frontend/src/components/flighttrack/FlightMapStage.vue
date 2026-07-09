<script setup lang="ts">
import 'ol/ol.css'
import Feature from 'ol/Feature'
import GeoJSON from 'ol/format/GeoJSON'
import Map from 'ol/Map'
import View from 'ol/View'
import LineString from 'ol/geom/LineString'
import Point from 'ol/geom/Point'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Icon from 'ol/style/Icon'
import Fill from 'ol/style/Fill'
import Stroke from 'ol/style/Stroke'
import Style from 'ol/style/Style'
import Text from 'ol/style/Text'
import { fromLonLat, transformExtent } from 'ol/proj'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Coordinate } from 'ol/coordinate'
import type { FeatureLike } from 'ol/Feature'
import type { FlightTrackCurrentDto } from '../../api/types'
import { flownTrackPoints, projectTrack, trackHeading } from './flightMapGeometry'

const props = defineProps<{
  current: FlightTrackCurrentDto | null
  loading: boolean
  error: string
}>()

const minZoom = numberEnv(import.meta.env.VITE_OFFLINE_MAP_MIN_ZOOM, 3)
const maxTileZoom = numberEnv(import.meta.env.VITE_OFFLINE_MAP_MAX_ZOOM, 10)
const initialZoom = 5
const defaultMapZoom = 4
const chinaMapCenter = fromLonLat([104.5, 35.5])
const geoJsonBaseUrl = '/map/geojson'
const geoJsonManifestUrl = `${geoJsonBaseUrl}/manifest.json`
const geoJsonLoadConcurrency = 16
const cityLoadZoom = 6.5
const countyLoadZoom = 9
const cityVisibleZoom = 6.5
const countyVisibleZoom = 9
const provinceLabelZoom = 4
const countyLabelZoom = 9.5
const countyLoadDebounceMs = 220
const basePlaneZoom = 6
const planeSourceSize = 512
const planeMinSize = 44
const planeMaxSize = 122
const mapContainer = ref<HTMLElement | null>(null)
const mapReady = ref(false)
const mapError = ref('')
const labelStyleCache = new globalThis.Map<string, Style>()

let map: Map | undefined
let trackFeature: Feature<LineString> | undefined
let planeFeature: Feature<Point> | undefined
let trackSource: VectorSource | undefined
let planeSource: VectorSource | undefined
let mapViewInitialized = false
let latestCoordinate: Coordinate | undefined
let followingPlane = true
let administrativeMapLoader: AdministrativeMapLoader | undefined
let administrativeMapLayer: VectorLayer<VectorSource> | undefined
let currentMapZoom = defaultMapZoom
let countyLoadTimer: number | undefined

type AdministrativeMapLevel = 'country' | 'province' | 'city' | 'county' | 'unknown'

interface AdministrativeGeoJsonEntry {
  path: string
  level: AdministrativeMapLevel
  bbox?: [number, number, number, number]
}

class AdministrativeMapLoader {
  private readonly format = new GeoJSON({
    dataProjection: 'EPSG:4326',
    featureProjection: 'EPSG:3857',
  })

  private manifest: AdministrativeGeoJsonEntry[] = []
  private manifestPromise: Promise<AdministrativeGeoJsonEntry[]> | undefined
  private readonly loadedLevels = new Set<AdministrativeMapLevel>()
  private readonly loadingLevels = new Set<AdministrativeMapLevel>()
  private readonly loadedPaths = new Set<string>()
  private readonly loadingPaths = new Set<string>()

  constructor(private readonly source: VectorSource) {}

  async loadLevels(levels: AdministrativeMapLevel[]): Promise<void> {
    const pendingLevels = levels.filter((level) =>
      !this.loadedLevels.has(level) && !this.loadingLevels.has(level),
    )
    if (pendingLevels.length === 0) return
    for (const level of pendingLevels) {
      this.loadingLevels.add(level)
    }
    try {
      const entries = (await this.loadManifest()).filter((entry) => pendingLevels.includes(entry.level))
      await this.loadEntries(entries)
      for (const level of pendingLevels) {
        this.loadedLevels.add(level)
      }
    } catch {
      mapError.value = '中国行政区 GeoJSON 加载失败'
    } finally {
      for (const level of pendingLevels) {
        this.loadingLevels.delete(level)
      }
    }
  }

  async loadVisibleCounties(viewBbox: [number, number, number, number]): Promise<void> {
    try {
      const entries = (await this.loadManifest()).filter((entry) =>
        entry.level === 'county'
        && entry.bbox !== undefined
        && intersectsBbox(entry.bbox, viewBbox)
        && !this.loadedPaths.has(entry.path)
        && !this.loadingPaths.has(entry.path),
      )
      if (entries.length === 0) return
      for (const entry of entries) {
        this.loadingPaths.add(entry.path)
      }
      try {
        await this.loadEntries(entries)
        for (const entry of entries) {
          this.loadedPaths.add(entry.path)
        }
      } finally {
        for (const entry of entries) {
          this.loadingPaths.delete(entry.path)
        }
      }
    } catch {
      mapError.value = '中国行政区 GeoJSON 加载失败'
    }
  }

  private async loadManifest(): Promise<AdministrativeGeoJsonEntry[]> {
    if (this.manifest.length > 0) return this.manifest
    if (!this.manifestPromise) {
      this.manifestPromise = fetch(geoJsonManifestUrl)
        .then((response) => {
          if (!response.ok) throw new Error(`manifest ${response.status}`)
          return response.json() as Promise<AdministrativeGeoJsonEntry[]>
        })
        .then((entries) => {
          this.manifest = entries
          return entries
        })
    }
    return this.manifestPromise
  }

  private async loadEntries(entries: AdministrativeGeoJsonEntry[]): Promise<void> {
    let failedCount = 0
    let nextIndex = 0
    const loadNext = async (): Promise<void> => {
      const entry = entries[nextIndex++]
      if (!entry) return
      try {
        const response = await fetch(`${geoJsonBaseUrl}/${encodeURI(entry.path)}`)
        if (!response.ok) throw new Error(`${entry.path} ${response.status}`)
        const payload = await response.json()
        const features = this.format.readFeatures(payload)
        for (const feature of features) {
          feature.set('mapLevel', entry.level, true)
        }
        this.source.addFeatures(features)
      } catch {
        failedCount += 1
      }
      await loadNext()
    }
    const workers = Array.from(
      { length: Math.min(geoJsonLoadConcurrency, entries.length) },
      () => loadNext(),
    )
    await Promise.all(workers)
    if (failedCount > 0) {
      mapError.value = `部分行政区 GeoJSON 加载失败：${failedCount} 个文件`
    }
  }
}

onMounted(() => {
  setupMap()
})

onBeforeUnmount(() => {
  if (countyLoadTimer !== undefined) window.clearTimeout(countyLoadTimer)
  map?.setTarget(undefined)
  map = undefined
  administrativeMapLoader = undefined
  administrativeMapLayer = undefined
})

watch(() => props.current, () => {
  updateTrack()
}, { deep: true })

function setupMap(): void {
  const target = mapContainer.value
  if (!target) return
  try {
    trackFeature = new Feature(new LineString([]))
    planeFeature = new Feature(new Point([0, 0]))
    trackSource = new VectorSource({ features: [trackFeature] })
    planeSource = new VectorSource({ features: [planeFeature] })
    map = new Map({
      target,
      controls: [],
      layers: [
        createChinaMapLayer(),
        new VectorLayer({
          source: trackSource,
          style: trackStyle,
          updateWhileAnimating: true,
          updateWhileInteracting: true,
        }),
        new VectorLayer({
          source: planeSource,
          style: planeStyle,
          updateWhileAnimating: true,
          updateWhileInteracting: true,
        }),
      ],
      view: new View({
        center: chinaMapCenter,
        maxZoom: maxTileZoom,
        minZoom,
        zoom: defaultMapZoom,
      }),
    })
    map.on('pointerdrag', stopFollowingPlane)
    map.getView().on('change:resolution', handleMapZoomChange)
    map.on('moveend', handleMapMoveEnd)
    handleMapZoomChange()
    mapReady.value = true
    updateTrack()
  } catch (caught) {
    mapError.value = caught instanceof Error ? caught.message : '离线地图加载失败'
  }
}

function createChinaMapLayer(): VectorLayer<VectorSource> {
  const source = new VectorSource({
  })
  administrativeMapLoader = new AdministrativeMapLoader(source)
  void administrativeMapLoader.loadLevels(['country', 'province'])
  administrativeMapLayer = new VectorLayer({
    source,
    style: chinaRegionStyle,
    declutter: true,
    renderBuffer: 120,
    updateWhileAnimating: true,
    updateWhileInteracting: true,
  })
  return administrativeMapLayer
}

function handleMapZoomChange(): void {
  const zoom = map?.getView().getZoom()
  if (zoom === undefined) return
  currentMapZoom = zoom
  administrativeMapLayer?.changed()
  if (zoom >= cityLoadZoom) {
    void administrativeMapLoader?.loadLevels(['city'])
  }
  if (zoom >= countyLoadZoom) {
    scheduleVisibleCountyLoad()
  }
}

function handleMapMoveEnd(): void {
  const zoom = map?.getView().getZoom()
  if (zoom === undefined || zoom < countyLoadZoom) return
  scheduleVisibleCountyLoad()
}

function scheduleVisibleCountyLoad(): void {
  if (countyLoadTimer !== undefined) window.clearTimeout(countyLoadTimer)
  countyLoadTimer = window.setTimeout(() => {
    countyLoadTimer = undefined
    const bbox = currentViewBbox()
    if (!bbox) return
    void administrativeMapLoader?.loadVisibleCounties(bbox)
  }, countyLoadDebounceMs)
}

function currentViewBbox(): [number, number, number, number] | null {
  if (!map) return null
  const size = map.getSize()
  if (!size) return null
  const extent = map.getView().calculateExtent(size)
  return transformExtent(extent, 'EPSG:3857', 'EPSG:4326') as [number, number, number, number]
}

function chinaRegionStyle(feature: FeatureLike): Style | Style[] {
  const level = feature.get('mapLevel') as AdministrativeMapLevel | undefined
  const baseStyle = switchRegionStyle(level)
  if (baseStyle === hiddenStyle) return hiddenStyle
  const labelStyle = administrativeLabelStyle(feature, level)
  return labelStyle ? [baseStyle, labelStyle] : baseStyle
}

function switchRegionStyle(level: AdministrativeMapLevel | undefined): Style {
  switch (level) {
    case 'country':
      return countryStyle
    case 'province':
      return provinceStyle
    case 'city':
      return currentMapZoom >= cityVisibleZoom ? cityStyle : hiddenStyle
    case 'county':
      return currentMapZoom >= countyVisibleZoom ? countyStyle : hiddenStyle
    default:
      return hiddenStyle
  }
}

const hiddenStyle = new Style({})

const countryStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0.42)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.46)',
    lineJoin: 'round',
    width: 1.45,
  }),
})

const provinceStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.32)',
    lineJoin: 'round',
    width: 0.8,
  }),
})

const cityStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.22)',
    lineJoin: 'round',
    width: 0.55,
  }),
})

const countyStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.07)',
    lineJoin: 'round',
    width: 0.28,
  }),
})

function administrativeLabelStyle(
  feature: FeatureLike,
  level: AdministrativeMapLevel | undefined,
): Style | null {
  const name = feature.get('name')
  if (typeof name !== 'string' || name.length === 0) return null
  if (level === 'province' && currentMapZoom < provinceLabelZoom) return null
  if (level === 'city') return null
  if (level === 'county' && currentMapZoom < countyLabelZoom) return null
  if (level !== 'province' && level !== 'county') return null

  const zoomBucket = Math.floor(currentMapZoom * 2) / 2
  const cacheKey = `${level}:${name}:${zoomBucket}`
  const cached = labelStyleCache.get(cacheKey)
  if (cached) return cached

  const labelStyle = new Style({
    text: new Text({
      text: name,
      font: labelFont(level),
      overflow: false,
      fill: new Fill({
        color: labelFill(level),
      }),
      stroke: new Stroke({
        color: 'rgba(8, 16, 28, 0.78)',
        width: labelHaloWidth(level),
      }),
    }),
  })
  labelStyleCache.set(cacheKey, labelStyle)
  return labelStyle
}

function labelFont(level: AdministrativeMapLevel): string {
  switch (level) {
    case 'province':
      return '600 14px "Microsoft YaHei", sans-serif'
    case 'city':
      return '500 12px "Microsoft YaHei", sans-serif'
    case 'county':
      return '400 10px "Microsoft YaHei", sans-serif'
    default:
      return '400 12px "Microsoft YaHei", sans-serif'
  }
}

function labelFill(level: AdministrativeMapLevel): string {
  switch (level) {
    case 'province':
      return 'rgba(196, 242, 255, 0.72)'
    case 'city':
      return 'rgba(178, 230, 246, 0.54)'
    case 'county':
      return 'rgba(166, 216, 232, 0.34)'
    default:
      return 'rgba(178, 230, 246, 0.5)'
  }
}

function labelHaloWidth(level: AdministrativeMapLevel): number {
  switch (level) {
    case 'province':
      return 3
    case 'city':
      return 2.4
    case 'county':
      return 2
    default:
      return 2
  }
}

function updateTrack(): void {
  if (!map || !trackFeature || !planeFeature) return
  const current = props.current
  if (!current) {
    trackFeature.getGeometry()?.setCoordinates([])
    planeFeature.setGeometry(undefined)
    latestCoordinate = undefined
    return
  }
  const points = flownTrackPoints(current.track, current.latestPoint)
  const coordinates = projectTrack(points)
  trackFeature.getGeometry()?.setCoordinates(coordinates)
  latestCoordinate = coordinates.at(-1)
  if (!latestCoordinate) {
    planeFeature.setGeometry(undefined)
    return
  }
  planeFeature.setGeometry(new Point(latestCoordinate))
  planeFeature.set('heading', trackHeading(points, current.latestPoint))
  if (!mapViewInitialized) {
    map.getView().setCenter(latestCoordinate)
    map.getView().setZoom(initialZoom)
    mapViewInitialized = true
  } else if (followingPlane) {
    map.getView().setCenter(latestCoordinate)
  }
}

function stopFollowingPlane(): void {
  followingPlane = false
}

function locatePlane(): void {
  if (!map || !latestCoordinate) return
  followingPlane = true
  map.getView().animate({
    center: latestCoordinate,
    duration: 260,
  })
}

defineExpose({ locatePlane })

function trackStyle(): Style {
  return new Style({
    stroke: new Stroke({
      color: '#56dcff',
      lineCap: 'round',
      lineJoin: 'round',
      width: 4,
    }),
  })
}

function planeStyle(feature: FeatureLike, resolution: number): Style {
  const zoom = map?.getView().getZoomForResolution(resolution) ?? initialZoom
  const size = planeSize(zoom)
  const heading = Number(feature.get('heading') ?? 90)
  return new Style({
    image: new Icon({
      anchor: [0.5, 0.5],
      opacity: 1,
      rotateWithView: false,
      rotation: (heading * Math.PI) / 180,
      scale: size / planeSourceSize,
      src: '/assets/plane.png',
    }),
    zIndex: 20,
  })
}

function planeSize(zoom: number): number {
  return clamp(66 * (2 ** ((zoom - basePlaneZoom) / 4)), planeMinSize, planeMaxSize)
}

function numberEnv(value: string | undefined, fallback: number): number {
  if (!value) return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function intersectsBbox(
  left: [number, number, number, number],
  right: [number, number, number, number],
): boolean {
  return left[0] <= right[2]
    && left[2] >= right[0]
    && left[1] <= right[3]
    && left[3] >= right[1]
}
</script>

<template>
  <section class="flight-map-stage" aria-label="飞机实时轨迹地图">
    <div ref="mapContainer" class="flight-ol-map" :class="{ 'is-ready': mapReady }"></div>
    <div v-if="loading && !current" class="flight-map-state">读取 QAR 轨迹中</div>
    <div v-else-if="!current" class="flight-map-state">{{ error || mapError || '等待模拟器 QAR 数据' }}</div>
    <div v-else-if="error" class="flight-map-warning">{{ error }}</div>
    <div v-else-if="mapError" class="flight-map-warning">{{ mapError }}</div>
  </section>
</template>
