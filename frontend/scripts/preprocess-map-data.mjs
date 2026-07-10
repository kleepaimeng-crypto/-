import { createRequire } from 'node:module'
import { cp, mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const { applyCommands, enableLogging } = require('mapshaper')

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const frontendDirectory = dirname(scriptDirectory)
const sourceDirectory = join(frontendDirectory, 'map', 'GeoJSON')
const outputDirectory = join(frontendDirectory, 'public', 'map', 'geojson')
const outputVersion = 2
const coordinatePrecision = 5
const excludedProvinceInteriorRings = [{
  provinceAdcode: 150000,
  bbox: [123.7, 50.1, 124.5, 50.7],
}]

const simplificationMeters = {
  country: 1500,
  province: 1500,
  city: 400,
  county: 100,
}

enableLogging(false)

async function main() {
  const outputStagingDirectory = `${outputDirectory}.staging`
  await rm(outputStagingDirectory, { recursive: true, force: true })
  await mkdir(outputStagingDirectory, { recursive: true })

  const manifestEntries = []
  const sourceMetrics = createMetrics()
  const outputMetrics = createMetrics()

  const country = await readGeoJson(join(sourceDirectory, 'china.json'))
  collectMetrics(country, sourceMetrics)
  const optimizedCountry = await simplifyPolygons(country, simplificationMeters.country)
  const countryOutput = compactPolygons(optimizedCountry)
  await writeGeoJson(join(outputStagingDirectory, 'china.json'), countryOutput)
  addManifestEntry(manifestEntries, 'china.json', 'country', country)
  collectMetrics(countryOutput, outputMetrics)

  const provinceDirectory = join(sourceDirectory, 'province')
  const provinceFiles = await geoJsonFiles(provinceDirectory)
  const provinces = removeExcludedProvinceInteriorRings(
    await createProvincePolygons(await readAndMerge(provinceFiles)),
  )
  collectMetrics(provinces, sourceMetrics)
  const provinceBoundaries = createOuterBoundaryCollection(provinces)
  const provinceLabels = await createLabelCollection(provinces)
  const provinceOutput = combineFeatureCollections(provinceBoundaries, provinceLabels)
  await writeGeoJson(join(outputStagingDirectory, 'province-boundaries.json'), provinceOutput)
  addManifestEntry(manifestEntries, 'province-boundaries.json', 'province', provinces)
  collectMetrics(provinceOutput, outputMetrics)

  for (const file of provinceFiles) {
    const source = await readGeoJson(file)
    collectMetrics(source, sourceMetrics)
    const output = await createBoundaryCollection(await simplifyPolygons(source, simplificationMeters.city))
    const outputPath = join('province', relative(provinceDirectory, file))
    await writeGeoJson(join(outputStagingDirectory, outputPath), output)
    addManifestEntry(manifestEntries, outputPath, 'city', source)
    collectMetrics(output, outputMetrics)
  }

  const cityDirectory = join(sourceDirectory, 'citys')
  const cityFiles = await geoJsonFiles(cityDirectory)
  for (const file of cityFiles) {
    const source = await readGeoJson(file)
    collectMetrics(source, sourceMetrics)
    const simplified = await simplifyPolygons(source, simplificationMeters.county)
    const boundaries = await createBoundaryCollection(simplified)
    const labels = await createLabelCollection(simplified)
    const output = combineFeatureCollections(boundaries, labels)
    const outputPath = join('citys', relative(cityDirectory, file))
    await writeGeoJson(join(outputStagingDirectory, outputPath), output)
    addManifestEntry(manifestEntries, outputPath, 'county', source)
    collectMetrics(output, outputMetrics)
  }

  const report = {
    version: outputVersion,
    simplificationMeters,
    source: sourceMetrics,
    output: outputMetrics,
    reductions: {
      bytes: percentageReduction(sourceMetrics.bytes, outputMetrics.bytes),
      coordinates: percentageReduction(sourceMetrics.coordinates, outputMetrics.coordinates),
    },
  }
  await validateOutput(manifestEntries, outputStagingDirectory)
  await writeFile(
    join(outputStagingDirectory, 'manifest.json'),
    JSON.stringify({ version: outputVersion, optimized: true, entries: manifestEntries }),
    'utf8',
  )
  await writeFile(join(outputStagingDirectory, 'preprocess-report.json'), JSON.stringify(report), 'utf8')

  await rm(outputDirectory, { recursive: true, force: true })
  await cp(outputStagingDirectory, outputDirectory, { recursive: true })
  await rm(outputStagingDirectory, { recursive: true, force: true })

  console.log(JSON.stringify(report, null, 2))
}

function createMetrics() {
  return { files: 0, features: 0, coordinates: 0, bytes: 0 }
}

async function geoJsonFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  return entries
    .filter((entry) => entry.isFile() && entry.name.endsWith('.json'))
    .map((entry) => join(directory, entry.name))
    .sort((left, right) => left.localeCompare(right, 'zh-Hans-CN'))
}

