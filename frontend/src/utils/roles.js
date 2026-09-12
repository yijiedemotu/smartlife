/**
 * 三端角色与路由归属的统一常量与工具
 *
 * 后端约定：
 *   role 1 = 用户端   2 = 商家端   3 = 管理端
 *   登录接口返回 homePath，前端只需按 homePath 跳转，不在前端硬编码业务规则
 */

export const ROLE = {
  USER: 1,
  MERCHANT: 2,
  ADMIN: 3
}

export const ROLE_TEXT = {
  [ROLE.USER]: '用户',
  [ROLE.MERCHANT]: '商家',
  [ROLE.ADMIN]: '平台管理员'
}

/** 角色 -> 端侧首页（与后端 UserService.homePath 保持一致，作为兜底） */
export function homePathOf(role) {
  const r = Number(role)
  if (r === ROLE.MERCHANT) return '/merchant/dashboard'
  if (r === ROLE.ADMIN) return '/admin/dashboard'
  return '/user/home'
}

/** 路由是否属于某个端（用于路由守卫校验越权跳转） */
export function pathRole(path) {
  if (path.startsWith('/merchant')) return ROLE.MERCHANT
  if (path.startsWith('/admin')) return ROLE.ADMIN
  return ROLE.USER
}

/** 店铺审核状态文案 */
export const AUDIT_TEXT = {
  0: '待审核',
  1: '营业中',
  2: '已驳回',
  3: '已停业'
}

export const AUDIT_TAG = {
  0: 'warning',
  1: 'success',
  2: 'danger',
  3: 'info'
}

/** 订单状态文案与标签色 */
export const ORDER_STATUS_TEXT = {
  1: '待支付',
  2: '待接单',
  3: '已接单',
  4: '配送中',
  5: '已完成',
  6: '已取消'
}

export const ORDER_STATUS_TAG = {
  1: 'warning',
  2: 'danger',
  3: 'primary',
  4: 'primary',
  5: 'success',
  6: 'info'
}

/** 券状态文案 */
export const VOUCHER_STATUS_TEXT = {
  1: '未使用',
  2: '已使用',
  3: '已过期'
}

/** 申请单状态 */
export const APPLY_STATUS_TEXT = {
  0: '待审核',
  1: '已通过',
  2: '已驳回'
}

export const APPLY_STATUS_TAG = {
  0: 'warning',
  1: 'success',
  2: 'danger'
}

/** 分 -> 元 */
export function fen2yuan(fen) {
  const n = Number(fen || 0) / 100
  return n.toFixed(2)
}
