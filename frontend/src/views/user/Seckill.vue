<template>
  <div>
    <div class="page-card banner">
      <h2>🔥 限时秒杀</h2>
      <p>Redis Lua 原子扣减 + RabbitMQ 异步削峰下单，抢购结果 WebSocket 实时通知</p>
    </div>
    <div class="page-card">
      <div v-if="!list.length" class="center">暂无可抢的秒杀活动</div>
      <div class="seckill-grid">
        <el-card v-for="v in list" :key="v.id" class="seckill-card" shadow="hover">
          <img :src="v.shopImage || ''" class="seckill-img" @error="onImgError" @click="$router.push('/user/shop/' + v.shopId)" />
          <div class="body">
            <div class="title" @click="$router.push('/user/shop/' + v.shopId)">{{ v.title }}</div>
            <div class="shop-line">{{ v.shopName }} · {{ v.shopArea }}</div>
            <div class="sub">{{ v.subTitle }}</div>
            <div class="price-line">
              <span class="now">¥{{ (v.payValue / 100).toFixed(2) }}</span>
              <span class="value">券面 ¥{{ (v.actualValue / 100).toFixed(0) }}</span>
            </div>
            <div class="op">
              <span class="remain" :class="{ zero: v.stockLeft <= 0 }">
                {{ v.stockLeft > 0 ? `仅剩 ${v.stockLeft} 张` : '已抢光' }}
              </span>
              <el-button type="danger" round :disabled="v.stockLeft <= 0 || seckilling === v.id"
                         :loading="seckilling === v.id" @click="seckill(v)">抢购</el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import request from '@/utils/request'
import bus from '@/utils/bus'

const list = ref([])
const seckilling = ref(null)

function onImgError(e) {
  e.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="600" height="300"><rect width="100%" height="100%" fill="%23ffdbdb"/><text x="50%" y="50%" fill="%23ff4d4f" text-anchor="middle" font-size="40">⏰</text></svg>'
}

async function load() {
  list.value = (await request.get('/voucher/seckill/list')) || []
}

async function seckill(v) {
  seckilling.value = v.id
  try {
    await request.post(`/voucher/${v.id}/seckill`)
    v.stockLeft = Math.max(0, v.stockLeft - 1)
    bus.toast('抢购请求已受理，结果将通过实时通知送达', 'success')
  } catch (e) {
    v.stockLeft = 0
  } finally {
    seckilling.value = null
    load()
  }
}

const offResult = bus.on('SECKILL_RESULT', () => load())

onMounted(() => load())
onBeforeUnmount(() => offResult())
</script>

<style scoped>
.center { text-align: center; color: #999; padding: 30px 0; }
.banner p { color: #888; }
.seckill-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.seckill-card { cursor: pointer; }
.seckill-img { width: 100%; height: 150px; object-fit: cover; border-radius: 6px; }
.body { padding-top: 8px; }
.title { font-size: 16px; font-weight: 700; color: #ff4d4f; }
.shop-line { color: #666; font-size: 13px; margin: 4px 0; }
.sub { color: #999; font-size: 12px; }
.price-line { display: flex; align-items: baseline; gap: 12px; margin: 8px 0; }
.now { color: #ff4d4f; font-size: 22px; font-weight: 800; }
.value { color: #bbb; text-decoration: line-through; font-size: 13px; }
.op { display: flex; justify-content: space-between; align-items: center; }
.remain { color: #ff9500; font-size: 13px; }
.remain.zero { color: #bbb; }
</style>
