<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="reload">
        <el-radio-button :value="0">待审核</el-radio-button>
        <el-radio-button :value="1">已通过</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
        <el-radio-button :value="null">全部</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="reload">刷新</el-button>
    </div>

    <el-alert class="mb" type="info" show-icon :closable="false"
              title="审核通过后系统会自动为商家创建店铺（状态「营业中」）并写入审计日志；驳回时请填写具体原因，商家可据此补充后重新提交。" />

    <el-table :data="records" v-loading="loading" empty-text="暂无申请单">
      <el-table-column prop="id" label="单号" width="70" />
      <el-table-column prop="shopName" label="店铺名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ row.type === 2 ? '资料变更' : '首次入驻' }}</template>
      </el-table-column>
      <el-table-column prop="merchantId" label="商家ID" width="90" />
      <el-table-column prop="contactName" label="联系人" width="110" />
      <el-table-column prop="contactPhone" label="联系电话" width="130" />
      <el-table-column prop="area" label="商圈" width="100" />
      <el-table-column prop="address" label="地址" min-width="170" show-overflow-tooltip />
      <el-table-column prop="licenseNo" label="营业执照号" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="APPLY_STATUS_TAG[row.status]" size="small">{{ APPLY_STATUS_TEXT[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="detail(row)">查看</el-button>
          <template v-if="row.status === 0">
            <el-button type="success" size="small" @click="audit(row, true)">通过</el-button>
            <el-button type="danger" size="small" plain @click="audit(row, false)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="onPage" />

    <!-- 资质详情 -->
    <el-dialog v-model="detailVisible" title="入驻资质详情" width="640px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="店铺名称" :span="2">{{ current.shopName }}</el-descriptions-item>
        <el-descriptions-item label="商家ID">{{ current.merchantId }}</el-descriptions-item>
        <el-descriptions-item label="申请类型">{{ current.type === 2 ? '资料变更' : '首次入驻' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ current.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ current.contactPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商圈">{{ current.area || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经度">{{ current.lon || '-' }}</el-descriptions-item>
        <el-descriptions-item label="纬度">{{ current.lat || '-' }}</el-descriptions-item>
        <el-descriptions-item label="营业执照号">{{ current.licenseNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ current.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="店铺简介" :span="2">{{ current.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2">{{ current.auditRemark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="current?.licenseImg" class="license">
        <div class="license-label">证照图片</div>
        <el-image :src="current.licenseImg" fit="contain" class="license-img"
                  :preview-src-list="[current.licenseImg]" preview-teleported />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="current && current.status === 0">
          <el-button type="danger" @click="audit(current, false)">驳回</el-button>
          <el-button type="success" @click="audit(current, true)">审核通过</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { APPLY_STATUS_TAG, APPLY_STATUS_TEXT } from '@/utils/roles'
import bus from '@/utils/bus'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const current = ref(null)

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (status.value !== null) params.status = status.value
    const data = await request.get('/admin/apply/page', { params })
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

function detail(row) {
  current.value = row
  detailVisible.value = true
}

async function audit(row, approved) {
  const { value } = await ElMessageBox.prompt(
    approved ? `确认通过「${row.shopName}」的入驻申请？通过后将立即开通店铺。` : `请填写驳回「${row.shopName}」的原因：`,
    approved ? '审核通过' : '驳回申请',
    {
      inputValue: approved ? '资质齐全，审核通过' : '',
      inputPlaceholder: '审核意见 / 驳回原因',
      inputValidator: (v) => (approved || (v && v.trim()) ? true : '驳回必须填写原因'),
      type: approved ? 'success' : 'warning'
    }
  )
  await request.post(`/admin/apply/${row.id}/audit`, { approved, remark: value })
  ElMessage.success(approved ? '已通过，店铺已开通' : '已驳回')
  detailVisible.value = false
  bus.emit('APPLY_HANDLED', {})
  reload()
}

onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.mb { margin-bottom: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
.license { margin-top: 12px; }
.license-label { font-size: 13px; color: #8a94a6; margin-bottom: 6px; }
.license-img { width: 100%; max-height: 240px; border-radius: 6px; background: #f5f7fa; }
</style>
