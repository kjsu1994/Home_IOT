const CANVAS_MAX_DPR = 2
const BACKGROUND = [15 / 255, 23 / 255, 42 / 255, 1]

const RENDER_PRESETS = {
  natural: {
    sharpness: 0,
    maxSharpness: 0,
    denoise: 0,
    maxDenoise: 0,
    contrast: 1,
    saturation: 1,
    edgeThreshold: 0.1,
  },
  sharp: {
    sharpness: 0.11,
    maxSharpness: 0.15,
    denoise: 0.035,
    maxDenoise: 0.055,
    contrast: 1.04,
    saturation: 1.03,
    edgeThreshold: 0.09,
  },
  detail: {
    sharpness: 0.16,
    maxSharpness: 0.22,
    denoise: 0.07,
    maxDenoise: 0.1,
    contrast: 1.06,
    saturation: 1.04,
    edgeThreshold: 0.11,
  },
}

export function createCameraRenderer(canvas, options = {}) {
  const webglRenderer = createWebglRenderer(canvas, options)
  if (webglRenderer) {
    return webglRenderer
  }
  return createCanvas2dRenderer(canvas, options)
}

export async function decodeCameraFrame(blob) {
  const decodeStartedAt = performance.now()
  const decoded = await decodeBlob(blob)
  const source = decoded.source
  const width = source.width || source.naturalWidth
  const height = source.height || source.naturalHeight
  return {
    source,
    width,
    height,
    decodeMs: performance.now() - decodeStartedAt,
    close: decoded.close,
  }
}

function createWebglRenderer(canvas, options) {
  const contextOptions = {
    alpha: false,
    antialias: false,
    depth: false,
    stencil: false,
    preserveDrawingBuffer: false,
    desynchronized: true,
    powerPreference: 'high-performance',
    failIfMajorPerformanceCaveat: true,
  }
  const gl = canvas.getContext('webgl2', contextOptions) || canvas.getContext('webgl', contextOptions)
  if (!gl) {
    return null
  }

  try {
    return new WebglCameraRenderer(canvas, gl, options)
  } catch (error) {
    console.warn('Falling back to Canvas2D camera renderer.', error)
    return null
  }
}

class WebglCameraRenderer {
  constructor(canvas, gl, options) {
    this.canvas = canvas
    this.gl = gl
    this.pixelRatioLimit = options.pixelRatioLimit || CANVAS_MAX_DPR
    this.rendererName = typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext ? 'WebGL2' : 'WebGL'
    this.gpuName = resolveGpuName(gl)
    this.program = createProgram(gl, VERTEX_SHADER, FRAGMENT_SHADER)
    this.positionBuffer = gl.createBuffer()
    this.texCoordBuffer = gl.createBuffer()
    this.texture = gl.createTexture()
    this.locations = {
      position: gl.getAttribLocation(this.program, 'a_position'),
      texCoord: gl.getAttribLocation(this.program, 'a_texCoord'),
      image: gl.getUniformLocation(this.program, 'u_image'),
      texelSize: gl.getUniformLocation(this.program, 'u_texelSize'),
      sharpness: gl.getUniformLocation(this.program, 'u_sharpness'),
      denoise: gl.getUniformLocation(this.program, 'u_denoise'),
      contrast: gl.getUniformLocation(this.program, 'u_contrast'),
      saturation: gl.getUniformLocation(this.program, 'u_saturation'),
      edgeThreshold: gl.getUniformLocation(this.program, 'u_edgeThreshold'),
    }

    gl.useProgram(this.program)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.positionBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, FULLSCREEN_POSITIONS, gl.STATIC_DRAW)
    gl.enableVertexAttribArray(this.locations.position)
    gl.vertexAttribPointer(this.locations.position, 2, gl.FLOAT, false, 0, 0)

