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
import { fromLonLat } from 'ol/proj'
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
const initialZoom = 6
const defaultMapZoom = 4
const chinaMapCenter = fromLonLat([104.5, 35.5])
const geoJsonBaseUrl = '/map/geojson'
const geoJsonManifestUrl = `${geoJsonBaseUrl}/manifest.json`
const geoJsonLoadConcurrency = 16
const basePlaneZoom = 6
const planeSourceSize = 512
const planeMinSize = 36
const planeMaxSize = 220
const mapContainer = ref<HTMLElement | null>(null)
const mapReady = ref(false)
const mapError = ref('')

let map: Map | undefined
let trackFeature: Feature<LineString> | undefined
let planeFeature: Feature<Point> | undefined
let trackSource: VectorSource | undefined
let planeSource: VectorSource | undefined
let mapViewInitialized = false
let latestCoordinate: Coordinate | undefined
let followingPlane = true

type AdministrativeMapLevel = 'country' | 'province' | 'city' | 'county' | 'unknown'

interface AdministrativeGeoJsonEntry {
  path: string
  level: AdministrativeMapLevel
}

onMounted(() => {
  setupMap()
})

onBeforeUnmount(() => {
  map?.setTarget(undefined)
  map = undefined
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
    mapReady.value = true
    updateTrack()
  } catch (caught) {
    mapError.value = caught instanceof Error ? caught.message : '离线地图加载失败'
  }
}

function createChinaMapLayer(): VectorLayer<VectorSource> {
  const source = new VectorSource({
  })
  void loadAdministrativeGeoJson(source)
  return new VectorLayer({
    source,
    style: chinaRegionStyle,
    renderBuffer: 120,
    updateWhileAnimating: true,
    updateWhileInteracting: true,
  })
}

async function loadAdministrativeGeoJson(source: VectorSource): Promise<void> {
  try {
    const manifestResponse = await fetch(geoJsonManifestUrl)
    if (!manifestResponse.ok) throw new Error(`manifest ${manifestResponse.status}`)
    const entries = await manifestResponse.json() as AdministrativeGeoJsonEntry[]
    const geoJsonFormat = new GeoJSON({
      dataProjection: 'EPSG:4326',
      featureProjection: 'EPSG:3857',
    })
    let failedCount = 0
    let nextIndex = 0
    async function loadNext(): Promise<void> {
      const entry = entries[nextIndex++]
      if (!entry) return
      try {
        const response = await fetch(`${geoJsonBaseUrl}/${encodeURI(entry.path)}`)
        if (!response.ok) throw new Error(`${entry.path} ${response.status}`)
        const payload = await response.json()
        const features = geoJsonFormat.readFeatures(payload)
        for (const feature of features) {
          feature.set('mapLevel', entry.level, true)
        }
        source.addFeatures(features)
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
  } catch {
    mapError.value = '中国行政区 GeoJSON 加载失败'
  }
}

function chinaRegionStyle(feature: FeatureLike): Style {
  const level = feature.get('mapLevel') as AdministrativeMapLevel | undefined
  return switchRegionStyle(level)
}

function switchRegionStyle(level: AdministrativeMapLevel | undefined): Style {
  switch (level) {
    case 'country':
      return countryStyle
    case 'province':
      return provinceStyle
    case 'city':
      return cityStyle
    case 'county':
      return countyStyle
    default:
      return cityStyle
  }
}

const countryStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0.72)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.56)',
    lineJoin: 'round',
    width: 1.4,
  }),
})

const provinceStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0.2)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.34)',
    lineJoin: 'round',
    width: 0.9,
  }),
})

const cityStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0.05)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.18)',
    lineJoin: 'round',
    width: 0.55,
  }),
})

const countyStyle = new Style({
  fill: new Fill({
    color: 'rgba(23, 42, 59, 0.02)',
  }),
  stroke: new Stroke({
    color: 'rgba(86, 220, 255, 0.08)',
    lineJoin: 'round',
    width: 0.35,
  }),
})

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
  return clamp(96 * (2 ** ((zoom - basePlaneZoom) / 2)), planeMinSize, planeMaxSize)
}

function numberEnv(value: string | undefined, fallback: number): number {
  if (!value) return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
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
