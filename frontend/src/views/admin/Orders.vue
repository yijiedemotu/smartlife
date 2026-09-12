<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="shopId" placeholder="全部店铺" clearable filterable style="width: 220px" @change="reload">
          <el-option v-for="s in shops" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-radio-group v-model="status" @change="reload">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="2">待接单</el-radio-button>
          <el-radio-button :value="4">配送中</el-radio-button>
          <el-radio-button :value="5">已完成</el-radio-button>
          <el-radio-button :value="6">已取消</el-radio-button>
        </el-radio-group>
      </div>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-alert class="mb" type="info" show-icon :closable="false"
              title="平台侧用于全平台订单巡检与客服兜底处置（正常履约请由商家在商家端操作）。" />

    <el-table :data="records" v-loading="loading" empty-text="暂无订单">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail">
            <div class="detail-line"><b>收货地址</b>{{ row.address }}</div>
            <div class="detail-line"><b>备注</b>{{ row.remark || '无' }}</div>
            <el-table :data="row.items || []" size="small" border style="margin-top:8px">
              <el-table-column prop="productName" label="商品" />
              <el-table-column label="单价" width="100">
                <template #default="s">¥{{ fen2yuan(s.row.price) }}</template>
              </el-table-column>
              <el-table-column prop="count" label="数量" width="80" />
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="number" label="订单号" width="180" />
      <el-table-column prop="userName" label="用户" width="110" />
      <el-table-column prop="shopName" label="店铺" min-width="140" show-overflow-tooltip />
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ fen2yuan(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="ORDER_STATUS_TAG[row.status]" size="small">{{ ORDER_STATUS_TEXT[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="170" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 2" size="small" @click="act(row, 'accept')">代接单</el-button>
          <el-button v-if="row.status === 4" size="small" type="success" @click="act(row, 'finish')">代完成</el-button>
          <el-button v-if="row.status === 1 || row.status === 2" size="small" type="danger" plain
                     @click="act(row, 'cancel')">兜底取消</el-button>
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
import { ORDER_STATUS_TAG, ORDER_STATUS_TEXT, fen2yuan } from '@/utils/roles'

const records = ref([])
const shops = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const shopId = ref(null)
const status = ref(null)
const loading = ref(false)

async function loadShops() {
  try {
    shops.value = await request.get('/admin/shop/list', { silent: true })
  } catch {
    shops.value = []
  }
}

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (shopId.value) params.shopId = shopId.value
    if (status.value) params.status = status.value
    const data = await request.get('/admin/order/page', { params })
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

async function act(row, action) {
  const text = { accept: '代接单', finish: '代完成', cancel: '兜底取消' }[action]
  await ElMessageBox.confirm(`确认对订单 ${row.number} 执行「${text}」？`, '平台代管操作', { type: 'warning' })
  await request.post(`/admin/order/${row.id}/${action}`)
  ElMessage.success(`${text}成功`)
  reload()
}

onMounted(() => {
  loadShops()
  reload()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; }
.mb { margin-bottom: 12px; }
.detail { padding: 8px 16px; background: #fafbfc; }
.detail-line { font-size: 13px; color: #4a5568; margin-bottom: 4px; }
.detail-line b { display: inline-block; width: 84px; color: #8a94a6; font-weight: 500; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
