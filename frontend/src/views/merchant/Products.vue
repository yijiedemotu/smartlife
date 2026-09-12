<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="left">
        <el-alert v-if="!isAdmin && !canOperate" type="warning" show-icon :closable="false"
                  title="店铺尚未通过审核，暂不能新增或修改商品（可先查看）" />
      </div>
      <div class="right">
        <el-button type="primary" :disabled="!isAdmin && !canOperate" @click="openEdit()">新增商品</el-button>
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" empty-text="暂无商品">
      <el-table-column label="图片" width="90">
        <template #default="{ row }">
          <el-image v-if="row.images" :src="firstImage(row.images)" fit="cover" class="thumb"
                    :preview-src-list="[firstImage(row.images)]" preview-teleported />
          <span v-else class="no-img">无图</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="单价" width="110">
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

    <el-dialog v-model="visible" :title="form.id ? '编辑商品' : '新增商品'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="如：麻婆豆腐" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" maxlength="255" placeholder="一句话卖点" />
        </el-form-item>
        <el-form-item label="单价(元)" prop="priceYuan">
          <el-input-number v-model="form.priceYuan" :min="0.01" :precision="2" :step="1" style="width: 200px" />
          <span class="hint">后端以"分"存储，提交时自动换算</span>
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :step="10" style="width: 200px" />
        </el-form-item>
        <el-form-item label="图片URL" prop="images">
          <el-input v-model="form.images" placeholder="https://... 多个用英文逗号分隔" />
        </el-form-item>
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
  id: null, name: '', description: '', priceYuan: 1, stock: 100, images: '', status: 1
})
const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  priceYuan: [{ required: true, message: '请输入单价', trigger: 'blur' }]
}

function firstImage(images) {
  return String(images || '').split(',')[0].trim()
}

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (props.shopId) params.shopId = props.shopId
    const data = await request.get('/merchant/product/list', { params })
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
      name: row.name,
      description: row.description,
      priceYuan: Number((row.price / 100).toFixed(2)),
      stock: row.stock,
      images: row.images,
      status: row.status
    })
  } else {
    Object.assign(form, { id: null, name: '', description: '', priceYuan: 1, stock: 100, images: '', status: 1 })
  }
  visible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await request.post('/merchant/product/save', {
      id: form.id,
      name: form.name,
      description: form.description,
      price: Math.round(Number(form.priceYuan) * 100),
      stock: form.stock,
      images: form.images,
      status: form.status,
      shopId: props.shopId || undefined
    })
    ElMessage.success('保存成功')
    visible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  await request.post('/merchant/product/save', {
    id: row.id,
    name: row.name,
    price: row.price,
    stock: row.stock,
    images: row.images,
    description: row.description,
    status: row.status === 1 ? 0 : 1,
    shopId: props.shopId || undefined
  })
  ElMessage.success(row.status === 1 ? '已下架' : '已上架')
  reload()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除商品「${row.name}」吗？`, '确认', { type: 'warning' })
  await request.delete(`/merchant/product/${row.id}`)
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
.thumb { width: 52px; height: 52px; border-radius: 6px; }
.no-img { color: #b0b8c4; font-size: 12px; }
.hint { margin-left: 10px; font-size: 12px; color: #98a2b3; }
.pager { margin-top: 14px; justify-content: flex-end; }
.left { flex: 1; }
</style>
