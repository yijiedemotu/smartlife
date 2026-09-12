<template>
  <div>
    <!-- 顶部：搜索 + 定位 -->
    <div class="page-card toolbar">
      <el-input v-model="keyword" placeholder="搜索店铺 / 商圈，如：望京、川菜" clearable style="width: 300px"
                @keyup.enter="load(1)" @clear="load(1)">
        <template #append><el-button @click="load(1)">搜索</el-button></template>
      </el-input>
      <el-button :type="mode === 'nearby' ? 'warning' : 'default'" @click="toggleNearby">
        <el-icon><Location /></el-icon>&nbsp;{{ mode === 'nearby' ? '附近店铺' : '开启附近' }}
      </el-button>
      <el-button type="danger" plain @click="$router.push('/seckill')">🔥 限时秒杀</el-button>
    </div>

    <!-- 分类 -->
    <div class="page-card">
      <el-radio-group v-model="typeId" @change="load(1)">
        <el-radio-button :label="null">全部</el-radio-button>
        <el-radio-button v-for="t in types" :key="t.id" :label="t.id">{{ t.icon }} {{ t.name }}</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 列表 / 附近 -->
    <div v-if="loading" class="page-card center">加载中...</div>
    <el-empty v-else-if="!shops.length" description="暂无店铺" />
    <div v-else class="shop-grid">
      <el-card v-for="s in shops" :key="s.id" class="shop-card" shadow="hover" @click="$router.push('/shop/' + s.id)">
        <img :src="s.images || ''" class="shop-img" alt="" @error="onImgError" />
        <div class="shop-body">
          <div class="shop-name">{{ s.name }}
            <el-tag v-if="s.distanceM !== undefined" size="small" type="warning">{{ fmtDist(s.distanceM) }}</el-tag>
          </div>
          <div class="shop-meta">
            <span>⭐ {{ s.score }}</span>
            <span>🔥 {{ s.popularity }}</span>
            <span class="area">{{ s.area }}</span>
          </div>
          <div class="shop-addr">{{ s.address }}</div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { DEFAULT_COORD } from '@/utils/format'

const types = ref([])
const typeId = ref(null)
const keyword = ref('')
const mode = ref('list') // list | nearby
const shops = ref([])
const page = ref(1)
const size = ref(50)
const loading = ref(false)
let coord = { ...DEFAULT_COORD }

function onImgError(e) {
  e.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="600" height="300"><rect width="100%" height="100%" fill="%23ffe8db"/><text x="50%" y="50%" fill="%23ff6b35" text-anchor="middle" font-size="40">🍜</text></svg>'
}
function fmtDist(m) {
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`
}

async function loadTypes() {
  types.value = await request.get('/shop/type/list')
}

async function load(p = 1) {
  page.value = p
  loading.value = true
  try {
    if (mode.value === 'nearby') {
      const data = await request.get('/shop/nearby', {
        params: { lon: coord.lon, lat: coord.lat, typeId: typeId.value, radiusKm: 10 }
      })
      shops.value = (data || []).map((n) => ({ ...n.shop, distanceM: n.distanceM }))
    } else {
      shops.value = (await request.get('/shop/list', {
        params: { typeId: typeId.value, keyword: keyword.value, page: page.value, size: size.value }
      })) || []
    }
  } finally {
    loading.value = false
  }
}

function toggleNearby() {
  if (mode.value === 'nearby') {
    mode.value = 'list'
    load(1)
    return
  }
  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        coord = { lon: pos.coords.longitude, lat: pos.coords.latitude }
        mode.value = 'nearby'
        load(1)
      },
      () => {
        mode.value = 'nearby'
        load(1)
      },
      { timeout: 4000 }
    )
  } else {
    mode.value = 'nearby'
    load(1)
  }
}

onMounted(() => {
  loadTypes()
  load(1)
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; }
.center { text-align: center; color: #999; }
.shop-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.shop-card { cursor: pointer; }
.shop-img { width: 100%; height: 150px; object-fit: cover; border-radius: 6px; }
.shop-body { padding: 4px 2px; }
.shop-name { font-size: 16px; font-weight: 600; display: flex; justify-content: space-between; align-items: center; }
.shop-meta { color: #ff9500; margin: 6px 0; display: flex; gap: 12px; }
.shop-meta .area { color: #999; }
.shop-addr { color: #888; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pager { margin-top: 16px; justify-content: center; }
</style>
