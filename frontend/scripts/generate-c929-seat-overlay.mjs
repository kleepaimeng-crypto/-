import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const outputPath = resolve(scriptDirectory, '../public/assets/C929_set.svg')

// Seat coordinates were measured on a losslessly quarter-scaled 510 x 2597
// reference of the original 2040 x 10388 cabin image. C929-700 1.svg embeds
// an 804 x 4096 JPEG inside an objectBoundingBox pattern, then paints that
// pattern into a 800 x 4075.62 rect. The overlay must therefore use the
// painted rect dimensions, not the embedded JPEG dimensions; otherwise the
// error accumulates toward the rear of the cabin.
const scaleX = 800 / 510
const scaleY = 4075.62 / 2597

const seats = []
const largeBusinessSeatWidth = 40
const largeBusinessSeatHeight = 36
const largeBusinessSeatOffsetY = -8

function largeBusinessSeatBox(centerX, centerY) {
  const adjustedCenterY = centerY + largeBusinessSeatOffsetY

  return [
    centerX - largeBusinessSeatWidth / 2,
    adjustedCenterY - largeBusinessSeatHeight / 2,
    centerX + largeBusinessSeatWidth / 2,
    adjustedCenterY + largeBusinessSeatHeight / 2,
  ]
}

function addSeat(seatNo, cabinClass, section, sourceBox, style) {
  const [left, top, right, bottom] = sourceBox
  seats.push({
    seatNo,
    cabinClass,
    section,
    style,
    x: left * scaleX,
    y: top * scaleY,
    width: (right - left) * scaleX,
    height: (bottom - top) * scaleY,
  })
}

const largeBusinessRows = [
  {
    row: 11,
    boxes: {
      A: largeBusinessSeatBox(148, 462.5),
      D: largeBusinessSeatBox(228, 474.5),
      G: largeBusinessSeatBox(282, 474.5),
      K: largeBusinessSeatBox(362, 462.5),
    },
  },
  {
    row: 12,
    boxes: {
      A: largeBusinessSeatBox(148, 550.5),
      D: largeBusinessSeatBox(228, 564.5),
      G: largeBusinessSeatBox(282, 564.5),
      K: largeBusinessSeatBox(362, 550.5),
    },
  },
]

for (const { row, boxes } of largeBusinessRows) {
  for (const letter of ['A', 'D', 'G', 'K']) {
    addSeat(`${letter}${row}`, 'BUSINESS', 'front', boxes[letter], 'business-large')
  }
}

const premiumColumns = {
  A: [128, 152],
  C: [165.5, 189.5],
  D: [223.5, 247.5],
  G: [261.5, 285.5],
  H: [321, 345],
  K: [358.5, 382.5],
}

const premiumRows = [
  { row: 13, sideY: [643, 674], centerY: [641.5, 672.5] },
  { row: 14, sideY: [717, 748], centerY: [715, 746] },
  { row: 15, sideY: [792.5, 823.5], centerY: [788, 819] },
  { row: 16, sideY: [1025, 1056], centerY: [1018.5, 1049.5] },
  { row: 17, sideY: [1100, 1131], centerY: [1094, 1125] },
]

for (const { row, sideY, centerY } of premiumRows) {
  for (const letter of ['A', 'C', 'D', 'G', 'H', 'K']) {
    const [left, right] = premiumColumns[letter]
    const [top, bottom] = letter === 'D' || letter === 'G' ? centerY : sideY
    addSeat(`${letter}${row}`, 'BUSINESS', 'front', [left, top, right, bottom], 'business-pair')
  }
}

const economyColumns = {
  A: [121, 145],
  B: [146, 170],
  C: [171, 195],
  D: [218, 242],
  E: [243, 267],
  F: [268, 292],
  G: [315, 339],
  H: [340, 364],
  K: [365, 389],
}

const mainEconomyRows = [
  { row: 31, sideY: [1182, 1212], centerY: [1170, 1200] },
  { row: 32, sideY: [1220, 1251], centerY: [1208, 1239] },
  { row: 33, sideY: [1259, 1289], centerY: [1247, 1277] },
  { row: 34, sideY: [1297, 1328], centerY: [1285, 1316] },
  { row: 35, sideY: [1336, 1366], centerY: [1324, 1354] },
  { row: 36, sideY: [1374, 1405], centerY: [1362, 1393] },
  { row: 37, sideY: [1413, 1444], centerY: [1401, 1431] },
  { row: 38, sideY: [1452, 1482], centerY: [1440, 1470] },
  { row: 39, sideY: [1490, 1521], centerY: [1478, 1509] },
  { row: 40, sideY: [1529, 1559], centerY: [1517, 1547] },
  { row: 41, sideY: [1567, 1598], centerY: [1555, 1586] },
  { row: 42, sideY: [1606, 1637], centerY: [1594, 1624] },
  { row: 43, sideY: [1645, 1675], centerY: [1633, 1663] },
]

function addFullEconomyRow(row, sideY, centerY, section) {
  for (const letter of ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'K']) {
    const [left, right] = economyColumns[letter]
    const [top, bottom] = ['D', 'E', 'F'].includes(letter) ? centerY : sideY
    addSeat(`${letter}${row}`, 'ECONOMY', section, [left, top, right, bottom], 'economy')
  }
}

for (const { row, sideY, centerY } of mainEconomyRows) {
  addFullEconomyRow(row, sideY, centerY, 'middle')
}

for (const letter of ['D', 'E', 'F']) {
  const [left, right] = economyColumns[letter]
  addSeat(`${letter}44`, 'ECONOMY', 'rear', [left, 1779, right, 1810], 'economy')
}