    gl.bindTexture(gl.TEXTURE_2D, this.texture)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
    gl.uniform1i(this.locations.image, 0)
  }

  async render(blob, options = {}) {
    const decoded = await decodeCameraFrame(blob)
    try {
      return this.renderDecoded(decoded, options)
    } finally {
      decoded.close()
    }
  }

  renderDecoded(decoded, options = {}) {
    const renderStartedAt = performance.now()
    this.draw(decoded.source, decoded.width, decoded.height, options)
    const renderMs = performance.now() - renderStartedAt

    return {
      decodeMs: decoded.decodeMs,
      renderMs,
      renderer: this.rendererName,
      gpu: this.gpuName,
      width: decoded.width,
      height: decoded.height,
    }
  }

  draw(source, sourceWidth, sourceHeight, options) {
    const gl = this.gl
    const target = resolveCanvasTarget(this.canvas, sourceWidth, sourceHeight, this.pixelRatioLimit)
    const preset = RENDER_PRESETS[options.preset] || RENDER_PRESETS.natural
    const tunedPreset = tunePresetForDisplayScale(preset, target.drawWidth / sourceWidth)
    const texCoords = options.rotated ? ROTATED_TEX_COORDS : NATURAL_TEX_COORDS

    resizeCanvas(this.canvas, target.width, target.height)

    gl.useProgram(this.program)
    gl.viewport(0, 0, target.width, target.height)
    gl.clearColor(...BACKGROUND)
    gl.clear(gl.COLOR_BUFFER_BIT)

    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, this.texture)
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false)
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, source)

    gl.bindBuffer(gl.ARRAY_BUFFER, this.positionBuffer)
    gl.enableVertexAttribArray(this.locations.position)
    gl.vertexAttribPointer(this.locations.position, 2, gl.FLOAT, false, 0, 0)

    gl.bindBuffer(gl.ARRAY_BUFFER, this.texCoordBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, texCoords, gl.DYNAMIC_DRAW)
    gl.enableVertexAttribArray(this.locations.texCoord)
    gl.vertexAttribPointer(this.locations.texCoord, 2, gl.FLOAT, false, 0, 0)

    gl.uniform2f(this.locations.texelSize, 1 / sourceWidth, 1 / sourceHeight)
    gl.uniform1f(this.locations.sharpness, tunedPreset.sharpness)
    gl.uniform1f(this.locations.denoise, tunedPreset.denoise)
    gl.uniform1f(this.locations.contrast, preset.contrast)
    gl.uniform1f(this.locations.saturation, preset.saturation)
    gl.uniform1f(this.locations.edgeThreshold, preset.edgeThreshold)

    gl.viewport(target.drawX, target.drawY, target.drawWidth, target.drawHeight)
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
  }

  destroy() {
    const gl = this.gl
    gl.deleteTexture(this.texture)
    gl.deleteBuffer(this.positionBuffer)
    gl.deleteBuffer(this.texCoordBuffer)
    gl.deleteProgram(this.program)
  }
}

function createCanvas2dRenderer(canvas, options) {
  const context = canvas.getContext('2d', { alpha: false, desynchronized: true })
  if (!context) {
    throw new Error('Canvas2D camera renderer is not available.')
  }
  return new Canvas2dCameraRenderer(canvas, context, options)
}

class Canvas2dCameraRenderer {
  constructor(canvas, context, options) {
    this.canvas = canvas
    this.context = context
    this.pixelRatioLimit = options.pixelRatioLimit || CANVAS_MAX_DPR
    this.rendererName = 'Canvas2D'
    this.gpuName = 'unavailable'
  }

  async render(blob, options = {}) {
    const decoded = await decodeCameraFrame(blob)
    try {
      return this.renderDecoded(decoded, options)
    } finally {
      decoded.close()
    }
  }

  renderDecoded(decoded, options = {}) {
    const renderStartedAt = performance.now()
    this.draw(decoded.source, decoded.width, decoded.height, options)
    const renderMs = performance.now() - renderStartedAt

    return {
      decodeMs: decoded.decodeMs,
      renderMs,
      renderer: 'Canvas2D',
      gpu: 'unavailable',
      width: decoded.width,
      height: decoded.height,
    }
  }

  draw(source, sourceWidth, sourceHeight, options) {
    const target = resolveCanvasTarget(this.canvas, sourceWidth, sourceHeight, this.pixelRatioLimit)
    const context = this.context
    const preset = RENDER_PRESETS[options.preset] || RENDER_PRESETS.natural

    resizeCanvas(this.canvas, target.width, target.height)
    context.setTransform(1, 0, 0, 1, 0, 0)
    context.clearRect(0, 0, target.width, target.height)
    context.fillStyle = '#0f172a'
    context.fillRect(0, 0, target.width, target.height)
    context.save()
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    context.filter = `contrast(${preset.contrast}) saturate(${preset.saturation})`

    if (options.rotated) {
      context.translate(target.drawX + target.drawWidth / 2, target.drawY + target.drawHeight / 2)
      context.rotate(Math.PI)
      context.drawImage(source, -target.drawWidth / 2, -target.drawHeight / 2, target.drawWidth, target.drawHeight)
    } else {
      context.drawImage(source, target.drawX, target.drawY, target.drawWidth, target.drawHeight)
    }
    context.restore()
  }

  destroy() {
    this.context.setTransform(1, 0, 0, 1, 0, 0)
    this.context.clearRect(0, 0, this.canvas.width, this.canvas.height)
  }
}

async function decodeBlob(blob) {
  if ('createImageBitmap' in window) {
    const bitmap = await createImageBitmap(blob)
    return {
      source: bitmap,
      close: () => bitmap.close(),
    }
  }

  const objectUrl = URL.createObjectURL(blob)
  const image = new Image()
  image.decoding = 'async'
  try {
    await new Promise((resolve, reject) => {
      image.onload = resolve
      image.onerror = reject
      image.src = objectUrl
    })
    return {
      source: image,
      close: () => URL.revokeObjectURL(objectUrl),
    }
  } catch (error) {
    URL.revokeObjectURL(objectUrl)
    throw error
  }
}

