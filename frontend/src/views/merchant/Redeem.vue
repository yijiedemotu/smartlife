<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="reload">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="1">待核销</el-radio-button>
        <el-radio-button :value="2">已核销</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-alert class="mb" type="info" show-icon :closable="false"
              title="顾客到店出示券后，在此处点击「核销」完成验证；核销动作会记录使用时间，不可撤销。" />

    <el-table :data="records" v-loading="loading" empty-text="暂无券记录">
      <el-table-column prop="id" label="券单号" width="90" />
      <el-table-column prop="voucherTitle" label="券名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="userName" label="领券人" width="120" />
      <el-table-column label="券面额" width="100">
        <template #default="{ row }">¥{{ fen2yuan(row.actualValue) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="领取时间" width="170" />
      <el-table-column prop="useTime" label="核销时间" width="170">
        <template #default="{ row }">{{ row.useTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'warning' : 'success'" size="small">
            {{ VOUCHER_STATUS_TEXT[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" type="success" size="small" @click="redeem(row)">核销</el-button>
          <span v-else class="muted">已完成</span>
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
import { VOUCHER_STATUS_TEXT, fen2yuan } from '@/utils/roles'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref(null)
const loading = ref(false)

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (status.value) params.status = status.value
    const data = await request.get('/merchant/voucher/order/page', { params })
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

async function redeem(row) {
  await ElMessageBox.confirm(
    `确认核销「${row.voucherTitle}」（领券人：${row.userName || '-'}）吗？`, '核销确认', { type: 'warning' }
  )
  await request.post(`/merchant/voucher/order/${row.id}/use`)
  ElMessage.success('核销成功')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.mb { margin-bottom: 12px; }
.muted { color: #b0b8c4; font-size: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