async function readAndMerge(files) {
  const collections = await Promise.all(files.map(readGeoJson))
  return {
    type: 'FeatureCollection',
    features: collections.flatMap((collection) => collection.features),
  }
}

async function readGeoJson(filePath) {
  const value = JSON.parse(await readFile(filePath, 'utf8'))
  if (value.type === 'FeatureCollection' && Array.isArray(value.features)) return value
  if (value.type === 'Feature') return { type: 'FeatureCollection', features: [value] }
  throw new Error(`Unsupported GeoJSON: ${filePath}`)
}

async function writeGeoJson(filePath, value) {
  await mkdir(dirname(filePath), { recursive: true })
  await writeFile(filePath, JSON.stringify(value), 'utf8')
}

async function simplifyPolygons(collection, toleranceMeters) {
  return executeMapshaper(collection, [
    '-proj webmercator',
    `-simplify interval=${toleranceMeters}m keep-shapes`,
    '-proj wgs84',
  ])
}

async function createProvincePolygons(collection) {
  const provinceNames = await provinceNameByAdcode()
  const normalized = {
    type: 'FeatureCollection',
    features: collection.features.map((feature) => {
      const isProvinceFeature = feature.properties?.level === 'province'
      const adcode = isProvinceFeature ? feature.properties?.adcode : feature.properties?.parent?.adcode
      const name = isProvinceFeature ? feature.properties?.name : provinceNames.get(adcode)
      if (!adcode || !name) throw new Error(`Missing province metadata for ${feature.properties?.name ?? 'unknown feature'}`)
      return {
        type: 'Feature',
        properties: { provinceAdcode: adcode, provinceName: name },
        geometry: feature.geometry,
      }
    }),
  }
  return executeMapshaper(normalized, [
    '-proj webmercator',
    '-dissolve provinceAdcode copy-fields=provinceName allow-overlaps',
    `-simplify interval=${simplificationMeters.province}m keep-shapes`,
    '-proj wgs84',
  ])
}

async function provinceNameByAdcode() {
  const info = JSON.parse(await readFile(join(sourceDirectory, 'info.json'), 'utf8'))
  return new Map(info['100000'].children.map((province) => [province.adcode, province.name]))
}

async function createBoundaryCollection(collection) {
  const output = await executeMapshaper(collection, [
    '-innerlines',
  ])
  const lines = extractLines(output)
  return {
    type: 'FeatureCollection',
    features: lines.length === 0
      ? []
      : [{
        type: 'Feature',
        properties: { renderKind: 'boundary' },
        geometry: { type: 'MultiLineString', coordinates: lines },
      }],
  }
}

function createOuterBoundaryCollection(collection) {
  return {
    type: 'FeatureCollection',
    features: collection.features
      .filter((feature) => isPolygonGeometry(feature.geometry))
      .map((feature) => ({
        type: 'Feature',
        properties: { renderKind: 'boundary' },
        geometry: feature.geometry,
      })),
  }
}

