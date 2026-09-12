import { createRouter, createWebHistory } from 'vue-router'
import { ROLE, homePathOf, pathRole } from '@/utils/roles'

/**
 * 三端路由划分（单应用多端）：
 *   /user/**      用户端   role=1
 *   /merchant/**  商家端   role=2（平台管理员 role=3 可代管进入）
 *   /admin/**     管理端   role=3
 *   公开页面      /login /register/merchant
 *
 * 守卫双重校验：登录态 + 端侧归属，避免手改 localStorage 的 role 越权跳转；
 * 真正的数据权限永远由后端拦截器（AuthInterceptor + RoleGuard）兜底。
 */
const routes = [
  { path: '/', redirect: '/user/home' },
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/register/merchant',
    name: 'MerchantRegister',
    component: () => import('@/views/RegisterMerchant.vue'),
    meta: { public: true, title: '商家入驻' }
  },
  {
    path: '/user',
    component: () => import('@/layout/UserLayout.vue'),
    meta: { role: ROLE.USER },
    children: [
      { path: '', redirect: '/user/home' },
      { path: 'home', name: 'Home', component: () => import('@/views/user/Home.vue'), meta: { title: '首页' } },
      { path: 'shop/:id', name: 'ShopDetail', component: () => import('@/views/user/ShopDetail.vue'), meta: { title: '店铺' } },
      { path: 'seckill', name: 'Seckill', component: () => import('@/views/user/Seckill.vue'), meta: { title: '限时秒杀' } },
      { path: 'mate', name: 'Mate', component: () => import('@/views/user/Mate.vue'), meta: { title: '找饭搭子' } },
      { path: 'chat', name: 'Chat', component: () => import('@/views/user/Chat.vue'), meta: { title: '消息' } },
      { path: 'cart', name: 'Cart', component: () => import('@/views/user/Cart.vue'), meta: { title: '购物车' } },
      { path: 'orders', name: 'Orders', component: () => import('@/views/user/Orders.vue'), meta: { title: '我的订单' } },
      { path: 'vouchers', name: 'MyVouchers', component: () => import('@/views/user/MyVouchers.vue'), meta: { title: '我的券包' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/user/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },
  {
    path: '/merchant',
    component: () => import('@/layout/MerchantLayout.vue'),
    meta: { role: ROLE.MERCHANT, allowAdmin: true },
    children: [
      { path: '', redirect: '/merchant/dashboard' },
      { path: 'dashboard', name: 'MerchantDashboard', component: () => import('@/views/merchant/Dashboard.vue'), meta: { title: '经营看板' } },
      { path: 'orders', name: 'MerchantOrders', component: () => import('@/views/merchant/Orders.vue'), meta: { title: '订单履约' } },
      { path: 'products', name: 'MerchantProducts', component: () => import('@/views/merchant/Products.vue'), meta: { title: '商品管理' } },
      { path: 'vouchers', name: 'MerchantVouchers', component: () => import('@/views/merchant/Vouchers.vue'), meta: { title: '优惠券管理' } },
      { path: 'redeem', name: 'MerchantRedeem', component: () => import('@/views/merchant/Redeem.vue'), meta: { title: '券核销' } },
      { path: 'shop', name: 'MerchantShop', component: () => import('@/views/merchant/ShopProfile.vue'), meta: { title: '店铺与入驻' } }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layout/AdminLayout.vue'),
    meta: { role: ROLE.ADMIN },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '平台看板' } },
      { path: 'applies', name: 'AdminApplies', component: () => import('@/views/admin/Applies.vue'), meta: { title: '入驻审核' } },
      { path: 'shops', name: 'AdminShops', component: () => import('@/views/admin/Shops.vue'), meta: { title: '店铺治理' } },
      { path: 'orders', name: 'AdminOrders', component: () => import('@/views/admin/Orders.vue'), meta: { title: '全平台订单' } },
      { path: 'products', name: 'AdminProducts', component: () => import('@/views/admin/Products.vue'), meta: { title: '商品巡检' } },
      { path: 'vouchers', name: 'AdminVouchers', component: () => import('@/views/admin/Vouchers.vue'), meta: { title: '优惠券治理' } },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/Users.vue'), meta: { title: '账号治理' } },
      { path: 'types', name: 'AdminTypes', component: () => import('@/views/admin/Types.vue'), meta: { title: '类目字典' } },
      { path: 'audit', name: 'AdminAuditLog', component: () => import('@/views/admin/AuditLog.vue'), meta: { title: '审计日志' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/user/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('sl_user') || '{}')
  } catch {
    return {}
  }
}

router.beforeEach((to) => {
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '智联生活三端平台'

  const user = readUser()
  const role = Number(user.role)
  const logged = !!user.token && !!role

  if (to.meta.public) {
    // 已登录用户访问登录页：直接回到自己的端
    if (to.path === '/login' && logged) {
      return homePathOf(role)
    }
    return true
  }
  if (!logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 端侧归属校验：允许管理员进入商家端代管，其余情况回自己的端
  const target = pathRole(to.path)
  if (target === ROLE.ADMIN && role !== ROLE.ADMIN) {
    return homePathOf(role)
  }
  if (target === ROLE.MERCHANT && role !== ROLE.MERCHANT && role !== ROLE.ADMIN) {
    return homePathOf(role)
  }
  if (target === ROLE.USER && role !== ROLE.USER) {
    return homePathOf(role)
  }
  return true
})

export default router