const transitionColumns = {
  A: [146, 170],
  C: [171, 195],
  D: economyColumns.D,
  E: economyColumns.E,
  F: economyColumns.F,
  H: [315, 339],
  K: [340, 364],
}

for (const letter of ['A', 'C', 'D', 'E', 'F', 'H', 'K']) {
  const [left, right] = transitionColumns[letter]
  const [top, bottom] = ['D', 'E', 'F'].includes(letter) ? [1817, 1848] : [1810, 1841]
  addSeat(`${letter}45`, 'ECONOMY', 'rear', [left, top, right, bottom], 'economy')
}

const rearEconomyRows = [
  { row: 46, sideY: [1848, 1879], centerY: [1855, 1886] },
  { row: 47, sideY: [1886, 1916], centerY: [1893, 1923] },
  { row: 48, sideY: [1923, 1954], centerY: [1930, 1961] },
  { row: 49, sideY: [1961, 1991], centerY: [1968, 1998] },
  { row: 50, sideY: [1998, 2029], centerY: [2005, 2036] },
  { row: 51, sideY: [2036, 2066], centerY: [2043, 2073] },
  { row: 52, sideY: [2073, 2104], centerY: [2080, 2111] },
  { row: 53, sideY: [2111, 2141], centerY: [2118, 2148] },
  { row: 54, sideY: [2148, 2179], centerY: [2155, 2186] },
  { row: 55, sideY: [2185, 2216], centerY: [2193, 2223] },
  { row: 56, sideY: [2223, 2253], centerY: [2230, 2260] },
  { row: 57, sideY: [2260, 2291], centerY: [2267, 2298] },
  { row: 58, sideY: [2298, 2328], centerY: [2305, 2335] },
]

for (const { row, sideY, centerY } of rearEconomyRows) {
  addFullEconomyRow(row, sideY, centerY, 'rear')
}

function roundedRectanglePath(x, y, width, height, radius) {
  const right = x + width
  const bottom = y + height
  return [
    `M ${format(x + radius)} ${format(y)}`,
    `H ${format(right - radius)}`,
    `Q ${format(right)} ${format(y)} ${format(right)} ${format(y + radius)}`,
    `V ${format(bottom - radius)}`,
    `Q ${format(right)} ${format(bottom)} ${format(right - radius)} ${format(bottom)}`,
    `H ${format(x + radius)}`,
    `Q ${format(x)} ${format(bottom)} ${format(x)} ${format(bottom - radius)}`,
    `V ${format(y + radius)}`,
    `Q ${format(x)} ${format(y)} ${format(x + radius)} ${format(y)}`,
    'Z',
  ].join(' ')
}

function format(value) {
  return Number(value.toFixed(2))
}

function seatGroup(seat) {
  const prefix = seat.cabinClass === 'BUSINESS' ? 'Business' : 'Economy'
  const fill = seat.style === 'business-large'
    ? '#8E929D'
    : seat.style === 'business-pair'
      ? '#49467C'
      : '#8991A6'

  const radius = Math.min(seat.width, seat.height) * 0.1
  const mainPath = roundedRectanglePath(seat.x, seat.y, seat.width, seat.height, radius)
  const cushionY = seat.y + seat.height * 0.78
  const cushionLeft = seat.x + seat.width * 0.16
  const cushionRight = seat.x + seat.width * 0.84
  const lowerY = seat.y + seat.height * 0.91

  return [
    `  <g id="${prefix}-Seat-${seat.seatNo}" data-seat-no="${seat.seatNo}" data-cabin-class="${seat.cabinClass}" data-cabin-section="${seat.section}">`,
    `    <path d="${mainPath}" fill="${fill}" stroke="#404638" stroke-width="1.6"/>`,
    `    <path d="M ${format(cushionLeft)} ${format(cushionY)} Q ${format((cushionLeft + cushionRight) / 2)} ${format(lowerY)} ${format(cushionRight)} ${format(cushionY)}" fill="none" stroke="#E8E9ED" stroke-width="1.6" stroke-linecap="round"/>`,
    `    <path d="M ${format(seat.x + seat.width * 0.2)} ${format(lowerY)} H ${format(seat.x + seat.width * 0.8)}" fill="none" stroke="#D3D6DC" stroke-width="2" stroke-linecap="round"/>`,
    '  </g>',
  ].join('\n')
}

const seatNumbers = seats.map((seat) => seat.seatNo)
const uniqueSeatNumbers = new Set(seatNumbers)

if (seats.length !== 282) {
  throw new Error(`Expected 282 C929 seats, generated ${seats.length}`)
}
if (uniqueSeatNumbers.size !== seats.length) {
  throw new Error('C929 seat numbers must be unique')
}
if (seatNumbers[0] !== 'A11' || seatNumbers.at(-1) !== 'K58') {
  throw new Error(`Unexpected C929 manifest boundaries: ${seatNumbers[0]} to ${seatNumbers.at(-1)}`)
}

const svg = [
  '<svg width="800" height="4076" viewBox="0 0 800 4076" fill="none" xmlns="http://www.w3.org/2000/svg">',
  '  <title>C929-700 seat interaction overlay</title>',
  '  <desc>282 individually addressable seats aligned to C929-700 1.svg.</desc>',
  `  <g id="C929-Seats" data-seat-count="${seats.length}">`,
  ...seats.map(seatGroup),
  '  </g>',
  '</svg>',
  '',
].join('\n')

writeFileSync(outputPath, svg, 'utf8')
console.log(`Generated ${outputPath}`)
console.log(`Seats: ${seats.length}; first: ${seatNumbers[0]}; last: ${seatNumbers.at(-1)}`)
