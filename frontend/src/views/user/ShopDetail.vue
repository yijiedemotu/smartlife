<template>
  <div v-if="shop">
    <el-card class="page-card">
      <div class="head">
        <img :src="shop.images || ''" class="cover" @error="onImgError" />
        <div class="info">
          <h2>{{ shop.name }}</h2>
          <div class="tags">
            <el-tag>{{ typeName }}</el-tag>
            <el-tag type="warning">⭐ {{ shop.score }}</el-tag>
            <el-tag type="danger">🔥 {{ shop.popularity }}</el-tag>
          </div>
          <p class="addr">📍 {{ shop.address }}（{{ shop.area }}）</p>
          <p class="coords" v-if="shop.lon">经纬度：{{ shop.lon }}, {{ shop.lat }}（Redis GEO 定位）</p>
        </div>
      </div>
    </el-card>

    <div class="page-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="点餐" name="menu">
          <div v-for="p in products" :key="p.id" class="product-row">
            <img :src="p.images || ''" class="product-img" @error="onImgError" />
            <div class="product-info">
              <div class="product-name">{{ p.name }}</div>
              <div class="product-desc">{{ p.description || '' }}</div>
              <div class="product-stock" v-if="p.stock <= 10">仅剩 {{ p.stock }} 份</div>
            </div>
            <div class="product-right">
              <div class="price">¥{{ (p.price / 100).toFixed(2) }}</div>
              <el-input-number v-model="quantities[p.id]" :min="1" :max="Math.min(99, p.stock || 99)" size="small" />
              <el-button type="warning" size="small" :disabled="p.stock <= 0" @click="addCart(p)">
                {{ p.stock > 0 ? '加入购物车' : '已售罄' }}
              </el-button>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="`优惠券(${vouchers.length})`" name="voucher">
          <el-empty v-if="!vouchers.length" description="暂无可领优惠券" />
          <div v-for="v in vouchers" :key="v.id" class="voucher-row">
            <div class="voucher-left">
              <div class="voucher-value">¥{{ (v.actualValue / 100).toFixed(0) }}<span> 优惠券</span></div>
              <div class="voucher-title">{{ v.title }}</div>
              <div class="voucher-sub">{{ v.subTitle }}</div>
              <div class="voucher-rules">{{ v.rules }}</div>
            </div>
            <div class="voucher-right">
              <div v-if="v.type === 2" class="remain">剩余 {{ v.stockLeft }}</div>
              <el-button v-if="v.type === 2" type="danger" round :disabled="v.stockLeft <= 0"
                         @click="goSeckill">去秒杀</el-button>
              <el-button v-else type="warning" round plain @click="grab(v)">立即领取</el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import bus from '@/utils/bus'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const shopId = Number(route.params.id)
const shop = ref(null)
const typeName = ref('')
const products = ref([])
const vouchers = ref([])
const activeTab = ref('menu')
const quantities = reactive({})

function onImgError(e) {
  e.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="600" height="300"><rect width="100%" height="100%" fill="%23ffe8db"/><text x="50%" y="50%" fill="%23ff6b35" text-anchor="middle" font-size="40">🍜</text></svg>'
}

async function loadShop() {
  shop.value = await request.get(`/shop/${shopId}`)
  const types = await request.get('/shop/type/list')
  const t = types.find((x) => x.id === shop.value.typeId)
  typeName.value = t ? `${t.icon} ${t.name}` : '其他'
}

async function loadProducts() {
  products.value = (await request.get(`/shop/${shopId}/products`)) || []
  products.value.forEach((p) => {
    quantities[p.id] = 1
  })
}

async function loadVouchers() {
  vouchers.value = (await request.get(`/voucher/shop/${shopId}`)) || []
}

async function addCart(p) {
  const count = quantities[p.id] || 1
  await request.post('/cart', { productId: p.id, count })
  bus.toast(`「${p.name}」已加入购物车`)
  bus.emit('CART_CHANGED', {})
}

async function grab(v) {
  await request.post(`/voucher/${v.id}/grab`)
  bus.toast('领取成功，已放入券包')
  loadVouchers()
}

function goSeckill() {
  router.push({ path: '/seckill', query: { shopId } })
}

onMounted(() => {
  loadShop()
  loadProducts()
  loadVouchers()
})
</script>

<style scoped>
.head { display: flex; gap: 20px; }
.cover { width: 320px; height: 180px; object-fit: cover; border-radius: 8px; }
.info h2 { margin: 4px 0 10px; }
.tags { display: flex; gap: 8px; }
.addr { color: #666; }
.coords { color: #aaa; font-size: 12px; }
.product-row { display: flex; align-items: center; gap: 14px; padding: 12px 0; border-bottom: 1px dashed #eee; }
.product-img { width: 72px; height: 72px; object-fit: cover; border-radius: 6px; }
.product-info { flex: 1; }
.product-name { font-size: 15px; font-weight: 600; }
.product-desc { color: #999; font-size: 12px; margin: 4px 0; }
.product-stock { color: #ff4d4f; font-size: 12px; }
.product-right { display: flex; align-items: center; gap: 10px; }
.price { color: #ff6b35; font-weight: 700; width: 70px; }
.voucher-row { display: flex; justify-content: space-between; align-items: center; border: 1px solid #ffe0b2; background: #fff8ef; border-radius: 8px; padding: 14px; margin-bottom: 10px; }
.voucher-value { color: #ff6b35; font-size: 24px; font-weight: 800; }
.voucher-value span { font-size: 13px; }
.voucher-title { font-weight: 600; margin: 2px 0; }
.voucher-sub, .voucher-rules { color: #999; font-size: 12px; }
.remain { color: #ff4d4f; font-size: 12px; margin-bottom: 4px; text-align: center; }
</style>
