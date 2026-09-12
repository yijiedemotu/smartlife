<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="left">
        <el-alert v-if="!isAdmin && !canOperate" type="warning" show-icon :closable="false"
                  title="店铺尚未通过审核，暂不能新增或修改优惠券（可先查看）" />
      </div>
      <div class="right">
        <el-button type="primary" :disabled="!isAdmin && !canOperate" @click="openEdit()">新增优惠券</el-button>
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" empty-text="暂无优惠券">
      <el-table-column prop="title" label="券名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="subTitle" label="副标题" min-width="140" show-overflow-tooltip />
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
      <el-table-column label="售价" width="90">
        <template #default="{ row }">¥{{ fen2yuan(row.payValue) }}</template>
      </el-table-column>
      <el-table-column label="库存/已售" width="110">
        <template #default="{ row }">{{ row.stock }} / {{ row.sold }}</template>
      </el-table-column>
      <el-table-column label="秒杀时间窗" min-width="200">
        <template #default="{ row }">
          <span v-if="row.type === 2">{{ (row.beginTime || '').slice(5, 16) }} ~ {{ (row.endTime || '').slice(5, 16) }}</span>
          <span v-else class="muted">长期有效</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
          <el-tag v-if="row.auditStatus === 0" type="danger" size="small" style="margin-left:4px">平台下架</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain
                     @click="toggle(row)">{{ row.status === 1 ? '下架' : '上架' }}</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="onPage" />

    <el-dialog v-model="visible" :title="form.id ? '编辑优惠券' : '新增优惠券'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="券名称" prop="title">
          <el-input v-model="form.title" maxlength="64" placeholder="如：川菜双人餐代金券" />
        </el-form-item>
        <el-form-item label="副标题" prop="subTitle">
          <el-input v-model="form.subTitle" maxlength="128" placeholder="如：满100减30 · 每日限量" />
        </el-form-item>
        <el-form-item label="使用规则" prop="rules">
          <el-input v-model="form.rules" maxlength="255" placeholder="如：每单限用一张" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="券类型" prop="type">
              <el-radio-group v-model="form.type">
                <el-radio :value="1">代金券</el-radio>
                <el-radio :value="2">秒杀券</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发放库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="购买价(元)" prop="payYuan">
              <el-input-number v-model="form.payYuan" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="券面额(元)" prop="actualYuan">
              <el-input-number v-model="form.actualYuan" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <template v-if="form.type === 2">
          <el-alert type="info" :closable="false" show-icon class="mb"
                    title="秒杀券需设置时间窗：保存后会同步刷新 Redis 秒杀库存，立即生效" />
          <el-form-item label="开始时间" prop="beginTime">
            <el-date-picker v-model="form.beginTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                            placeholder="选择开始时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                            placeholder="选择结束时间" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { fen2yuan } from '@/utils/roles'
import bus from '@/utils/bus'

const props = defineProps({ shopId: { type: [Number, String], default: null } })
const user = useUserStore()
const isAdmin = computed(() => user.isAdmin)
const canOperate = computed(() => user.isAdmin || Number(user.shopAuditStatus) === 1)

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  id: null, title: '', subTitle: '', rules: '', type: 1, stock: 100,
  payYuan: 0, actualYuan: 10, beginTime: '', endTime: '', status: 1
})
const rules = {
  title: [{ required: true, message: '请输入券名称', trigger: 'blur' }],
  actualYuan: [{ required: true, message: '请输入券面额', trigger: 'blur' }],
  beginTime: [{
    validator: (rule, value, cb) => {
      if (form.type === 2 && !value) return cb(new Error('秒杀券必须设置开始时间'))
      cb()
    }, trigger: 'change'
  }],
  endTime: [{
    validator: (rule, value, cb) => {
      if (form.type === 2 && !value) return cb(new Error('秒杀券必须设置结束时间'))
      cb()
    }, trigger: 'change'
  }]
}

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (props.shopId) params.shopId = props.shopId
    const data = await request.get('/merchant/voucher/page', { params })
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

function openEdit(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title,
      subTitle: row.subTitle,
      rules: row.rules,
      type: row.type,
      stock: row.stock,
      payYuan: Number((row.payValue / 100).toFixed(2)),
      actualYuan: Number((row.actualValue / 100).toFixed(2)),
      beginTime: row.beginTime || '',
      endTime: row.endTime || '',
      status: row.status
    })
  } else {
    Object.assign(form, {
      id: null, title: '', subTitle: '', rules: '', type: 1, stock: 100,
      payYuan: 0, actualYuan: 10, beginTime: '', endTime: '', status: 1
    })
  }
  visible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await request.post('/merchant/voucher/save', {
      id: form.id,
      title: form.title,
      subTitle: form.subTitle,
      rules: form.rules,
      type: form.type,
      stock: form.stock,
      payValue: Math.round(Number(form.payYuan) * 100),
      actualValue: Math.round(Number(form.actualYuan) * 100),
      beginTime: form.type === 2 ? form.beginTime : null,
      endTime: form.type === 2 ? form.endTime : null,
      status: form.status,
      shopId: props.shopId || undefined
    })
    ElMessage.success('保存成功，秒杀券库存已同步到 Redis')
    visible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  await request.post('/merchant/voucher/save', {
    ...row,
    status: row.status === 1 ? 0 : 1,
    shopId: props.shopId || undefined
  })
  ElMessage.success(row.status === 1 ? '已下架' : '已上架')
  reload()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除券「${row.title}」吗？`, '确认', { type: 'warning' })
  await request.delete(`/merchant/voucher/${row.id}`)
  ElMessage.success('删除成功')
  reload()
}

const off = bus.on('MERCHANT_SHOP_CHANGED', () => { page.value = 1; reload() })
watch(() => props.shopId, () => { page.value = 1; reload() })

onMounted(reload)
onBeforeUnmount(off)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.toolbar .right { display: flex; gap: 8px; }
.toolbar .left { flex: 1; }
.muted { color: #b0b8c4; font-size: 12px; }
.mb { margin-bottom: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
