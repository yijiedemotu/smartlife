<template>
  <div class="page-card">
    <div class="head">
      <el-radio-group v-model="status" @change="load(1)">
        <el-radio-button :label="null">全部</el-radio-button>
        <el-radio-button v-for="(v, k) in ORDER_STATUS" :key="k" :label="Number(k)">{{ v.text }}</el-radio-button>
      </el-radio-group>
    </div>
    <el-empty v-if="!orders.length" description="暂无订单" />
    <el-card v-for="o in orders" :key="o.id" class="order-card" shadow="never">
      <div class="order-head">
        <span class="number">订单号：{{ o.number }}</span>
        <el-tag :type="orderStatusType(o.status)">{{ orderStatusText(o.status) }}</el-tag>
      </div>
      <div class="order-body">
        <div class="shop">{{ o.shopName }}</div>
        <div class="addr">📍 {{ o.address }}</div>
        <div class="items">
          <span v-for="it in o.items" :key="it.id">{{ it.productName }} ×{{ it.count }}</span>
        </div>
      </div>
      <div class="order-foot">
        <span class="time">下单：{{ o.createTime }}</span>
        <span class="amount">¥{{ (o.amount / 100).toFixed(2) }}</span>
        <span class="actions">
          <el-button v-if="o.status === 1" size="small" type="warning" @click="pay(o)">去支付</el-button>
          <el-button v-if="o.status === 1" size="small" plain type="danger" @click="cancel(o)">取消订单</el-button>
        </span>
      </div>
    </el-card>
    <el-pagination v-if="total > size" layout="prev, pager, next" :total="total" :page-size="size"
                   :current-page="page" @current-change="load" class="pager" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import request from '@/utils/request'
import bus from '@/utils/bus'
import { ORDER_STATUS, orderStatusText, orderStatusType } from '@/utils/format'

const orders = ref([])
const status = ref(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function load(p = 1) {
  page.value = p
  const data = await request.get('/order/mine', { params: { status: status.value, page: p, size: size.value } })
  orders.value = data?.records || []
  total.value = data?.total || 0
}

async function pay(o) {
  await request.post(`/order/${o.id}/pay`, null, { silent: true })
  bus.toast('支付成功，商家端已收到新订单提醒')
  load()
}

async function cancel(o) {
  await request.post(`/order/${o.id}/cancel`, null, { silent: true })
  bus.toast('订单已取消，库存已回补', 'warning')
  load()
}

const offOrder = bus.on('ORDER_STATUS', () => load())

onMounted(load)
onBeforeUnmount(offOrder)
</script>

<style scoped>
.head { margin-bottom: 12px; }
.order-card { margin-bottom: 12px; border-radius: 8px; }
.order-head { display: flex; justify-content: space-between; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; }
.number { color: #999; font-size: 13px; }
.order-body { padding: 10px 0; }
.shop { font-size: 16px; font-weight: 700; }
.addr { color: #666; margin: 4px 0; font-size: 13px; }
.items { color: #888; font-size: 13px; display: flex; gap: 16px; flex-wrap: wrap; }
.order-foot { display: flex; align-items: center; gap: 16px; border-top: 1px solid #f0f0f0; padding-top: 8px; }
.time { color: #bbb; font-size: 12px; flex: 1; }
.amount { color: #ff4d4f; font-weight: 800; font-size: 17px; }
.pager { margin-top: 12px; justify-content: center; }
</style>