function removeExcludedProvinceInteriorRings(collection) {
  return {
    type: 'FeatureCollection',
    features: collection.features.map((feature) => ({
      ...feature,
      geometry: removeExcludedRingsFromGeometry(feature.geometry, Number(feature.properties?.provinceAdcode)),
    })),
  }
}

function removeExcludedRingsFromGeometry(geometry, provinceAdcode) {
  if (!isPolygonGeometry(geometry)) return geometry
  const polygons = geometry.type === 'MultiPolygon' ? geometry.coordinates : [geometry.coordinates]
  const filteredPolygons = polygons.map((polygon) => polygon.filter((ring, index) =>
    index === 0 || !excludedProvinceInteriorRings.some((rule) =>
      rule.provinceAdcode === provinceAdcode && ringIsInsideBbox(ring, rule.bbox),
    ),
  ))
  return geometry.type === 'MultiPolygon'
    ? { ...geometry, coordinates: filteredPolygons }
    : { ...geometry, coordinates: filteredPolygons[0] }
}

function ringIsInsideBbox(ring, bbox) {
  return ring.every(([longitude, latitude]) =>
    longitude >= bbox[0] && longitude <= bbox[2] && latitude >= bbox[1] && latitude <= bbox[3],
  )
}

async function createLabelCollection(collection) {
  const output = await executeMapshaper(collection, [
    '-points inner',
  ])
  const labels = {
    type: 'FeatureCollection',
    features: output.features
      .filter((feature) => feature.geometry?.type === 'Point' && typeof labelName(feature) === 'string')
      .map((feature) => ({
        type: 'Feature',
        properties: {
          name: labelName(feature),
          adcode: feature.properties.adcode ?? feature.properties.provinceAdcode,
          renderKind: 'label',
        },
        geometry: feature.geometry,
      })),
  }
  const polygonCount = collection.features.filter((feature) => isPolygonGeometry(feature.geometry)).length
  if (labels.features.length !== polygonCount) {
    throw new Error(`Expected ${polygonCount} labels but generated ${labels.features.length}`)
  }
  return labels
}

function labelName(feature) {
  return feature.properties?.name ?? feature.properties?.provinceName
}

async function executeMapshaper(collection, commands) {
  const output = await applyCommands(
    `-quiet -i input.json ${commands.join(' ')} -o output.json format=geojson precision=0.${'0'.repeat(coordinatePrecision - 1)}1`,
    { 'input.json': JSON.stringify(collection) },
  )
  const value = output['output.json']
  const json = Buffer.isBuffer(value) ? value.toString('utf8') : String(value)
  return normalizeGeoJson(JSON.parse(json))
}

function normalizeGeoJson(value) {
  if (value.type === 'FeatureCollection') return value
  if (value.type === 'GeometryCollection') {
    return {
      type: 'FeatureCollection',
      features: value.geometries.map((geometry) => ({ type: 'Feature', properties: {}, geometry })),
    }
  }
  if (value.type === 'Feature') return { type: 'FeatureCollection', features: [value] }
  throw new Error(`Unexpected mapshaper output: ${value.type}`)
}

function compactPolygons(collection) {
  return {
    type: 'FeatureCollection',
    features: collection.features.map((feature) => ({
      type: 'Feature',
      properties: {
        name: feature.properties?.name,
        adcode: feature.properties?.adcode,
      },
      geometry: feature.geometry,
    })),
  }
}

function combineFeatureCollections(...collections) {
  return {
    type: 'FeatureCollection',
    features: collections.flatMap((collection) => collection.features),
  }
}

function extractLines(collection) {
  const lines = []
  for (const feature of collection.features) {
    const geometry = feature.geometry
    if (!geometry) continue
    if (geometry.type === 'LineString') lines.push(geometry.coordinates)
    if (geometry.type === 'MultiLineString') lines.push(...geometry.coordinates)
  }
  return lines
}

