<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
    <el-form-item label="店铺名称" prop="shopName">
      <el-input v-model="form.shopName" maxlength="64" placeholder="如：蜀香居川菜馆（望京二店）" />
    </el-form-item>
    <el-row :gutter="12">
      <el-col :span="12">
        <el-form-item label="经营类目" prop="typeId">
          <el-select v-model="form.typeId" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in types" :key="t.id" :label="`${t.icon || ''} ${t.name}`" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" maxlength="32" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="12">
      <el-col :span="12">
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="20" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="商圈" prop="area">
          <el-input v-model="form.area" maxlength="32" placeholder="如：望京" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item label="详细地址" prop="address">
      <el-input v-model="form.address" maxlength="255" />
    </el-form-item>
    <el-row :gutter="12">
      <el-col :span="12">
        <el-form-item label="经度" prop="lon">
          <el-input-number v-model="form.lon" :precision="6" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="纬度" prop="lat">
          <el-input-number v-model="form.lat" :precision="6" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item label="营业执照号" prop="licenseNo">
      <el-input v-model="form.licenseNo" maxlength="64" placeholder="统一社会信用代码" />
    </el-form-item>
    <el-form-item label="证照图片" prop="licenseImg">
      <el-input v-model="form.licenseImg" placeholder="图片 URL" />
    </el-form-item>
    <el-form-item label="店铺简介" prop="description">
      <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
    </el-form-item>
    <div class="actions">
      <el-button type="primary" :loading="saving" @click="submit">提交申请</el-button>
    </div>
  </el-form>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  types: { type: Array, default: () => [] },
  initial: { type: Object, default: null }
})
const emit = defineEmits(['submitted'])

const formRef = ref()
const saving = ref(false)
const form = reactive({
  shopName: '', typeId: null, contactName: '', contactPhone: '',
  area: '', address: '', lon: null, lat: null,
  licenseNo: '', licenseImg: '', description: ''
})

const rules = {
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  typeId: [{ required: true, message: '请选择经营类目', trigger: 'change' }],
  address: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

/** 变更申请时带入现有店铺资料 */
watch(() => props.initial, (shop) => {
  if (!shop) return
  Object.assign(form, {
    shopName: shop.name || '',
    typeId: shop.typeId ?? null,
    contactName: '',
    contactPhone: shop.phone || '',
    area: shop.area || '',
    address: shop.address || '',
    lon: shop.lon ?? null,
    lat: shop.lat ?? null,
    licenseNo: '',
    licenseImg: '',
    description: shop.description || ''
  })
}, { immediate: true })

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    await request.post('/merchant/apply/submit', { ...form })
    ElMessage.success('申请已提交，请等待平台审核')
    emit('submitted')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.actions { display: flex; justify-content: flex-end; }
</style>
