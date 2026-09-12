<template>
  <div class="profile-grid">
    <div class="page-card">
      <h3>👤 我的资料</h3>
      <el-form label-width="80px" v-if="me">
        <el-form-item label="手机号">
          <span>{{ me.phone }}</span>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :label="0">保密</el-radio>
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="外卖收货/约饭活动地址" />
        </el-form-item>
        <el-form-item label="微信号">
          <el-input v-model="form.wechat" placeholder="方便搭子联系你（展示在个人页/搭子卡片）" />
        </el-form-item>
        <el-form-item label="兴趣标签">
          <el-select v-model="form.tags" multiple filterable allow-create default-first-option
                     placeholder="输入后回车创建，如：川菜/夜跑/剧本杀" style="width: 100%">
            <el-option v-for="t in hotTags" :key="t.tag" :label="t.tag" :value="t.tag" />
          </el-select>
          <div class="tip">标签是饭搭子匹配的数据基础（编辑距离算法）</div>
        </el-form-item>
        <el-button type="warning" :loading="saving" @click="save">保存</el-button>
      </el-form>
    </div>

    <div class="page-card">
      <h3>📅 每日签到 <span class="sub">BitMap 实现 · 连续 {{ streak }} 天</span></h3>
      <div class="sign-panel">
        <div class="sign-info">
          <p class="month">{{ monthLabel }}</p>
          <p>本月已签到 <b>{{ signCount }}</b> 天</p>
          <el-button v-if="!signedToday" type="success" round size="large" @click="doSign" :loading="signing">
            ✅ 立即签到
          </el-button>
          <el-tag v-else type="success" size="large">今日已签到</el-tag>
        </div>
        <div class="calendar">
          <div v-for="d in calendar" :key="d.day" class="cell" :class="{
            signed: d.signed, today: d.today, future: d.future }">
            <span v-if="d.signed" class="stamp">✓</span>{{ d.day }}
          </div>
        </div>
      </div>
      <p class="tip">原理：sign:{userId}:{yyyyMM} 的 BitMap，offset=当月第 N 天；统计用 BITCOUNT，连续天数向前遍历即可。一年仅约 46 字节/用户。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/utils/request'
import bus from '@/utils/bus'
import { useUserStore } from '@/store/user'
import { GENDERS } from '@/utils/format'

const me = ref(null)
const form = ref({ nickname: '', gender: 0, address: '', wechat: '', tags: [] })
const hotTags = ref([])
const saving = ref(false)
const signing = ref(false)
const signedToday = ref(false)
const signCount = ref(0)
const streak = ref(0)
const signedDays = ref([])
const month = ref({})

const daysInMonth = computed(() => {
  const now = new Date()
  const y = month.value?.year ?? now.getFullYear()
  const m = month.value?.month ?? now.getMonth() + 1
  return new Date(y, m, 0).getDate()
})

const monthLabel = computed(() => `${month.value?.year ?? new Date().getFullYear()} 年 ${(month.value?.month ?? new Date().getMonth() + 1)} 月`)

const calendar = computed(() => {
  const now = new Date()
  const today = now.getDate()
  const arr = []
  for (let d = 1; d <= daysInMonth.value; d++) {
    arr.push({
      day: d,
      signed: signedDays.value.includes(d),
      today: d === today,
      future: d > today
    })
  }
  return arr
})

async function loadMe() {
  me.value = await request.get('/user/me')
  form.value = {
    nickname: me.value.nickname || '',
    gender: me.value.gender ?? 0,
    address: me.value.address || '',
    wechat: me.value.wechat || '',
    tags: me.value.tags || []
  }
  hotTags.value = (await request.get('/mate/tags/hot')) || []
}

async function save() {
  saving.value = true
  try {
    await request.put('/user/me', null, {
      params: {
        nickname: form.value.nickname,
        gender: form.value.gender,
        address: form.value.address,
        wechat: form.value.wechat,
        tags: JSON.stringify(form.value.tags)
      }
    })
    const store = useUserStore()
    store.setMe({ nickname: form.value.nickname })
    bus.toast('资料已保存')
    loadMe()
  } finally {
    saving.value = false
  }
}

async function loadSign() {
  const status = await request.get('/sign/status')
  signedToday.value = status.signedToday
  signCount.value = status.monthSignCount
  streak.value = status.streak
  const now = new Date()
  month.value = { year: now.getFullYear(), month: now.getMonth() + 1 }
  const data = await request.get(`/sign/month/${String(month.value.year)}${String(month.value.month).padStart(2, '0')}`)
  signedDays.value = data?.signedDays || []
}

async function doSign() {
  signing.value = true
  try {
    await request.post('/sign/today', null, { silent: true })
    bus.toast('签到成功 🎉')
    loadSign()
  } finally {
    signing.value = false
  }
}

onMounted(() => {
  loadMe()
  loadSign()
})
</script>

<style scoped>
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 900px) { .profile-grid { grid-template-columns: 1fr; } }
.sub { color: #999; font-size: 13px; font-weight: 400; }
.tip { color: #999; font-size: 12px; margin-top: 6px; line-height: 1.6; }
.sign-panel { display: flex; gap: 24px; align-items: flex-start; }
.sign-info { min-width: 150px; }
.month { font-size: 18px; font-weight: 700; margin: 4px 0; }
.calendar { flex: 1; display: grid; grid-template-columns: repeat(7, 1fr); gap: 6px; }
.cell { position: relative; aspect-ratio: 1; display: flex; align-items: center; justify-content: center;
  border-radius: 8px; background: #f7f8fa; color: #666; font-size: 14px; }
.cell.signed { background: #67c23a; color: #fff; font-weight: 700; }
.cell.today { outline: 2px solid #ff6b35; }
.cell.future { opacity: 0.4; }
.stamp { position: absolute; top: -2px; right: 2px; font-size: 12px; }
</style>
