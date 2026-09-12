import { defineStore } from 'pinia'
import { homePathOf, ROLE } from '@/utils/roles'

const KEY = 'sl_user'

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}')
  } catch {
    return {}
  }
}

/**
 * 三端统一登录态。
 *
 * 注意：state 必须显式声明全部键（Pinia/Vue3 对未声明键的赋值不生效），
 * 否则首次登录 setLogin 写入后 save() 仍存出空对象，路由守卫会误判未登录。
 */
export const useUserStore = defineStore('user', {
  state: () => {
    const saved = load()
    return {
      token: saved.token || '',
      id: saved.id ?? null,
      role: saved.role ?? null,
      nickname: saved.nickname || '',
      avatar: saved.avatar || '',
      homePath: saved.homePath || '',
      // 商家端上下文（登录时后端下发，避免首屏多次请求）
      shopId: saved.shopId ?? null,
      shopName: saved.shopName || '',
      shopAuditStatus: saved.shopAuditStatus ?? null
    }
  },
  getters: {
    isLogin: (s) => !!s.token,
    isUser: (s) => Number(s.role) === ROLE.USER,
    isMerchant: (s) => Number(s.role) === ROLE.MERCHANT,
    isAdmin: (s) => Number(s.role) === ROLE.ADMIN,
    /** 是否可以进入商家端（商家 + 平台管理员代管） */
    canOperateShop: (s) => Number(s.role) === ROLE.MERCHANT || Number(s.role) === ROLE.ADMIN,
    /** 登录后应落地的主页 */
    landing: (s) => s.homePath || homePathOf(s.role)
  },
  actions: {
    setLogin(data) {
      this.token = data.token || ''
      this.id = data.id ?? null
      this.role = data.role ?? null
      this.nickname = data.nickname || ''
      this.avatar = data.avatar || ''
      this.homePath = data.homePath || homePathOf(data.role)
      this.shopId = data.shopId ?? null
      this.shopName = data.shopName || ''
      this.shopAuditStatus = data.shopAuditStatus ?? null
      this.save()
    },
    save() {
      localStorage.setItem(KEY, JSON.stringify({
        token: this.token,
        id: this.id,
        role: this.role,
        nickname: this.nickname,
        avatar: this.avatar,
        homePath: this.homePath,
        shopId: this.shopId,
        shopName: this.shopName,
        shopAuditStatus: this.shopAuditStatus
      }))
    },
    setToken(token) {
      this.token = token
      this.save()
    },
    setMe(profile) {
      this.nickname = profile.nickname || this.nickname
      this.avatar = profile.avatar || this.avatar
      this.save()
    },
    setShop(shop) {
      this.shopId = shop?.id ?? null
      this.shopName = shop?.name || ''
      this.shopAuditStatus = shop?.auditStatus ?? null
      this.save()
    },
    logout() {
      this.token = ''
      this.id = null
      this.role = null
      this.nickname = ''
      this.avatar = ''
      this.homePath = ''
      this.shopId = null
      this.shopName = ''
      this.shopAuditStatus = null
      localStorage.removeItem(KEY)
    }
  }
})
