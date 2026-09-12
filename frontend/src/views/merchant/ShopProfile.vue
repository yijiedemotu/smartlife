<template>
  <div>
    <!-- ① 未入驻：引导提交入驻资料 -->
    <el-card v-if="!isAdmin && !shop" shadow="never" class="page-card">
      <template #header><span>店铺入驻申请</span></template>
      <el-alert type="warning" show-icon :closable="false" class="mb"
                title="您还没有店铺。提交入驻资料后由平台管理员审核，审核通过即可开始经营。" />
      <ApplyForm ref="applyFormRef" :types="types" @submitted="loadAll" />
    </el-card>

    <!-- ② 已有店铺：状态卡 + 资料维护 -->
    <template v-else-if="!isAdmin">
      <el-card shadow="never" class="page-card">
        <template #header>
          <div class="head">
            <span>我的店铺</span>
            <el-tag :type="AUDIT_TAG[shop.auditStatus] || 'info'">{{ AUDIT_TEXT[shop.auditStatus] }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="店铺名称">{{ shop.name }}</el-descriptions-item>
          <el-descriptions-item label="经营类目">{{ typeName(shop.typeId) }}</el-descriptions-item>
          <el-descriptions-item label="商圈">{{ shop.area || '-' }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ shop.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ shop.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ shop.score || '-' }}</el-descriptions-item>
          <el-descriptions-item label="经度">{{ shop.lon || '-' }}</el-descriptions-item>
          <el-descriptions-item label="纬度">{{ shop.lat || '-' }}</el-descriptions-item>
          <el-descriptions-item label="营业状态">
            <el-tag :type="shop.openStatus === 1 ? 'success' : 'info'" size="small">
              {{ shop.openStatus === 1 ? '营业中' : '休息中' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核意见" :span="3">
            <span :class="{ danger: shop.auditStatus === 2 }">{{ shop.auditRemark || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-switch v-model="openStatus" :active-value="1" :inactive-value="0"
                     :disabled="shop.auditStatus !== 1" active-text="营业" inactive-text="休息"
                     @change="toggleOpen" />
          <el-tooltip v-if="shop.auditStatus !== 1" content="审核通过后才能切换营业状态">
            <el-icon class="tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
          <div class="right">
            <el-button @click="editVisible = true">维护可自助修改的信息</el-button>
            <el-button type="primary" plain @click="changeVisible = true">提交资料变更申请</el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="page-card">
        <template #header>
          <div class="head">
            <span>入驻 / 变更申请记录</span>
            <el-tag v-if="hasPending" type="warning" size="small">有申请正在审核中</el-tag>
          </div>
        </template>
        <el-table :data="applies" size="small" empty-text="暂无申请记录">
          <el-table-column prop="id" label="单号" width="80" />
          <el-table-column prop="shopName" label="店铺名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ row.type === 2 ? '资料变更' : '首次入驻' }}</template>
          </el-table-column>
          <el-table-column prop="contactName" label="联系人" width="110" />
          <el-table-column prop="licenseNo" label="营业执照号" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="APPLY_STATUS_TAG[row.status]" size="small">{{ APPLY_STATUS_TEXT[row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="auditRemark" label="审核意见" min-width="160" show-overflow-tooltip />
          <el-table-column prop="createTime" label="提交时间" width="170" />
        </el-table>
      </el-card>
    </template>

    <!-- ③ 平台管理员视角 -->
    <el-card v-else shadow="never" class="page-card">
      <template #header><span>平台代管说明</span></template>
      <el-alert type="info" show-icon :closable="false"
                title="您当前以平台管理员身份进入商家端：请在左侧顶部选择要代管的店铺，即可查看与处理该店铺的商品、订单与优惠券。" />
      <el-divider />
      <el-table :data="adminShops" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="店铺" min-width="150" />
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="AUDIT_TAG[row.auditStatus] || 'info'" size="small">
              {{ AUDIT_TEXT[row.auditStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="area" label="商圈" width="110" />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/admin/shops?shopId=${row.id}`)">去治理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 自助修改弹窗 -->
    <el-dialog v-model="editVisible" title="维护店铺信息" width="540px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="联系电话">
          <el-input v-model="editForm.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="editForm.address" maxlength="255" />
        </el-form-item>
        <el-form-item label="经度">
          <el-input-number v-model="editForm.lon" :precision="6" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input-number v-model="editForm.lat" :precision="6" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="图片URL">
          <el-input v-model="editForm.images" placeholder="多个用英文逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 资料变更申请 -->
    <el-dialog v-model="changeVisible" title="提交资料变更申请" width="640px">
      <el-alert type="info" show-icon :closable="false" class="mb"
                title="店铺名称、经营类目、地址等关键信息变更需平台重新审核；审核通过后自动生效。" />
      <ApplyForm ref="changeFormRef" :types="types" :initial="shop" @submitted="onChanged" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { APPLY_STATUS_TAG, APPLY_STATUS_TEXT, AUDIT_TAG, AUDIT_TEXT } from '@/utils/roles'
import ApplyForm from './components/ApplyForm.vue'

const user = useUserStore()
const isAdmin = computed(() => user.isAdmin)

const shop = ref(null)
const applies = ref([])
const types = ref([])
const adminShops = ref([])
const hasPending = ref(false)
const openStatus = ref(1)

const applyFormRef = ref()
const changeFormRef = ref()
const editVisible = ref(false)
const changeVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ phone: '', address: '', lon: null, lat: null, images: '' })

function typeName(id) {
  const t = types.value.find(x => x.id === id)
  return t ? `${t.icon || ''} ${t.name}` : '-'
}

async function loadAll() {
  try {
    const data = await request.get('/merchant/shop/mine')
    if (data?.admin) {
      adminShops.value = data.shops || []
      return
    }
    shop.value = data?.shop || null
    hasPending.value = !!data?.hasPendingApply
    user.setShop(shop.value || {})
    openStatus.value = shop.value?.openStatus ?? 1
    if (shop.value) {
      Object.assign(editForm, {
        phone: shop.value.phone || '',
        address: shop.value.address || '',
        lon: shop.value.lon,
        lat: shop.value.lat,
        images: shop.value.images || ''
      })
    }
    const page = await request.get('/merchant/apply/page', { params: { page: 1, size: 20 } })
    applies.value = page?.records || []
  } catch {
    shop.value = null
  }
}

async function loadTypes() {
  try {
    types.value = await request.get('/shop/type/list')
  } catch {
    types.value = []
  }
}

async function toggleOpen(val) {
  try {
    await request.post('/merchant/shop/open', null, { params: { open: val === 1 } })
    ElMessage.success(val === 1 ? '已开始营业' : '已切换为休息中')
  } catch {
    openStatus.value = val === 1 ? 0 : 1
  }
}

async function saveProfile() {
  saving.value = true
  try {
    await request.post('/merchant/shop/profile', { ...editForm })
    ElMessage.success('已保存')
    editVisible.value = false
    loadAll()
  } finally {
    saving.value = false
  }
}

function onChanged() {
  changeVisible.value = false
  loadAll()
}

onMounted(() => {
  loadTypes()
  loadAll()
})
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.actions { display: flex; align-items: center; gap: 8px; margin-top: 16px; }
.actions .right { margin-left: auto; display: flex; gap: 8px; }
.tip-icon { color: #e6a23c; }
.mb { margin-bottom: 12px; }
.danger { color: #f56c6c; }
</style>
