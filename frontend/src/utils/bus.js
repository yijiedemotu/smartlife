import { ElMessage } from 'element-plus'

/**
 * 极简事件总线：用于 WS 推送驱动页面局部刷新/弹窗
 */
const listeners = new Map()

export function on(event, fn) {
  if (!listeners.has(event)) listeners.set(event, new Set())
  listeners.get(event).add(fn)
  return () => off(event, fn)
}

export function off(event, fn) {
  listeners.get(event)?.delete(fn)
}

export function emit(event, payload) {
  listeners.get(event)?.forEach((fn) => {
    try {
      fn(payload)
    } catch (e) {
      console.error('[bus]', event, e)
    }
  })
}

export function toast(msg, type = 'success') {
  ElMessage({ message: msg, type, showClose: true })
}

export default { on, off, emit, toast }
