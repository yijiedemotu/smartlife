<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="shopId" placeholder="全部店铺" clearable filterable style="width: 240px" @change="reload">
        <el-option v-for="s in shops" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-alert class="mb" type="warning" show-icon :closable="false"
              title="强制下架会立即让该券在用户端不可见、不可领取（秒杀券同时停止抢购）；商家自主上下架请让其在商家端操作。" />

    <el-table :data="records" v-loading="loading" empty-text="暂无优惠券">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="shopId" label="店铺ID" width="90" />
      <el-table-column prop="title" label="券名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 2 ? 'danger' : 'primary'" size="small">
            {{ row.type === 2 ? '秒杀券' : '代金券' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="券面额" width="100">
        <template #default="{ row }">¥{{ fen2yuan(row.actualValue) }}</template>
      </el-table-column>
      <el-table-column label="库存/已售" width="110">
        <template #default="{ row }">{{ row.stock }} / {{ row.sold }}</template>
      </el-table-column>
      <el-table-column label="商家状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="平台状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.auditStatus === 1 ? 'success' : 'danger'" size="small" effect="plain">
            {{ row.auditStatus === 1 ? '正常' : '已强制下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :type="row.auditStatus === 1 ? 'warning' : 'success'" plain
                     @click="audit(row)">{{ row.auditStatus === 1 ? '强制下架' : '恢复' }}</el-button>
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
    const data = await request.get('/admin/voucher/page', { params })
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

async function audit(row) {
  const to = row.auditStatus === 1 ? 0 : 1
  if (to === 0) {
    await ElMessageBox.confirm(`确认强制下架券「${row.title}」？`, '违规处置', { type: 'warning' })
  }
  await request.post(`/admin/voucher/${row.id}/audit`, null, { params: { auditStatus: to } })
  ElMessage.success(to === 1 ? '已恢复正常' : '已强制下架')
  reload()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除券「${row.title}」吗？该操作不可恢复。`, '危险操作',
    { type: 'error', confirmButtonText: '确认删除' })
  await request.delete(`/admin/voucher/${row.id}`)
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
