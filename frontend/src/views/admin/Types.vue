<template>
  <div class="page-card">
    <div class="toolbar">
      <el-alert type="info" show-icon :closable="false" class="tip"
                title="店铺类型是平台级字典：商家入驻时选择、用户端按类目筛选都依赖它。修改后前端缓存会立即失效。" />
      <el-button type="primary" @click="openEdit()">新增类目</el-button>
    </div>

    <el-table :data="records" v-loading="loading" empty-text="暂无类目">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="icon" label="图标" width="90" />
      <el-table-column prop="name" label="类目名称" min-width="160" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑类目' : '新增类目'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="类目名称">
          <el-input v-model="form.name" maxlength="32" placeholder="如：咖啡甜品" />
        </el-form-item>
        <el-form-item label="emoji 图标">
          <el-input v-model="form.icon" maxlength="8" placeholder="如：☕" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :step="1" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const records = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, name: '', icon: '', sort: 0 })

async function reload() {
  loading.value = true
  try {
    records.value = await request.get('/admin/type/list') || []
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  Object.assign(form, row
    ? { id: row.id, name: row.name, icon: row.icon, sort: row.sort }
    : { id: null, name: '', icon: '', sort: (records.value.length + 1) })
  visible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写类目名称')
    return
  }
  saving.value = true
  try {
    await request.post('/admin/type/save', { ...form })
    ElMessage.success('保存成功')
    visible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(
    `确定删除类目「${row.name}」吗？已使用该类目的店铺不会被删除，但会失去类目归属。`,
    '确认', { type: 'warning' }
  )
  await request.delete(`/admin/type/${row.id}`)
  ElMessage.success('已删除')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.tip { flex: 1; }
</style>
