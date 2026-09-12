<template>
  <el-container class="user-layout">
    <el-header class="header">
      <div class="logo" @click="$router.push('/user/home')">🍜 智联生活</div>
      <div class="nav">
        <router-link to="/user/home">首页</router-link>
        <router-link to="/user/seckill">限时秒杀</router-link>
        <router-link to="/user/mate">找饭搭子</router-link>
        <router-link to="/user/chat">消息<el-badge v-if="msgUnread" :value="msgUnread" class="cart-badge" /></router-link>
        <router-link to="/user/cart">购物车<el-badge v-if="cartCount" :value="cartCount" class="cart-badge" /></router-link>
        <router-link to="/user/orders">我的订单</router-link>
        <router-link to="/user/vouchers">我的券包</router-link>
      </div>
      <el-dropdown @command="onCommand">
        <span class="user-name">
          <el-avatar :size="28" :src="user.avatar || ''">{{ (user.nickname || '友')[0] }}</el-avatar>
          <span>{{ user.nickname }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心/签到</el-dropdown-item>
            <el-dropdown-item v-if="user.isMerchant" command="merchant" divided>进入商家端</el-dropdown-item>
            <el-dropdown-item v-if="user.isAdmin" command="admin" divided>进入管理端</el-dropdown-item>
            <el-dropdown-item v-if="user.isAdmin" command="merchant-admin">进入商家端（代管）</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { connectWs, closeWs } from '@/utils/ws'
import request from '@/utils/request'
import bus from '@/utils/bus'

const user = useUserStore()
const router = useRouter()
const route = useRoute()
const cartCount = ref(0)
const msgUnread = ref(0)
let msgTimer = null

async function loadCartCount() {
  try {
    const data = await request.get('/cart/count', { silent: true })
    cartCount.value = data?.count || 0
  } catch {
    cartCount.value = 0
  }
}

/** 私信未读总数：轮询会话列表求和（每 6s），导航栏红点 */
async function loadMsgUnread() {
  try {
    const data = await request.get('/chat/contacts', { silent: true })
    msgUnread.value = (data || []).reduce((s, c) => s + Number(c.unread || 0), 0)
  } catch {
    msgUnread.value = 0
  }
}

function onCommand(cmd) {
  if (cmd === 'profile') router.push('/user/profile')
  if (cmd === 'merchant' || cmd === 'merchant-admin') router.push('/merchant/dashboard')
  if (cmd === 'admin') router.push('/admin/dashboard')
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(() => {
      user.logout()
      closeWs()
      router.push('/login')
    }).catch(() => {})
  }
}

// 购物车角标在导航变化时刷新
watch(() => route.path, () => {
  loadCartCount()
  loadMsgUnread()
})

const offStatus = bus.on('ORDER_STATUS', () => loadCartCount())
const offCartChanged = bus.on('CART_CHANGED', () => loadCartCount())

onMounted(() => {
  connectWs()
  loadCartCount()
  loadMsgUnread()
  msgTimer = setInterval(loadMsgUnread, 6000)
})
onBeforeUnmount(() => {
  offStatus()
  offCartChanged()
  if (msgTimer) clearInterval(msgTimer)
  closeWs()
})
</script>

<style scoped>
.user-layout { min-height: 100vh; }
.header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
}
.logo { font-size: 20px; font-weight: 700; color: #ff6b35; cursor: pointer; white-space: nowrap; }
.nav { flex: 1; display: flex; gap: 22px; }
.nav a { color: #444; text-decoration: none; font-size: 15px; }
.nav a.router-link-active { color: #ff6b35; font-weight: 600; }
.cart-badge { margin-left: 4px; }
.user-name { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #333; outline: none; }
.main { max-width: 1200px; width: 100%; margin: 0 auto; }
</style>
