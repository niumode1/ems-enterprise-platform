import type { RealtimeCapabilities } from '@/types'

export interface RealtimeEvent {
  type: string
  data?: Record<string, unknown>
  at?: string
}

const EVENT_NAME = 'ems-realtime'
const RECONNECT_DELAYS = [1000, 2000, 5000, 10_000, 30_000]

let socket: WebSocket | null = null
let reconnectTimer: number | undefined
let heartbeatTimer: number | undefined
let heartbeatSeconds = 25
let attempts = 0
let manualClose = true
let token = ''

/**
 * Opens the realtime channel for the signed-in user. Safe to call more than once;
 * an existing open/connecting socket is reused. Company policy is read from the
 * backend, so a deployment that disables realtime simply never connects.
 */
export function startRealtime(accessToken: string) {
  if (!accessToken) return
  token = accessToken
  manualClose = false
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }
  void bootstrap()
}

export function stopRealtime() {
  manualClose = true
  clearReconnect()
  stopHeartbeat()
  const current = socket
  socket = null
  if (current) {
    try {
      current.close()
    } catch {
      // socket is already gone
    }
  }
}

export function isRealtimeConnected() {
  return socket?.readyState === WebSocket.OPEN
}

async function bootstrap() {
  const capabilities = await fetchCapabilities()
  if (manualClose) return
  if (capabilities && !capabilities.enabled) {
    emit({ type: 'REALTIME_DISABLED', data: { ...capabilities } })
    return
  }
  if (capabilities) {
    heartbeatSeconds = capabilities.heartbeatSeconds
  }
  open()
}

function open() {
  if (manualClose) return
  const latest = localStorage.getItem('ems_access_token')
  if (latest) token = latest
  if (!token) return
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${scheme}://${window.location.host}/api/v1/ws?token=${encodeURIComponent(token)}`
  const ws = new WebSocket(url)
  socket = ws

  ws.onopen = () => {
    attempts = 0
    startHeartbeat()
  }

  ws.onmessage = (message) => {
    let event: RealtimeEvent
    try {
      event = JSON.parse(message.data) as RealtimeEvent
    } catch {
      return
    }
    if (!event || !event.type || event.type === 'PONG') return
    if (event.type === 'CONNECTED') {
      const seconds = Number(event.data?.heartbeatSeconds)
      if (Number.isFinite(seconds) && seconds >= 0) {
        heartbeatSeconds = seconds
        startHeartbeat()
      }
    }
    emit(event)
  }

  ws.onclose = () => {
    if (socket === ws) socket = null
    stopHeartbeat()
    emit({ type: 'REALTIME_DISCONNECTED' })
    scheduleReconnect()
  }

  ws.onerror = () => {
    try {
      ws.close()
    } catch {
      // close will trigger the reconnect path
    }
  }
}

function scheduleReconnect() {
  if (manualClose) return
  if (!localStorage.getItem('ems_access_token')) return
  clearReconnect()
  const delay = RECONNECT_DELAYS[Math.min(attempts, RECONNECT_DELAYS.length - 1)]
  attempts += 1
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = undefined
    if (!manualClose) open()
  }, delay)
}

function startHeartbeat() {
  stopHeartbeat()
  if (heartbeatSeconds <= 0) return
  heartbeatTimer = window.setInterval(() => {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'PING' }))
    }
  }, heartbeatSeconds * 1000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }
}

function clearReconnect() {
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
  }
}

function emit(event: RealtimeEvent) {
  window.dispatchEvent(new CustomEvent<RealtimeEvent>(EVENT_NAME, { detail: event }))
}

async function fetchCapabilities(): Promise<RealtimeCapabilities | null> {
  try {
    const response = await fetch('/api/v1/realtime/capabilities', {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!response.ok) return null
    const body = (await response.json()) as { code?: number; data?: RealtimeCapabilities }
    return body.code === 0 && body.data ? body.data : null
  } catch {
    return null
  }
}
