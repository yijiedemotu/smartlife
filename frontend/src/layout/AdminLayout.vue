<template>
  <el-container class="admin-layout">
    <el-aside width="216px" class="aside">
      <div class="admin-logo">🛡️ 智联生活 · 管理端</div>
      <el-menu :default-active="activeMenu" router background-color="#1b1f2a" text-color="#c9d4df"
               active-text-color="#ffffff">
        <el-menu-item index="/admin/dashboard"><el-icon><Odometer /></el-icon>平台看板</el-menu-item>
        <el-menu-item index="/admin/applies">
          <el-icon><DocumentChecked /></el-icon>
          <span>入驻审核</span>
          <el-badge v-if="pendingCount" :value="pendingCount" class="menu-badge" />
        </el-menu-item>
        <el-menu-item index="/admin/shops"><el-icon><Shop /></el-icon>店铺治理</el-menu-item>
        <el-menu-item index="/admin/orders"><el-icon><List /></el-icon>全平台订单</el-menu-item>
        <el-menu-item index="/admin/products"><el-icon><Goods /></el-icon>商品巡检</el-menu-item>
        <el-menu-item index="/admin/vouchers"><el-icon><Ticket /></el-icon>优惠券治理</el-menu-item>
        <el-menu-item index="/admin/users"><el-icon><UserFilled /></el-icon>账号治理</el-menu-item>
        <el-menu-item index="/admin/types"><el-icon><Grid /></el-icon>类目字典</el-menu-item>
        <el-menu-item index="/admin/audit"><el-icon><Memo /></el-icon>审计日志</el-menu-item>
      </el-menu>

      <div class="aside-foot">
        <div class="foot-line">平台监管 · 数据全景</div>
        <el-button link type="primary" size="small" @click="$router.push('/merchant/dashboard')">
          进入商家端代管 →
        </el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="title">{{ route.meta.title || '' }}</div>
        <el-dropdown @command="onCommand">
          <span class="user-name">
            <el-avatar :size="28" :src="user.avatar || ''">{{ (user.nickname || '管')[0] }}</el-avatar>
            <span>{{ user.nickname }}</span>
            <el-tag size="small" effect="plain" type="danger">管理员</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="audit">审计日志</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { connectWs, closeWs } from '@/utils/ws'
import request from '@/utils/request'
import bus from '@/utils/bus'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const activeMenu = computed(() => route.path)
const pendingCount = ref(0)

/** 待审核入驻数量：管理端角标，提醒平台处理 */
async function loadPending() {
  try {
    const data = await request.get('/admin/stats/overview', { silent: true })
    pendingCount.value = Number(data?.pendingApplies || 0)
  } catch {
    pendingCount.value = 0
  }
}

function onCommand(cmd) {
  if (cmd === 'audit') router.push('/admin/audit')
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(() => {
      user.logout()
      closeWs()
      router.push('/login')
    }).catch(() => {})
  }
}

watch(() => route.path, () => loadPending())

const offApply = bus.on('APPLY_HANDLED', () => loadPending())

onMounted(() => {
  connectWs()
  loadPending()
})
onBeforeUnmount(() => {
  offApply()
  closeWs()
})
</script>

<style scoped>
.admin-layout { min-height: 100vh; }
.aside { background: #1b1f2a; display: flex; flex-direction: column; }
.admin-logo { color: #fff; font-weight: 700; padding: 18px 16px; font-size: 16px; white-space: nowrap; }
.admin-layout :deep(.el-menu) { border-right: none; flex: 1; }
.menu-badge { margin-left: 8px; }
.aside-foot {
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.aside-foot .foot-line { color: #8a94a6; font-size: 12px; }
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}
.title { font-size: 17px; font-weight: 600; }
.user-name { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #333; outline: none; }
.admin-main { background: #f0f2f5; }
</style>
