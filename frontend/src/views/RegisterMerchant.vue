<template>
  <div class="reg-page">
    <div class="reg-card">
      <div class="head">
        <div class="logo">🏪 我要开店 · 商家入驻</div>
        <div class="sub">提交后由平台管理员审核，审核通过即可上架商品、接单营业</div>
      </div>

      <el-steps :active="1" simple class="steps">
        <el-step title="注册并提交资料" icon="Edit" />
        <el-step title="平台审核" icon="Search" />
        <el-step title="开店营业" icon="Shop" />
      </el-steps>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-divider content-position="left">账号信息</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" maxlength="11" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="6~20 位" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商家昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="16" placeholder="如：蜀香居王老板" />
        </el-form-item>

        <el-divider content-position="left">店铺资料</el-divider>
        <el-form-item label="店铺名称" prop="shopName">
          <el-input v-model="form.shopName" maxlength="64" placeholder="如：蜀香居川菜馆（望京店）" />
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
              <el-input v-model="form.contactName" maxlength="32" placeholder="法人/店长姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" maxlength="20" placeholder="店铺联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商圈" prop="area">
              <el-input v-model="form.area" maxlength="32" placeholder="如：望京 / 三里屯" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" maxlength="255" placeholder="省市区 + 街道门牌" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="经度" prop="lon">
              <el-input-number v-model="form.lon" :precision="6" :step="0.001" :controls="false"
                               placeholder="116.486" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="纬度" prop="lat">
              <el-input-number v-model="form.lat" :precision="6" :step="0.001" :controls="false"
                               placeholder="39.996" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="营业执照号" prop="licenseNo">
              <el-input v-model="form.licenseNo" maxlength="64" placeholder="统一社会信用代码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="证照图片" prop="licenseImg">
          <el-input v-model="form.licenseImg" placeholder="图片 URL（演示环境可直接填任意图片地址）" />
        </el-form-item>
        <el-form-item label="店铺简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="一句话介绍你的店铺特色" />
        </el-form-item>

        <div class="loc-tip">
          <el-button link type="primary" @click="useBeijingDemo">填入示例坐标（北京望京）</el-button>
          <span class="hint">经纬度用于用户端"附近店铺"（Redis GEO）检索</span>
        </div>

        <div class="actions">
          <el-button @click="$router.push('/login')">返回登录</el-button>
          <el-button type="primary" :loading="loading" @click="submit">提交入驻申请</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const types = ref([])

const form = reactive({
  phone: '', password: '', nickname: '',
  shopName: '', typeId: null, contactName: '', contactPhone: '',
  area: '', address: '', lon: null, lat: null,
  licenseNo: '', licenseImg: '', description: ''
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6~20 位', trigger: 'blur' }
  ],
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  typeId: [{ required: true, message: '请选择经营类目', trigger: 'change' }],
  address: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

function useBeijingDemo() {
  form.lon = 116.486
  form.lat = 39.996
  if (!form.area) form.area = '望京'
  if (!form.address) form.address = '北京市朝阳区望京SOHO T2座'
}

async function loadTypes() {
  try {
    types.value = await request.get('/shop/type/list')
  } catch {
    types.value = []
  }
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await request.post('/auth/register/merchant', { ...form })
    await ElMessageBox.alert(
      '入驻申请已提交！请使用刚注册的手机号登录商家端，等待平台管理员审核通过后即可上架商品并接单。',
      '提交成功',
      { confirmButtonText: '去登录', type: 'success' }
    )
    router.push('/login')
  } finally {
    loading.value = false
  }
}

onMounted(loadTypes)
</script>

<style scoped>
.reg-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #2f9e6f 0%, #1f7a54 100%);
  padding: 32px 16px;
  display: flex;
  justify-content: center;
}
.reg-card {
  width: 860px;
  max-width: 100%;
  background: #fff;
  border-radius: 14px;
  padding: 24px 28px 28px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
}
.head { margin-bottom: 12px; }
.logo { font-size: 22px; font-weight: 800; color: #1f7a54; }
.sub { font-size: 12px; color: #8a94a6; margin-top: 6px; }
.steps { margin: 12px 0 18px; }
.loc-tip { display: flex; align-items: center; gap: 10px; margin: 4px 0 16px 100px; }
.loc-tip .hint { font-size: 12px; color: #98a2b3; }
.actions { display: flex; justify-content: flex-end; gap: 10px; }
</style>