function addManifestEntry(entries, outputPath, level, source) {
  entries.push({
    path: outputPath.replaceAll('\\', '/'),
    level,
    bbox: bboxOf(source),
  })
}

function bboxOf(collection) {
  const bounds = [Infinity, Infinity, -Infinity, -Infinity]
  forEachCoordinate(collection, ([longitude, latitude]) => {
    bounds[0] = Math.min(bounds[0], longitude)
    bounds[1] = Math.min(bounds[1], latitude)
    bounds[2] = Math.max(bounds[2], longitude)
    bounds[3] = Math.max(bounds[3], latitude)
  })
  if (!Number.isFinite(bounds[0])) throw new Error('Unable to calculate GeoJSON bounding box')
  return bounds.map((value) => Number(value.toFixed(coordinatePrecision)))
}

function collectMetrics(collection, metrics) {
  metrics.files += 1
  metrics.features += collection.features.length
  metrics.bytes += Buffer.byteLength(JSON.stringify(collection))
  forEachCoordinate(collection, () => { metrics.coordinates += 1 })
}

function forEachCoordinate(value, callback) {
  if (Array.isArray(value)) {
    if (typeof value[0] === 'number' && typeof value[1] === 'number') {
      callback(value)
      return
    }
    for (const item of value) forEachCoordinate(item, callback)
    return
  }
  if (!value || typeof value !== 'object') return
  if (value.type === 'FeatureCollection') {
    for (const feature of value.features) forEachCoordinate(feature.geometry?.coordinates, callback)
    return
  }
  if (value.type === 'Feature') {
    forEachCoordinate(value.geometry?.coordinates, callback)
    return
  }
  forEachCoordinate(value.coordinates, callback)
}

async function validateOutput(entries, directory) {
  const paths = new Set()
  for (const entry of entries) {
    if (paths.has(entry.path)) throw new Error(`Duplicate manifest path: ${entry.path}`)
    paths.add(entry.path)
    if (!Array.isArray(entry.bbox) || entry.bbox.length !== 4 || entry.bbox.some((value) => !Number.isFinite(value))) {
      throw new Error(`Invalid manifest bbox: ${entry.path}`)
    }
  }
  if (entries.filter((entry) => entry.level === 'country').length !== 1) throw new Error('Expected one country resource')
  if (entries.filter((entry) => entry.level === 'province').length !== 1) throw new Error('Expected one province resource')
  if (entries.filter((entry) => entry.level === 'city').length !== 34) throw new Error('Expected 34 city resources')
  if (entries.filter((entry) => entry.level === 'county').length !== 419) throw new Error('Expected 419 county resources')
  for (const entry of entries) {
    const collection = await readGeoJson(join(directory, entry.path))
    for (const feature of collection.features) validateFeature(feature, entry.path)
  }
}

function validateFeature(feature, path) {
  if (feature.type !== 'Feature' || !feature.geometry) throw new Error(`Invalid feature in ${path}`)
  if (!['Point', 'Polygon', 'MultiPolygon', 'LineString', 'MultiLineString'].includes(feature.geometry.type)) {
    throw new Error(`Unsupported geometry in ${path}: ${feature.geometry.type}`)
  }
  forEachCoordinate(feature.geometry.coordinates, (coordinate) => {
    if (!Number.isFinite(coordinate[0]) || !Number.isFinite(coordinate[1])) {
      throw new Error(`Invalid coordinate in ${path}`)
    }
  })
  if (feature.properties?.renderKind === 'label' && typeof feature.properties.name !== 'string') {
    throw new Error(`Unnamed label in ${path}`)
  }
}

function isPolygonGeometry(geometry) {
  return geometry?.type === 'Polygon' || geometry?.type === 'MultiPolygon'
}

function percentageReduction(before, after) {
  return before === 0 ? 0 : Number((((before - after) / before) * 100).toFixed(2))
}

await main()
