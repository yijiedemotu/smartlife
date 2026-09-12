<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="auditStatus" placeholder="审核状态" clearable style="width: 140px" @change="reload">
          <el-option label="待审核" :value="0" />
          <el-option label="营业中" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已停业" :value="3" />
        </el-select>
        <el-select v-model="typeId" placeholder="经营类目" clearable style="width: 160px" @change="reload">
          <el-option v-for="t in types" :key="t.id" :label="`${t.icon || ''} ${t.name}`" :value="t.id" />
        </el-select>
        <el-input v-model="keyword" placeholder="店铺名/商圈" clearable style="width: 200px"
                  @keyup.enter="reload" @clear="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-table :data="records" v-loading="loading" empty-text="暂无店铺">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="店铺名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="merchantId" label="商家ID" width="90" />
      <el-table-column label="类目" width="120">
        <template #default="{ row }">{{ typeName(row.typeId) }}</template>
      </el-table-column>
      <el-table-column prop="area" label="商圈" width="100" />
      <el-table-column prop="address" label="地址" min-width="170" show-overflow-tooltip />
      <el-table-column label="评分" width="80">
        <template #default="{ row }">{{ row.score || '-' }}</template>
      </el-table-column>
      <el-table-column label="审核状态" width="110">
        <template #default="{ row }">
          <el-tag :type="AUDIT_TAG[row.auditStatus] || 'info'" size="small">
            {{ AUDIT_TEXT[row.auditStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="营业" width="80">
        <template #default="{ row }">
          <el-tag :type="row.openStatus === 1 ? 'success' : 'info'" size="small" effect="plain">
            {{ row.openStatus === 1 ? '营业' : '休息' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditRemark" label="最近审核意见" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="goMerchant(row)">代管经营</el-button>
          <el-button v-if="row.auditStatus !== 3" size="small" type="warning" plain
                     @click="changeStatus(row, false)">停业</el-button>
          <el-button v-else size="small" type="success" plain @click="changeStatus(row, true)">恢复</el-button>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { AUDIT_TAG, AUDIT_TEXT } from '@/utils/roles'

const router = useRouter()
const user = useUserStore()

const records = ref([])
const types = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const auditStatus = ref(null)
const typeId = ref(null)
const keyword = ref('')
const loading = ref(false)

function typeName(id) {
  const t = types.value.find(x => x.id === id)
  return t ? `${t.icon || ''} ${t.name}` : '-'
}

async function loadTypes() {
  try {
    types.value = await request.get('/admin/type/list', { silent: true })
  } catch {
    types.value = []
  }
}

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (auditStatus.value !== null) params.auditStatus = auditStatus.value
    if (typeId.value) params.typeId = typeId.value
    if (keyword.value) params.keyword = keyword.value
    const data = await request.get('/admin/shop/page', { params })
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

/** 平台代管：把选中店铺写入本地上下文，再跳商家端 */
function goMerchant(row) {
  user.setShop({ id: row.id, name: row.name, auditStatus: row.auditStatus })
  router.push('/merchant/dashboard')
}

async function changeStatus(row, restore) {
  const { value } = await ElMessageBox.prompt(
    restore ? `确认恢复「${row.name}」营业？` : `确认将「${row.name}」停业整顿？停业后用户端不可见。`,
    restore ? '恢复营业' : '停业整顿',
    {
      inputValue: restore ? '平台恢复营业' : '违规停业整顿',
      inputPlaceholder: '处置原因（会记入审计日志）',
      type: restore ? 'success' : 'warning'
    }
  )
  await request.post(`/admin/shop/${row.id}/audit`, { approved: restore, remark: value })
  ElMessage.success(restore ? '已恢复营业' : '已停业')
  reload()
}

async function remove(row) {
  await ElMessageBox.confirm(
    `确定删除店铺「${row.name}」吗？该操作会同时清理其缓存与附近搜索索引，且不可恢复。`,
    '危险操作', { type: 'error', confirmButtonText: '确认删除' }
  )
  await request.delete(`/admin/shop/${row.id}`)
  ElMessage.success('已删除')
  reload()
}

onMounted(() => {
  loadTypes()
  reload()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
