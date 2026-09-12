<template>
  <div>
    <!-- 未入驻 / 审核中提示：商家端第一屏引导 -->
    <el-alert v-if="!isAdmin && needOnboard" class="page-card" type="warning" show-icon :closable="false"
              title="店铺尚未开通经营权限">
      <template #default>
        <div class="alert-body">
          <span>{{ overview.auditText || '未入驻' }}：审核通过后才能上架商品、接单与发放优惠券。</span>
          <el-button type="primary" size="small" @click="$router.push('/merchant/shop')">
            前往「店铺与入驻」
          </el-button>
        </div>
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="8" :md="4">
        <el-card class="stat-card" shadow="never">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-foot">{{ card.foot }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :md="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>订单 / GMV 趋势</span>
              <el-radio-group v-model="days" size="small" @change="loadTrend">
                <el-radio-button :value="7">近 7 天</el-radio-button>
                <el-radio-button :value="14">近 14 天</el-radio-button>
                <el-radio-button :value="30">近 30 天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :md="8">
        <el-card shadow="never">
          <template #header><span>订单状态分布</span></template>
          <div ref="pieRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span>热销商品 Top5</span></template>
          <el-table :data="topProducts" size="small" empty-text="暂无成交数据">
            <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
            <el-table-column prop="sales" label="成交件数" width="100" />
            <el-table-column label="成交额" width="110">
              <template #default="{ row }">¥{{ fen2yuan(row.gmv) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span>券核销概览</span></template>
          <el-row :gutter="12">
            <el-col :span="8">
              <div class="mini">
                <div class="mini-value">{{ overview.total || 0 }}</div>
                <div class="mini-label">累计发放</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="mini">
                <div class="mini-value" style="color:#2f9e6f">{{ overview.used || 0 }}</div>
                <div class="mini-label">已核销</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="mini">
                <div class="mini-value" style="color:#e6a23c">{{ overview.unused || 0 }}</div>
                <div class="mini-label">待核销</div>
              </div>
            </el-col>
          </el-row>
          <el-divider />
          <div class="quick">
            <el-button size="small" @click="$router.push('/merchant/orders')">处理订单</el-button>
            <el-button size="small" @click="$router.push('/merchant/products')">上架商品</el-button>
            <el-button size="small" @click="$router.push('/merchant/vouchers')">发放优惠券</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { fen2yuan } from '@/utils/roles'
import bus from '@/utils/bus'

const props = defineProps({ shopId: { type: [Number, String], default: null } })
const user = useUserStore()
const isAdmin = computed(() => user.isAdmin)
const needOnboard = ref(false)
const overview = ref({})
const topProducts = ref([])
const days = ref(7)

const trendRef = ref()
const pieRef = ref()
let trendChart = null
let pieChart = null

const cards = computed(() => [
  { label: '今日订单', value: overview.value.todayOrders ?? 0, foot: '含全部状态', color: '#409eff' },
  { label: '今日 GMV', value: '¥' + fen2yuan(overview.value.todayGmv || 0), foot: '有效订单金额', color: '#ff6b35' },
  { label: '待接单', value: overview.value.pendingOrders ?? 0, foot: '需尽快处理', color: '#f56c6c' },
  { label: '配送中', value: overview.value.deliveringOrders ?? 0, foot: '履约进行中', color: '#e6a23c' },
  { label: '在售商品', value: overview.value.productCount ?? 0, foot: '商品总数', color: '#2f9e6f' },
  { label: '优惠券', value: overview.value.voucherCount ?? 0, foot: '已配置券数', color: '#909399' }
])

function q(payload = {}) {
  const query = { ...payload }
  if (props.shopId) query.shopId = props.shopId
  return query
}

async function loadOverview() {
  const data = await request.get('/merchant/dashboard/overview', { params: q(), silent: true })
  overview.value = data || {}
  needOnboard.value = !isAdmin.value
    && Number(data?.auditStatus) !== 1
}

async function loadTrend() {
  try {
    const data = await request.get('/merchant/dashboard/trend', { params: q({ days: days.value }) })
    renderTrend(data || [])
  } catch {
    renderTrend([])
  }
}

async function loadPie() {
  try {
    const data = await request.get('/merchant/dashboard/orderStatus', { params: q() })
    renderPie(data || [])
  } catch {
    renderPie([])
  }
}

async function loadTop() {
  try {
    topProducts.value = await request.get('/merchant/dashboard/topProducts', { params: q(), silent: true }) || []
  } catch {
    topProducts.value = []
  }
}

function renderTrend(rows) {
  if (!trendRef.value) return
  trendChart = trendChart || echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单量', 'GMV(元)'], right: 0 },
    grid: { left: 40, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: rows.map(r => r.date.slice(5)) },
    yAxis: [
      { type: 'value', name: '单' },
      { type: 'value', name: '元' }
    ],
    series: [
      { name: '订单量', type: 'bar', barWidth: 16, itemStyle: { color: '#409eff' }, data: rows.map(r => r.orders) },
      {
        name: 'GMV(元)', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#ff6b35' },
        data: rows.map(r => Number((r.gmv / 100).toFixed(2)))
      }
    ]
  }, true)
}

function renderPie(rows) {
  if (!pieRef.value) return
  pieChart = pieChart || echarts.init(pieRef.value)
  const data = rows.filter(r => Number(r.value) > 0).map(r => ({ name: r.name, value: r.value }))
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '44%'],
      label: { formatter: '{b}\n{c}' },
      data: data.length ? data : [{ name: '暂无订单', value: 1, itemStyle: { color: '#e8ecf2' } }]
    }]
  }, true)
}

function resize() {
  trendChart?.resize()
  pieChart?.resize()
}

async function loadAll() {
  await loadOverview()
  await Promise.all([loadTrend(), loadPie(), loadTop()])
}

const off = bus.on('MERCHANT_SHOP_CHANGED', () => loadAll())

watch(() => props.shopId, () => loadAll())

onMounted(async () => {
  await nextTick()
  await loadAll()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  off()
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<style scoped>
.stat-card { margin-bottom: 12px; }
.stat-label { font-size: 13px; color: #8a94a6; }
.stat-value { font-size: 24px; font-weight: 700; margin: 6px 0 4px; }
.stat-foot { font-size: 12px; color: #b0b8c4; }
.mt { margin-top: 4px; }
.chart { height: 300px; width: 100%; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.mini { text-align: center; padding: 8px 0; }
.mini-value { font-size: 22px; font-weight: 700; }
.mini-label { font-size: 12px; color: #8a94a6; }
.quick { display: flex; gap: 8px; flex-wrap: wrap; }
.alert-body { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
</style>
