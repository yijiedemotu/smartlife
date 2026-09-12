<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="filters">
        <el-tag type="info" effect="plain">关键操作全留痕：审核入驻 / 停业 / 封禁 / 强制下架 / 删除</el-tag>
      </div>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-table :data="records" v-loading="loading" empty-text="暂无审计记录">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="createTime" label="时间" width="180" />
      <el-table-column label="操作人" width="150">
        <template #default="{ row }">
          <span>#{{ row.actorId }}</span>
          <el-tag :type="row.actorRole === 3 ? 'danger' : 'success'" size="small" effect="plain" class="ml">
            {{ row.actorRole === 3 ? '管理员' : '商家' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="170">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ actionText(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标" width="140">
        <template #default="{ row }">{{ row.targetType || '-' }} #{{ row.targetId ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="detail" label="明细" min-width="240" show-overflow-tooltip />
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="onPage" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '@/utils/request'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const ACTION_TEXT = {
  APPLY_APPROVE: '通过入驻申请',
  APPLY_REJECT: '驳回入驻申请',
  SHOP_CLOSE: '店铺停业整顿',
  SHOP_REOPEN: '店铺恢复营业',
  SHOP_DELETE: '删除店铺',
  TYPE_SAVE: '保存类目',
  TYPE_DELETE: '删除类目',
  USER_BAN: '封禁账号',
  USER_UNBAN: '解封账号',
  PRODUCT_UP: '商品上架',
  PRODUCT_DOWN: '商品下架',
  PRODUCT_DELETE: '删除商品',
  VOUCHER_BAN: '强制下架券',
  VOUCHER_RESTORE: '恢复券',
  VOUCHER_DELETE: '删除券'
}

function actionText(action) {
  return ACTION_TEXT[action] || action
}

async function reload() {
  loading.value = true
  try {
    const data = await request.get('/admin/audit/page', { params: { page: page.value, size: size.value } })
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

onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.ml { margin-left: 6px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
