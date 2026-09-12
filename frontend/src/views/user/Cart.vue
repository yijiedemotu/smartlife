<template>
  <div>
    <div class="page-card" v-if="!groups.length">
      <el-empty description="购物车是空的，快去挑点好吃的吧">
        <el-button type="warning" @click="$router.push('/user/home')">去逛逛</el-button>
      </el-empty>
    </div>

    <div v-for="g in groups" :key="g.shopId" class="page-card">
      <div class="shop-title">🏪 {{ g.shopName }} <span class="area">{{ g.shopArea }}</span></div>
      <div v-for="item in g.items" :key="item.productId" class="cart-row">
        <img :src="item.productImage || ''" class="cart-img" @error="onImgError" />
        <div class="cart-info">
          <div class="cart-name">{{ item.productName }}</div>
          <div class="cart-price">¥{{ (item.price / 100).toFixed(2) }}</div>
        </div>
        <el-input-number v-model="item.count" :min="1" :max="99" size="small" @change="(v) => update(item, v)" />
        <el-button text type="danger" @click="remove(item)">移除</el-button>
      </div>
      <div class="shop-footer">
        <span>小计：<b class="amount">¥{{ (groupAmount(g) / 100).toFixed(2) }}</b></span>
        <el-button type="warning" @click="checkout(g)">去结算</el-button>
      </div>
    </div>

    <!-- 结算弹窗 -->
    <el-dialog v-model="payVisible" title="确认订单" width="440px">
      <template v-if="currentOrder">
        <p>店铺：{{ currentOrder.shopName }}</p>
        <p>订单号：{{ currentOrder.number }}</p>
        <p>收货地址：<el-input v-model="checkoutAddress" size="small" /></p>
        <p v-if="checkoutRemark">备注：{{ checkoutRemark }}</p>
        <p class="total">应付金额：<b class="amount">¥{{ (currentOrder.amount / 100).toFixed(2) }}</b></p>
      </template>
      <template #footer>
        <el-button @click="payVisible = false">稍后支付</el-button>
        <el-button type="warning" :loading="paying" @click="doPay">模拟支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import bus from '@/utils/bus'

const router = useRouter()
const items = ref([])
const payVisible = ref(false)
const currentOrder = ref(null)
const paying = ref(false)
const checkoutAddress = ref('')
const checkoutRemark = ref('')

function onImgError(e) {
  e.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="100%" height="100%" fill="%23ffe8db"/><text x="50%" y="50%" fill="%23ff6b35" text-anchor="middle" font-size="40">🍽️</text></svg>'
}

const groups = computed(() => {
  const map = new Map()
  items.value.forEach((it) => {
    if (!map.has(it.shopId)) {
      map.set(it.shopId, { shopId: it.shopId, shopName: it.shopName, shopArea: it.shopArea, items: [] })
    }
    map.get(it.shopId).items.push(it)
  })
  return [...map.values()]
})

function groupAmount(g) {
  return g.items.reduce((s, it) => s + (it.price || 0) * it.count, 0)
}

async function load() {
  items.value = (await request.get('/cart')) || []
}

async function update(item, count) {
  await request.put('/cart', { productId: item.productId, count })
  bus.emit('CART_CHANGED', {})
}

async function remove(item) {
  await request.delete(`/cart/${item.productId}`)
  bus.toast('已移除')
  bus.emit('CART_CHANGED', {})
  load()
}

async function checkout(g) {
  const me = await request.get('/user/me')
  checkoutAddress.value = me?.address || ''
  try {
    const order = await request.post('/order/create', {
      shopId: g.shopId,
      address: checkoutAddress.value,
      remark: checkoutRemark.value,
      items: g.items.map((it) => ({ productId: it.productId, count: it.count }))
    })
    currentOrder.value = order
    payVisible.value = true
    bus.emit('CART_CHANGED', {})
    load()
  } catch (e) {
    load() // 库存变化时刷新
  }
}

async function doPay() {
  paying.value = true
  try {
    const order = await request.post(`/order/${currentOrder.value.id}/pay`, null, { silent: true })
    bus.toast('支付成功！商家端将收到实时新订单提醒')
    currentOrder.value.status = order.status
    payVisible.value = false
    bus.emit('CART_CHANGED', {})
  } finally {
    paying.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.shop-title { font-size: 16px; font-weight: 700; margin-bottom: 6px; }
.area { color: #999; font-size: 13px; font-weight: 400; }
.cart-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px dashed #eee; }
.cart-img { width: 60px; height: 60px; object-fit: cover; border-radius: 6px; }
.cart-info { flex: 1; }
.cart-name { font-weight: 600; }
.cart-price { color: #ff6b35; margin-top: 2px; }
.shop-footer { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 10px; }
.amount { color: #ff4d4f; font-size: 18px; }
.total { font-size: 15px; }
</style>
