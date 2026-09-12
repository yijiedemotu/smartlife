<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">🍜 智联生活</div>
        <div class="sub">本地生活与社交聚合平台 · 用户端 / 商家端 / 管理端</div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码"
                    @keyup.enter="onSubmit" />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
          登录（自动进入对应端）
        </el-button>
      </el-form>

      <div class="tips">
        <div class="tips-title">演示账号（点击可直接填充）</div>
        <div class="tip-list">
          <div v-for="acc in accounts" :key="acc.phone" class="tip-item" @click="fill(acc)">
            <el-tag :type="acc.tag" size="small" effect="dark">{{ acc.role }}</el-tag>
            <span class="phone">{{ acc.phone }}</span>
            <span class="pwd">{{ acc.password }}</span>
            <span class="desc">{{ acc.desc }}</span>
          </div>
        </div>
      </div>

      <div class="footer">
        <span>还没有账号？</span>
        <el-link type="primary" @click="goRegister">用户注册</el-link>
        <el-divider direction="vertical" />
        <el-link type="success" @click="$router.push('/register/merchant')">我要开店（商家入驻）</el-link>
      </div>
    </div>

    <!-- 用户注册 -->
    <el-dialog v-model="registerVisible" title="用户注册" width="420px">
      <el-form ref="regFormRef" :model="regForm" :rules="regRules" label-width="80px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="regForm.phone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="regForm.password" type="password" show-password placeholder="6~20 位" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="regForm.nickname" maxlength="16" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="regLoading" @click="doRegister">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { homePathOf } from '@/utils/roles'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })
const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const accounts = [
  { role: '用户端', tag: 'primary', phone: '13900000001', password: '123456', desc: '浏览下单/秒杀/找搭子' },
  { role: '商家端', tag: 'success', phone: '13700000001', password: 'merchant123', desc: '蜀香居川菜馆(已过审)' },
  { role: '商家端', tag: 'warning', phone: '13700000002', password: 'merchant123', desc: '待审核入驻' },
  { role: '管理端', tag: 'danger', phone: '13800000000', password: 'admin123', desc: '平台治理与审核' }
]

function fill(acc) {
  form.phone = acc.phone
  form.password = acc.password
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await request.post('/auth/login', { phone: form.phone, password: form.password })
    user.setLogin(data)
    ElMessage.success(`登录成功，欢迎 ${data.nickname || ''}`)
    // 后端下发 homePath 决定落地端；带 redirect 时优先回跳
    const redirect = route.query.redirect
    const target = redirect && String(redirect).startsWith('/') ? String(redirect) : null
    router.replace(target || data.homePath || homePathOf(data.role))
  } finally {
    loading.value = false
  }
}

// ---------------- 用户注册 ----------------
const registerVisible = ref(false)
const regLoading = ref(false)
const regFormRef = ref()
const regForm = reactive({ phone: '', password: '', nickname: '' })
const regRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6~20 位', trigger: 'blur' }
  ]
}

function goRegister() {
  registerVisible.value = true
}

async function doRegister() {
  await regFormRef.value.validate()
  regLoading.value = true
  try {
    await request.post('/auth/register', { ...regForm })
    ElMessage.success('注册成功，请登录')
    registerVisible.value = false
    form.phone = regForm.phone
    form.password = regForm.password
  } finally {
    regLoading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff8a3d 0%, #ff6b35 45%, #d8451f 100%);
  padding: 24px;
}
.login-card {
  width: 470px;
  background: #fff;
  border-radius: 14px;
  padding: 28px 30px 22px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
}
.brand { text-align: center; margin-bottom: 18px; }
.logo { font-size: 26px; font-weight: 800; color: #ff6b35; }
.sub { font-size: 12px; color: #8a94a6; margin-top: 6px; }
.submit { width: 100%; margin-top: 4px; }
.tips { margin-top: 18px; border-top: 1px dashed #e6e8eb; padding-top: 14px; }
.tips-title { font-size: 12px; color: #8a94a6; margin-bottom: 8px; }
.tip-list { display: flex; flex-direction: column; gap: 8px; }
.tip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #4a5568;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  background: #f7f9fc;
}
.tip-item:hover { background: #fff3ec; }
.tip-item .phone { font-family: Consolas, monospace; color: #1f2937; }
.tip-item .pwd { font-family: Consolas, monospace; color: #d8451f; }
.tip-item .desc { color: #98a2b3; margin-left: auto; }
.footer { margin-top: 14px; text-align: center; font-size: 13px; color: #6b7280; }
</style>
