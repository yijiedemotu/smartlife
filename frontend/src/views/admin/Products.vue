<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="shopId" placeholder="全部店铺" clearable filterable style="width: 240px" @change="reload">
        <el-option v-for="s in shops" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-alert class="mb" type="info" show-icon :closable="false"
              title="平台定位为治理：可查看全平台商品、下架违规商品、删除脏数据；商品新增与日常维护由商家在商家端完成。" />

    <el-table :data="records" v-loading="loading" empty-text="暂无商品">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="shopId" label="店铺ID" width="90" />
      <el-table-column prop="name" label="商品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column label="单价" width="100">
        <template #default="{ row }">¥{{ fen2yuan(row.price) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="90" />
      <el-table-column prop="sales" label="销量" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain
                     @click="changeStatus(row)">{{ row.status === 1 ? '强制下架' : '恢复上架' }}</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
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
import { fen2yuan } from '@/utils/roles'

const records = ref([])
const shops = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const shopId = ref(null)
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
    const data = await request.get('/admin/product/page', { params })
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

async function changeStatus(row) {
  const to = row.status === 1 ? 0 : 1
  await request.post(`/admin/product/${row.id}/status`, null, { params: { status: to } })
  ElMessage.success(to === 1 ? '已恢复上架' : '已强制下架')
  reload()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除商品「${row.name}」吗？该操作不可恢复。`, '危险操作',
    { type: 'error', confirmButtonText: '确认删除' })
  await request.delete(`/admin/product/${row.id}`)
  ElMessage.success('已删除')
  reload()
}

onMounted(() => {
  loadShops()
  reload()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.mb { margin-bottom: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
