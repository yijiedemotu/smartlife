<template>
  <div class="page-card">
    <h3>🎫 我的券包 <span class="sub">秒杀成功的券也会实时出现在这里</span></h3>
    <el-empty v-if="!vouchers.length" description="还没有优惠券，去秒杀页或店铺看看吧">
      <el-button type="danger" round @click="$router.push('/user/seckill')">去秒杀</el-button>
    </el-empty>
    <div v-else class="voucher-list">
      <div v-for="v in vouchers" :key="v.id" class="voucher" :class="{ used: v.status === 2 }">
        <div class="val">
          <span class="num">¥{{ (v.actualValue / 100).toFixed(0) }}</span>
          <span class="shop">{{ v.shopName }}</span>
        </div>
        <div class="mid">
          <div class="title">{{ v.voucherTitle }}</div>
          <div class="time">领取于 {{ v.createTime }}</div>
          <div class="time" v-if="v.useTime">核销于 {{ v.useTime }}</div>
        </div>
        <div class="ops">
          <el-tag v-if="v.status === 2" type="info">已使用</el-tag>
          <template v-else>
            <el-tag type="success">可用</el-tag>
            <el-button size="small" type="primary" plain @click="use(v)">到店核销</el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import request from '@/utils/request'
import bus from '@/utils/bus'

const vouchers = ref([])

async function load() {
  vouchers.value = (await request.get('/voucher/order/my', { params: { page: 1, size: 50 } })) || []
}

async function use(v) {
  await request.post(`/voucher/order/${v.id}/use`, null, { silent: true })
  bus.toast('核销成功')
  load()
}

const offResult = bus.on('SECKILL_RESULT', () => load())

onMounted(load)
onBeforeUnmount(offResult)
</script>

<style scoped>
.sub { color: #999; font-size: 13px; font-weight: 400; margin-left: 8px; }
.voucher { display: flex; align-items: center; border-radius: 10px; overflow: hidden; margin-bottom: 14px;
  background: linear-gradient(135deg, #fff7f0, #fff); border: 1px solid #ffe3cd; }
.used { opacity: 0.55; filter: grayscale(0.6); }
.val { background: #ff6b35; color: #fff; width: 130px; padding: 18px 12px; text-align: center; position: relative; }
.val::after { content: ''; position: absolute; right: -8px; top: 0; bottom: 0; width: 16px;
  background: radial-gradient(circle at 0 50%, transparent 6px, #ff6b35 7px); background-size: 100% 16px; }
.num { font-size: 26px; font-weight: 800; display: block; }
.shop { font-size: 12px; }
.mid { flex: 1; padding: 12px 18px; }
.title { font-weight: 700; }
.time { color: #999; font-size: 12px; margin-top: 2px; }
.ops { display: flex; flex-direction: column; gap: 8px; align-items: center; padding-right: 16px; }
</style>