function resolveCanvasTarget(canvas, sourceWidth, sourceHeight, pixelRatioLimit) {
  const bounds = canvas.getBoundingClientRect()
  const cssWidth = Math.max(1, Math.round(bounds.width || sourceWidth))
  const cssHeight = Math.max(1, Math.round(bounds.height || sourceHeight))
  const pixelRatio = Math.min(window.devicePixelRatio || 1, pixelRatioLimit)
  const width = Math.max(1, Math.round(cssWidth * pixelRatio))
  const height = Math.max(1, Math.round(cssHeight * pixelRatio))
  const scale = Math.min(width / sourceWidth, height / sourceHeight)
  const drawWidth = Math.max(1, Math.round(sourceWidth * scale))
  const drawHeight = Math.max(1, Math.round(sourceHeight * scale))

  return {
    width,
    height,
    drawWidth,
    drawHeight,
    drawX: Math.floor((width - drawWidth) / 2),
    drawY: Math.floor((height - drawHeight) / 2),
  }
}

function resizeCanvas(canvas, width, height) {
  if (canvas.width !== width) {
    canvas.width = width
  }
  if (canvas.height !== height) {
    canvas.height = height
  }
}

function tunePresetForDisplayScale(preset, displayScale) {
  if (displayScale <= 1 || preset.sharpness === 0) {
    return preset
  }

  const boost = Math.min(0.38, (displayScale - 1) * 0.18)
  return {
    ...preset,
    sharpness: Math.min(preset.maxSharpness, preset.sharpness * (1 + boost)),
    denoise: Math.min(preset.maxDenoise, preset.denoise * (1 + boost * 0.5)),
  }
}

function resolveGpuName(gl) {
  const debugInfo = gl.getExtension('WEBGL_debug_renderer_info')
  if (!debugInfo) {
    return 'available'
  }
  const vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL)
  const renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL)
  return [vendor, renderer].filter(Boolean).join(' / ') || 'available'
}

function createProgram(gl, vertexSource, fragmentSource) {
  const vertexShader = createShader(gl, gl.VERTEX_SHADER, vertexSource)
  const fragmentShader = createShader(gl, gl.FRAGMENT_SHADER, fragmentSource)
  const program = gl.createProgram()
  gl.attachShader(program, vertexShader)
  gl.attachShader(program, fragmentShader)
  gl.linkProgram(program)
  gl.deleteShader(vertexShader)
  gl.deleteShader(fragmentShader)

  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const error = gl.getProgramInfoLog(program)
    gl.deleteProgram(program)
    throw new Error(error || 'Failed to link camera renderer shader program.')
  }

  return program
}

function createShader(gl, type, source) {
  const shader = gl.createShader(type)
  gl.shaderSource(shader, source)
  gl.compileShader(shader)

  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const error = gl.getShaderInfoLog(shader)
    gl.deleteShader(shader)
    throw new Error(error || 'Failed to compile camera renderer shader.')
  }

  return shader
}

const FULLSCREEN_POSITIONS = new Float32Array([
  -1, 1,
  1, 1,
  -1, -1,
  1, -1,
])

const NATURAL_TEX_COORDS = new Float32Array([
  0, 0,
  1, 0,
  0, 1,
  1, 1,
])

const ROTATED_TEX_COORDS = new Float32Array([
  1, 1,
  0, 1,
  1, 0,
  0, 0,
])

const VERTEX_SHADER = `
attribute vec2 a_position;
attribute vec2 a_texCoord;
varying vec2 v_texCoord;

void main() {
  gl_Position = vec4(a_position, 0.0, 1.0);
  v_texCoord = a_texCoord;
}
`

const FRAGMENT_SHADER = `
precision mediump float;

uniform sampler2D u_image;
uniform vec2 u_texelSize;
uniform float u_sharpness;
uniform float u_denoise;
uniform float u_contrast;
uniform float u_saturation;
uniform float u_edgeThreshold;
varying vec2 v_texCoord;

vec3 applySaturation(vec3 color, float saturation) {
  float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
  return mix(vec3(luma), color, saturation);
}

void main() {
  vec3 center = texture2D(u_image, v_texCoord).rgb;
  vec3 north = texture2D(u_image, v_texCoord + vec2(0.0, -u_texelSize.y)).rgb;
  vec3 south = texture2D(u_image, v_texCoord + vec2(0.0, u_texelSize.y)).rgb;
  vec3 west = texture2D(u_image, v_texCoord + vec2(-u_texelSize.x, 0.0)).rgb;
  vec3 east = texture2D(u_image, v_texCoord + vec2(u_texelSize.x, 0.0)).rgb;
  vec3 blur = (north + south + west + east) * 0.25;
  float edge = length(center - blur);
  float flatArea = 1.0 - smoothstep(u_edgeThreshold, u_edgeThreshold * 2.0, edge);
  vec3 denoised = mix(center, blur, u_denoise * flatArea);
  vec3 sharpened = denoised + (denoised - blur) * (u_sharpness * 4.0);
  vec3 color = clamp(sharpened, 0.0, 1.0);
  color = (color - 0.5) * u_contrast + 0.5;
  color = applySaturation(color, u_saturation);
  gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
`
