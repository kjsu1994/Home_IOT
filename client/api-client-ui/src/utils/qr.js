const DATA_CODEWORDS_L = {
  1: { data: 19, ecc: 7, align: [] },
  2: { data: 34, ecc: 10, align: [6, 18] },
  3: { data: 55, ecc: 15, align: [6, 22] },
  4: { data: 80, ecc: 20, align: [6, 26] },
  5: { data: 108, ecc: 26, align: [6, 30] },
}

const FORMAT_L_MASK_0 = 0b111011111000100
const PAD_BYTES = [0xec, 0x11]
const GF_EXP = new Array(512)
const GF_LOG = new Array(256)

let x = 1
for (let i = 0; i < 255; i += 1) {
  GF_EXP[i] = x
  GF_LOG[x] = i
  x <<= 1
  if (x & 0x100) {
    x ^= 0x11d
  }
}
for (let i = 255; i < 512; i += 1) {
  GF_EXP[i] = GF_EXP[i - 255]
}

export function createQrSvgDataUri(text, options = {}) {
  const moduleSize = options.moduleSize || 7
  const quietZone = options.quietZone || 4
  const matrix = createQrMatrix(text)
  const size = matrix.length
  const svgSize = (size + quietZone * 2) * moduleSize
  const darkModules = []

  for (let row = 0; row < size; row += 1) {
    for (let col = 0; col < size; col += 1) {
      if (matrix[row][col]) {
        darkModules.push(`<rect x="${(col + quietZone) * moduleSize}" y="${(row + quietZone) * moduleSize}" width="${moduleSize}" height="${moduleSize}"/>`)
      }
    }
  }

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${svgSize} ${svgSize}" width="${svgSize}" height="${svgSize}" shape-rendering="crispEdges"><rect width="100%" height="100%" fill="#fff"/><g fill="#0f172a">${darkModules.join('')}</g></svg>`
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

function createQrMatrix(text) {
  const dataBytes = Array.from(new TextEncoder().encode(text))
  const version = chooseVersion(dataBytes.length)
  const config = DATA_CODEWORDS_L[version]
  const dataCodewords = encodeData(dataBytes, config.data)
  const eccCodewords = createEcc(dataCodewords, config.ecc)
  const bits = bytesToBits([...dataCodewords, ...eccCodewords])
  const size = version * 4 + 17
  const matrix = makeMatrix(size)
  const reserved = makeMatrix(size)

  addFunctionPatterns(matrix, reserved, version, config.align)
  addDataBits(matrix, reserved, bits)
  addFormatBits(matrix, reserved)
  return matrix
}

function chooseVersion(byteLength) {
  for (const [version, config] of Object.entries(DATA_CODEWORDS_L)) {
    if (byteLength <= byteCapacity(config.data)) {
      return Number(version)
    }
  }
  throw new Error('QR 데이터가 너무 깁니다.')
}

function byteCapacity(dataCodewords) {
  return Math.floor((dataCodewords * 8 - 4 - 8) / 8)
}

function encodeData(dataBytes, dataCodewordCount) {
  const bits = []
  appendBits(bits, 0b0100, 4)
  appendBits(bits, dataBytes.length, 8)
  dataBytes.forEach((byte) => appendBits(bits, byte, 8))

  const capacityBits = dataCodewordCount * 8
  const terminator = Math.min(4, capacityBits - bits.length)
  appendBits(bits, 0, terminator)
  while (bits.length % 8 !== 0) {
    bits.push(0)
  }

  const result = []
  for (let i = 0; i < bits.length; i += 8) {
    result.push(bitsToByte(bits.slice(i, i + 8)))
  }
  let padIndex = 0
  while (result.length < dataCodewordCount) {
    result.push(PAD_BYTES[padIndex % 2])
    padIndex += 1
  }
  return result
}

function createEcc(dataCodewords, degree) {
  const generator = createGenerator(degree)
  const result = new Array(degree).fill(0)

  dataCodewords.forEach((byte) => {
    const factor = byte ^ result.shift()
    result.push(0)
    for (let i = 0; i < degree; i += 1) {
      result[i] ^= gfMultiply(generator[i + 1], factor)
    }
  })

  return result
}

function createGenerator(degree) {
  let generator = [1]
  for (let i = 0; i < degree; i += 1) {
    generator = multiplyPolynomials(generator, [1, GF_EXP[i]])
  }
  return generator
}

function multiplyPolynomials(left, right) {
  const result = new Array(left.length + right.length - 1).fill(0)
  for (let i = 0; i < left.length; i += 1) {
    for (let j = 0; j < right.length; j += 1) {
      result[i + j] ^= gfMultiply(left[i], right[j])
    }
  }
  return result
}

function gfMultiply(left, right) {
  if (left === 0 || right === 0) {
    return 0
  }
  return GF_EXP[GF_LOG[left] + GF_LOG[right]]
}

function makeMatrix(size) {
  return Array.from({ length: size }, () => new Array(size).fill(false))
}

function addFunctionPatterns(matrix, reserved, version, alignPositions) {
  const size = matrix.length
  addFinder(matrix, reserved, 0, 0)
  addFinder(matrix, reserved, size - 7, 0)
  addFinder(matrix, reserved, 0, size - 7)

  for (let i = 8; i < size - 8; i += 1) {
    setFunctionModule(matrix, reserved, i, 6, i % 2 === 0)
    setFunctionModule(matrix, reserved, 6, i, i % 2 === 0)
  }

  alignPositions.forEach((row) => {
    alignPositions.forEach((col) => {
      if (reserved[row][col]) {
        return
      }
      addAlignment(matrix, reserved, col - 2, row - 2)
    })
  })

  setFunctionModule(matrix, reserved, 8, version * 4 + 9, true)
  reserveFormatAreas(reserved)
}

function addFinder(matrix, reserved, left, top) {
  for (let row = -1; row <= 7; row += 1) {
    for (let col = -1; col <= 7; col += 1) {
      const x = left + col
      const y = top + row
      if (!inBounds(matrix, x, y)) {
        continue
      }
      const dark = col >= 0 && col <= 6 && row >= 0 && row <= 6 && (col === 0 || col === 6 || row === 0 || row === 6 || (col >= 2 && col <= 4 && row >= 2 && row <= 4))
      setFunctionModule(matrix, reserved, x, y, dark)
    }
  }
}

function addAlignment(matrix, reserved, left, top) {
  for (let row = 0; row < 5; row += 1) {
    for (let col = 0; col < 5; col += 1) {
      const dark = col === 0 || col === 4 || row === 0 || row === 4 || (col === 2 && row === 2)
      setFunctionModule(matrix, reserved, left + col, top + row, dark)
    }
  }
}

function reserveFormatAreas(reserved) {
  const size = reserved.length
  for (let i = 0; i <= 8; i += 1) {
    reserved[8][i] = true
    reserved[i][8] = true
  }
  for (let i = 0; i < 8; i += 1) {
    reserved[8][size - 1 - i] = true
    reserved[size - 1 - i][8] = true
  }
}

function addDataBits(matrix, reserved, bits) {
  const size = matrix.length
  let bitIndex = 0
  let upward = true

  for (let right = size - 1; right > 0; right -= 2) {
    if (right === 6) {
      right -= 1
    }
    for (let vertical = 0; vertical < size; vertical += 1) {
      const row = upward ? size - 1 - vertical : vertical
      for (let offset = 0; offset < 2; offset += 1) {
        const col = right - offset
        if (reserved[row][col]) {
          continue
        }
        const bit = bitIndex < bits.length ? bits[bitIndex] === 1 : false
        matrix[row][col] = bit !== mask(row, col)
        bitIndex += 1
      }
    }
    upward = !upward
  }
}

function addFormatBits(matrix, reserved) {
  const size = matrix.length
  for (let i = 0; i <= 5; i += 1) {
    setFunctionModule(matrix, reserved, i, 8, getBit(FORMAT_L_MASK_0, i))
  }
  setFunctionModule(matrix, reserved, 7, 8, getBit(FORMAT_L_MASK_0, 6))
  setFunctionModule(matrix, reserved, 8, 8, getBit(FORMAT_L_MASK_0, 7))
  setFunctionModule(matrix, reserved, 8, 7, getBit(FORMAT_L_MASK_0, 8))
  for (let i = 9; i < 15; i += 1) {
    setFunctionModule(matrix, reserved, 8, 14 - i, getBit(FORMAT_L_MASK_0, i))
  }

  for (let i = 0; i < 8; i += 1) {
    setFunctionModule(matrix, reserved, 8, size - 1 - i, getBit(FORMAT_L_MASK_0, i))
  }
  for (let i = 8; i < 15; i += 1) {
    setFunctionModule(matrix, reserved, size - 15 + i, 8, getBit(FORMAT_L_MASK_0, i))
  }
}

function setFunctionModule(matrix, reserved, x, y, dark) {
  matrix[y][x] = dark
  reserved[y][x] = true
}

function inBounds(matrix, x, y) {
  return y >= 0 && y < matrix.length && x >= 0 && x < matrix.length
}

function mask(row, col) {
  return (row + col) % 2 === 0
}

function bytesToBits(bytes) {
  const bits = []
  bytes.forEach((byte) => appendBits(bits, byte, 8))
  return bits
}

function appendBits(bits, value, length) {
  for (let i = length - 1; i >= 0; i -= 1) {
    bits.push((value >>> i) & 1)
  }
}

function bitsToByte(bits) {
  return bits.reduce((value, bit) => (value << 1) | bit, 0)
}

function getBit(value, index) {
  return ((value >>> index) & 1) === 1
}