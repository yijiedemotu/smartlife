import { ElNotification } from 'element-plus'
import { useUserStore } from '@/store/user'
import bus from './bus'

let socket = null
let heartbeat = null
let reconnectTimer = null
let closedByUser = false

/**
 * WebSocket 连接（三端共用一条连接，按角色消费不同消息）
 *   ORDER_STATUS   用户端：订单状态变化
 *   NEW_ORDER      商家端：本店新订单响铃提醒
 *   SECKILL_RESULT 用户端：秒杀结果
 */
export function connectWs() {
  const user = useUserStore()
  if (!user.token || socket || closedByUser) return
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const base = window.location.host
  socket = new WebSocket(`${proto}://${base}/api/ws?token=${encodeURIComponent(user.token)}`)

  socket.onopen = () => {
    heartbeat = setInterval(() => {
      if (socket && socket.readyState === WebSocket.OPEN) socket.send('ping')
    }, 30000)
  }

  socket.onmessage = (evt) => {
    let payload
    try {
      payload = JSON.parse(evt.data)
    } catch {
      return
    }
    dispatch(payload)
  }

  socket.onclose = () => {
    stop()
    if (!closedByUser && useUserStore().token) {
      reconnectTimer = setTimeout(connectWs, 3000)
    }
  }
  socket.onerror = () => socket && socket.close()
}

function dispatch(payload) {
  const user = useUserStore()
  const { type, data } = payload
  if (type === 'ORDER_STATUS') {
    if (user.isUser) {
      ElNotification({
        title: '订单状态更新',
        message: `${data.msg || ''}`,
        type: data.status === 5 ? 'success' : 'info',
        duration: 3000
      })
    }
    bus.emit('ORDER_STATUS', data)
  } else if (type === 'NEW_ORDER') {
    // 商家端专属：只提示自己店铺的新单
    if (user.isMerchant) {
      ElNotification({
        title: '🔔 新订单提醒',
        message: `#${data.number}  ¥${(data.amount / 100).toFixed(2)} · ${data.userName}`,
        type: 'success',
        duration: 8000
      })
      bus.emit('NEW_ORDER', data)
    }
  } else if (type === 'SECKILL_RESULT') {
    if (user.isUser) {
      ElNotification({
        title: data.success ? '🎉 秒杀成功' : '秒杀结果',
        message: data.msg || '',
        type: data.success ? 'success' : 'warning',
        duration: 5000
      })
    }
    bus.emit('SECKILL_RESULT', data)
  }
}

function stop() {
  if (heartbeat) {
    clearInterval(heartbeat)
    heartbeat = null
  }
}

export function closeWs() {
  closedByUser = true
  stop()
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (socket) {
    socket.onclose = null
    socket.close()
    socket = null
  }
  // 允许下次登录重新连接
  setTimeout(() => {
    closedByUser = false
  }, 100)
}
