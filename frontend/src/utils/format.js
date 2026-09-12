/** 分 -> 元 */
export function yuan(fen, withSymbol = true) {
  const v = (Number(fen || 0) / 100).toFixed(2)
  return withSymbol ? `¥${v}` : v
}

export const ORDER_STATUS = {
  1: { text: '待支付', type: 'warning' },
  2: { text: '已支付·待接单', type: 'primary' },
  3: { text: '制作中', type: 'info' },
  4: { text: '配送中', type: 'primary' },
  5: { text: '已完成', type: 'success' },
  6: { text: '已取消', type: 'danger' }
}

export function orderStatusText(s) {
  return ORDER_STATUS[s]?.text || '未知'
}

export function orderStatusType(s) {
  return ORDER_STATUS[s]?.type || 'info'
}

export const VOUCHER_TYPE = { 1: '代金券', 2: '秒杀券' }

export const GENDERS = { 0: '保密', 1: '男', 2: '女' }

/** 北京默认坐标（浏览器拒绝授权时兜底） */
export const DEFAULT_COORD = { lon: 116.486, lat: 39.996 }
