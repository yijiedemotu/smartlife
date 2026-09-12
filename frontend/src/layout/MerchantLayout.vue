<template>
  <el-container class="merchant-layout">
    <el-aside width="216px" class="aside">
      <div class="m-logo">🏪 智联生活 · 商家端</div>

      <!-- 平台管理员代管时：需要先选择要管理的店铺 -->
      <div v-if="user.isAdmin" class="shop-picker">
        <el-select v-model="currentShopId" placeholder="选择店铺代管" size="small" style="width: 100%"
                   filterable @change="onShopChange">
          <el-option v-for="s in adminShops" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </div>

      <el-menu :default-active="activeMenu" router background-color="#0f2438" text-color="#c9d4df"
               active-text-color="#ffffff">
        <el-menu-item index="/merchant/dashboard"><el-icon><DataLine /></el-icon>经营看板</el-menu-item>
        <el-menu-item index="/merchant/orders"><el-icon><List /></el-icon>订单履约</el-menu-item>
        <el-menu-item index="/merchant/products"><el-icon><Goods /></el-icon>商品管理</el-menu-item>
        <el-menu-item index="/merchant/vouchers"><el-icon><Ticket /></el-icon>优惠券管理</el-menu-item>
        <el-menu-item index="/merchant/redeem"><el-icon><Stamp /></el-icon>券核销</el-menu-item>
        <el-menu-item index="/merchant/shop"><el-icon><Shop /></el-icon>店铺与入驻</el-menu-item>
      </el-menu>

      <div class="aside-foot">
        <div class="shop-name">{{ user.isAdmin ? '平台代管模式' : (shop?.name || '尚未入驻店铺') }}</div>
        <el-tag v-if="!user.isAdmin && shop" :type="AUDIT_TAG[shop.auditStatus] || 'info'" size="small">
          {{ AUDIT_TEXT[shop.auditStatus] }}
        </el-tag>
      </div>
    </el-aside>

    <el-container>
      <el-header class="m-header">
        <div class="title">{{ route.meta.title || '' }}</div>
        <div class="right">
          <el-tooltip content="切换到用户端">
            <el-button v-if="user.isUser" link @click="$router.push('/user/home')">用户端</el-button>
          </el-tooltip>
          <el-tooltip content="平台管理端">
            <el-button v-if="user.isAdmin" link @click="$router.push('/admin/dashboard')">管理端</el-button>
          </el-tooltip>
          <el-dropdown @command="onCommand">
            <span class="user-name">
              <el-avatar :size="28" :src="user.avatar || ''">{{ (user.nickname || '商')[0] }}</el-avatar>
              <span>{{ user.nickname }}</span>
              <el-tag size="small" effect="plain" type="success">商家</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="shop">店铺与入驻</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="m-main">
        <router-view :shop-id="currentShopId" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { connectWs, closeWs } from '@/utils/ws'
import { AUDIT_TAG, AUDIT_TEXT } from '@/utils/roles'
import request from '@/utils/request'
import bus from '@/utils/bus'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const activeMenu = computed(() => route.path)
const shop = ref(null)
const adminShops = ref([])
const currentShopId = ref(user.shopId ?? null)

/** 平台管理员代管：选择店铺后广播给子页面（通过 provide/inject 更规范，这里用 bus 简单联动） */
function onShopChange(id) {
  const target = adminShops.value.find(s => s.id === id)
  user.setShop(target || {})
  bus.emit('MERCHANT_SHOP_CHANGED', id)
  ElMessage.success(`已切换到：${target?.name || ''}`)
}

async function loadShop() {
  try {
    const data = await request.get('/merchant/shop/mine', { silent: true })
    if (data?.admin) {
      adminShops.value = data.shops || []
      if (!currentShopId.value && adminShops.value.length) {
        currentShopId.value = adminShops.value[0].id
        user.setShop(adminShops.value[0])
      }
    } else {
      shop.value = data?.shop || null
      user.setShop(shop.value || {})
      currentShopId.value = shop.value?.id ?? null
    }
  } catch {
    shop.value = null
  }
}

function onCommand(cmd) {
  if (cmd === 'shop') router.push('/merchant/shop')
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(() => {
      user.logout()
      closeWs()
      router.push('/login')
    }).catch(() => {})
  }
}

const offNewOrder = bus.on('NEW_ORDER', () => {
  if (route.path === '/merchant/orders') bus.emit('MERCHANT_RELOAD_ORDERS', {})
})
const offShopChange = bus.on('MERCHANT_SHOP_CHANGED', () => loadShop())

watch(() => route.path, () => {
  if (route.path === '/merchant/shop') loadShop()
})

onMounted(() => {
  connectWs()
  loadShop()
})
onBeforeUnmount(() => {
  offNewOrder()
  offShopChange()
  closeWs()
})
</script>

<style scoped>
.merchant-layout { min-height: 100vh; }
.aside { background: #0f2438; display: flex; flex-direction: column; }
.m-logo { color: #fff; font-weight: 700; padding: 18px 16px; font-size: 16px; white-space: nowrap; }
.shop-picker { padding: 0 12px 12px; }
.merchant-layout :deep(.el-menu) { border-right: none; flex: 1; }
.aside-foot {
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.aside-foot .shop-name { color: #c9d4df; font-size: 12px; }
.m-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}
.title { font-size: 17px; font-weight: 600; }
.right { display: flex; align-items: center; gap: 8px; }
.user-name { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #333; outline: none; }
.m-main { background: #f0f2f5; }
</style>
