<script setup lang="ts">
import 'ol/ol.css'
import Feature from 'ol/Feature'
import Map from 'ol/Map'
import View from 'ol/View'
import LineString from 'ol/geom/LineString'
import Point from 'ol/geom/Point'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import XYZ from 'ol/source/XYZ'
import VectorSource from 'ol/source/Vector'
import Icon from 'ol/style/Icon'
import Stroke from 'ol/style/Stroke'
import Style from 'ol/style/Style'
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
const basePlaneZoom = 6
const planeSourceSize = 512
const planeMinSize = 36
const planeMaxSize = 220
const tileUrlTemplate = import.meta.env.VITE_OFFLINE_MAP_TILE_URL || '/offline-map/{z}/{x}/{y}.png'
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
        new TileLayer({
          source: createOfflineTileSource(),
        }),
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
        center: [0, 0],
        maxZoom: maxTileZoom,
        minZoom,
        zoom: initialZoom,
      }),
    })
    map.on('pointerdrag', stopFollowingPlane)
    mapReady.value = true
    updateTrack()
  } catch (caught) {
    mapError.value = caught instanceof Error ? caught.message : '离线地图加载失败'
  }
}

function createOfflineTileSource(): XYZ {
  return new XYZ({
    minZoom,
    maxZoom: maxTileZoom,
    url: tileUrlTemplate,
    wrapX: false,
  })
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
