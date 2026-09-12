<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="role" placeholder="全部角色" clearable style="width: 150px" @change="reload">
          <el-option label="用户 (1)" :value="1" />
          <el-option label="商家 (2)" :value="2" />
          <el-option label="管理员 (3)" :value="3" />
        </el-select>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="reload">
          <el-option label="正常" :value="1" />
          <el-option label="已封禁" :value="0" />
        </el-select>
        <el-input v-model="keyword" placeholder="手机号/昵称" clearable style="width: 200px"
                  @keyup.enter="reload" @clear="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-table :data="records" v-loading="loading" empty-text="暂无账号">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="头像" width="70">
        <template #default="{ row }">
          <el-avatar :size="30" :src="row.avatar || ''">{{ (row.nickname || '?')[0] }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="nickname" label="昵称" min-width="130" show-overflow-tooltip />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="roleTag(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130">
        <template #default="{ row }">{{ row.phone || '（已脱敏）' }}</template>
      </el-table-column>
      <el-table-column prop="wechat" label="微信/联系" width="130" />
      <el-table-column prop="address" label="地址" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '已封禁' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="170" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.role !== 3" size="small" :type="row.status === 1 ? 'danger' : 'success'" plain
                     @click="toggle(row)">{{ row.status === 1 ? '封禁' : '解封' }}</el-button>
          <span v-else class="muted">受保护</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="onPage" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const role = ref(null)
const status = ref(null)
const keyword = ref('')
const loading = ref(false)

function roleText(r) {
  return { 1: '用户', 2: '商家', 3: '管理员' }[Number(r)] || '未知'
}

function roleTag(r) {
  return { 1: 'primary', 2: 'success', 3: 'danger' }[Number(r)] || 'info'
}

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (role.value) params.role = role.value
    if (status.value !== null && status.value !== '') params.status = status.value
    if (keyword.value) params.keyword = keyword.value
    const data = await request.get('/admin/user/page', { params })
    records.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  page.value = p
  reload()
}

async function toggle(row) {
  const ban = row.status === 1
  await ElMessageBox.confirm(
    ban ? `确认封禁「${row.nickname}」？封禁后该账号无法登录。` : `确认解封「${row.nickname}」？`,
    ban ? '封禁账号' : '解封账号', { type: 'warning' }
  )
  await request.post(`/admin/user/${row.id}/status`, null, { params: { status: ban ? 0 : 1 } })
  ElMessage.success(ban ? '已封禁' : '已解封')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; }
.muted { color: #b0b8c4; font-size: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
