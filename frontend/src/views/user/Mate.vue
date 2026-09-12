<template>
  <div>
    <div class="page-card">
      <div class="row">
        <h3>🤝 找饭搭子</h3>
        <el-button type="warning" round @click="recommend" :loading="loading">给我推荐</el-button>
      </div>
      <p class="tip">基于兴趣标签编辑距离匹配 TopN（优先队列优化内存），支持一键按标签搜索。</p>
      <el-empty v-if="!mates.length" description="完善个人标签后点击推荐，寻找与你合拍的饭搭子">
      </el-empty>
      <div class="mate-grid">
        <el-card v-for="m in mates" :key="m.user.id" class="mate-card" shadow="hover">
          <div class="avatar-row">
            <el-avatar :size="56" :src="m.user.avatar || ''">{{ (m.user.nickname || '友')[0] }}</el-avatar>
            <div class="rate">匹配度<br /><b>{{ m.matchRate }}%</b></div>
          </div>
          <div class="name">{{ m.user.nickname }}</div>
          <div class="tags">
            <el-tag v-for="t in m.user.tags || []" :key="t" size="small" type="warning" effect="plain">{{ t }}</el-tag>
          </div>
          <div class="foot">
            <span v-if="m.matchedTags">共同标签 ×{{ m.matchedTags }}</span>
            <span class="ops">
              <el-button size="small" @click="detail(m.user.id)">查看</el-button>
              <el-button size="small" type="warning" plain @click="chatTo(m.user.id)">💬 私聊</el-button>
            </span>
          </div>
        </el-card>
      </div>
    </div>

    <div class="page-card">
      <h3>🔍 按标签找人</h3>
      <div class="row">
        <el-input v-model="searchTag" placeholder="输入标签，如：川菜 / 剧本杀" style="width: 260px"
                  @keyup.enter="search" />
        <el-button @click="search">搜索</el-button>
      </div>
      <div class="hot-tags" v-if="hotTags.length">
        <span class="hot-label">热门：</span>
        <el-tag v-for="h in hotTags" :key="h.tag" size="small" class="hot-tag" @click="quickSearch(h.tag)">
          {{ h.tag }} ({{ h.count }})
        </el-tag>
      </div>
      <div v-if="searchResult.length" class="search-result">
        <div v-for="u in searchResult" :key="u.id" class="user-row" @click="detail(u.id)">
          <el-avatar :size="36" :src="u.avatar || ''">{{ (u.nickname || '')[0] }}</el-avatar>
          <span class="nick">{{ u.nickname }}</span>
          <span v-for="t in u.tags || []" :key="t" class="tag">{{ t }}</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="(selected || {}).nickname" width="360px">
      <div v-if="selected" class="profile-dialog">
        <el-avatar :size="72" :src="selected.avatar || ''">{{ (selected.nickname || '')[0] }}</el-avatar>
        <p class="addr" v-if="selected.address">📍 {{ selected.address }}</p>
        <p class="wechat" v-if="selected.wechat">📱 微信号：<b>{{ selected.wechat }}</b></p>
        <div class="tags">
          <el-tag v-for="t in selected.tags || []" :key="t" type="warning" effect="plain">{{ t }}</el-tag>
        </div>
        <p class="tip">感觉合拍？加个微信，或直接发站内私信约饭 🍚</p>
        <el-button type="warning" round class="cta" @click="chatTo(selected.id)">💬 私信 TA / 约饭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()
const mates = ref([])
const loading = ref(false)
const searchTag = ref('')
const searchResult = ref([])
const hotTags = ref([])
const dialogVisible = ref(false)
const selected = ref(null)

async function recommend() {
  loading.value = true
  try {
    mates.value = (await request.get('/mate/recommend', { params: { size: 8 } })) || []
  } catch (e) {
    // 无标签时后端返回提示
  } finally {
    loading.value = false
  }
}

async function search() {
  if (!searchTag.value.trim()) return
  searchResult.value = (await request.get('/mate/search', { params: { tag: searchTag.value.trim(), size: 20 } })) || []
}

function quickSearch(tag) {
  searchTag.value = tag
  search()
}

async function detail(id) {
  selected.value = await request.get(`/user/${id}`)
  dialogVisible.value = true
}

/** 跳转到私信会话（新搭子也可直接发起） */
function chatTo(id) {
  dialogVisible.value = false
  router.push({ path: '/user/chat', query: { to: id } })
}

async function loadHot() {
  hotTags.value = (await request.get('/mate/tags/hot')) || []
}

onMounted(() => {
  recommend()
  loadHot()
})
</script>

<style scoped>
.row { display: flex; align-items: center; justify-content: space-between; }
.tip { color: #999; font-size: 13px; }
.mate-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; margin-top: 10px; }
.mate-card { cursor: pointer; }
.avatar-row { display: flex; align-items: center; justify-content: space-between; }
.rate { text-align: center; color: #ff6b35; font-size: 12px; }
.rate b { font-size: 20px; }
.name { font-weight: 700; margin: 8px 0 6px; font-size: 16px; }
.tags { display: flex; gap: 6px; flex-wrap: wrap; min-height: 26px; }
.foot { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; color: #bbb; font-size: 12px; }
.ops { display: flex; gap: 6px; }
.wechat { color: #555; }
.cta { width: 100%; margin-top: 4px; }
.hot-tags { margin-top: 12px; }
.hot-label { color: #999; font-size: 13px; }
.hot-tag { margin: 2px 4px 2px 0; cursor: pointer; }
.search-result { margin-top: 12px; }
.user-row { display: flex; align-items: center; gap: 10px; padding: 8px 4px; border-bottom: 1px dashed #eee; cursor: pointer; }
.nick { font-weight: 600; width: 110px; }
.tag { color: #ff9500; font-size: 12px; background: #fff7e6; padding: 2px 8px; border-radius: 10px; }
</style>
