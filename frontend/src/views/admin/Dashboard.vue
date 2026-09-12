<template>
  <div>
    <el-row :gutter="16">
      <el-col v-for="c in cards" :key="c.label" :xs="12" :sm="8" :md="4">
        <el-card class="stat-card" shadow="never">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="stat-foot">{{ c.foot }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="overview.pendingApplies > 0" class="page-card" type="warning" show-icon :closable="false">
      <template #default>
        <div class="alert-body">
          <span>有 <b>{{ overview.pendingApplies }}</b> 条商家入驻申请等待审核。</span>
          <el-button type="primary" size="small" @click="$router.push('/admin/applies')">立即处理</el-button>
        </div>
      </template>
    </el-alert>

    <el-row :gutter="16" class="mt">
      <el-col :md="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>平台订单 / GMV 趋势</span>
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
          <template #header><span>账号角色分布</span></template>
          <div ref="roleRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :md="8">
        <el-card shadow="never">
          <template #header><span>店铺审核状态</span></template>
          <div ref="auditRef" class="chart-sm"></div>
        </el-card>
      </el-col>
      <el-col :md="8">
        <el-card shadow="never">
          <template #header><span>订单状态分布</span></template>
          <div ref="statusRef" class="chart-sm"></div>
        </el-card>
      </el-col>
      <el-col :md="8">
        <el-card shadow="never">
          <template #header><span>新增账号趋势</span></template>
          <div ref="userRef" class="chart-sm"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :md="14">
        <el-card shadow="never">
          <template #header><span>店铺 GMV 排行 Top10</span></template>
          <el-table :data="shopRank" size="small" empty-text="暂无成交数据">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="shopName" label="店铺" min-width="150" show-overflow-tooltip />
            <el-table-column prop="cnt" label="订单数" width="90" />
            <el-table-column label="GMV" width="120">
              <template #default="{ row }">¥{{ fen2yuan(row.gmv) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :md="10">
        <el-card shadow="never">
          <template #header><span>平台实时指标</span></template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="今日 GMV">¥{{ fen2yuan(overview.todayGmv || 0) }}</el-descriptions-item>
            <el-descriptions-item label="累计 GMV">¥{{ fen2yuan(overview.totalGmv || 0) }}</el-descriptions-item>
            <el-descriptions-item label="累计订单">{{ overview.totalOrders || 0 }}</el-descriptions-item>
            <el-descriptions-item label="今日页面 UV">
              {{ overview.uvToday || 0 }}（HyperLogLog 统计）
            </el-descriptions-item>
            <el-descriptions-item label="当前在线连接">
              {{ overview.onlineUsers || 0 }}（WebSocket 实时）
            </el-descriptions-item>
            <el-descriptions-item label="封禁账号">{{ overview.bannedUsers || 0 }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { fen2yuan } from '@/utils/roles'

const overview = ref({})
const shopRank = ref([])
const days = ref(7)

const trendRef = ref()
const roleRef = ref()
const auditRef = ref()
const statusRef = ref()
const userRef = ref()
const charts = {}

const cards = computed(() => [
  { label: '今日订单', value: overview.value.todayOrders ?? 0, foot: '全平台', color: '#409eff' },
  { label: '今日 GMV', value: '¥' + fen2yuan(overview.value.todayGmv || 0), foot: '有效订单', color: '#ff6b35' },
  { label: '用户数', value: overview.value.totalUsers ?? 0, foot: 'role=1', color: '#2f9e6f' },
  { label: '商家数', value: overview.value.totalMerchants ?? 0, foot: 'role=2', color: '#7c5cff' },
  { label: '店铺数', value: overview.value.totalShops ?? 0, foot: `营业中 ${overview.value.openShops ?? 0}`, color: '#e6a23c' },
  { label: '待审核', value: overview.value.pendingApplies ?? 0, foot: '入驻申请', color: '#f56c6c' }
])

function getChart(refEl, key) {
  if (!refEl) return null
  charts[key] = charts[key] || echarts.init(refEl)
  return charts[key]
}

async function loadOverview() {
  overview.value = await request.get('/admin/stats/overview') || {}
}

async function loadTrend() {
  const rows = await request.get('/admin/stats/trend', { params: { days: days.value } })
  const chart = getChart(trendRef.value, 'trend')
  chart?.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单量', 'GMV(元)'], right: 0 },
    grid: { left: 40, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: (rows || []).map(r => r.date.slice(5)) },
    yAxis: [{ type: 'value', name: '单' }, { type: 'value', name: '元' }],
    series: [
      { name: '订单量', type: 'bar', barWidth: 14, itemStyle: { color: '#409eff' }, data: (rows || []).map(r => r.orders) },
      {
        name: 'GMV(元)', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#ff6b35' },
        data: (rows || []).map(r => Number((r.gmv / 100).toFixed(2)))
      }
    ]
  }, true)
}

async function loadRole() {
  const rows = await request.get('/admin/stats/roleDistribution')
  const chart = getChart(roleRef.value, 'role')
  chart?.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    color: ['#409eff', '#2f9e6f', '#f56c6c'],
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '45%'],
      label: { formatter: '{b}\n{c}' },
      data: (rows || []).map(r => ({ name: r.name, value: r.value }))
    }]
  }, true)
}

async function loadAudit() {
  const rows = await request.get('/admin/stats/shopAudit')
  const chart = getChart(auditRef.value, 'audit')
  chart?.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 70, right: 30, top: 20, bottom: 30 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: (rows || []).map(r => r.name) },
    series: [{
      type: 'bar', barWidth: 16,
      itemStyle: {
        color: (p) => ['#e6a23c', '#2f9e6f', '#f56c6c', '#909399'][p.dataIndex] || '#409eff'
      },
      label: { show: true, position: 'right' },
      data: (rows || []).map(r => r.value)
    }]
  }, true)
}

async function loadStatus() {
  const rows = await request.get('/admin/stats/orderStatus')
  const chart = getChart(statusRef.value, 'status')
  chart?.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['40%', '66%'], center: ['50%', '44%'],
      label: { formatter: '{b}\n{c}' },
      data: (rows || []).filter(r => Number(r.value) > 0).map(r => ({ name: r.name, value: r.value }))
    }]
  }, true)
}

async function loadUserTrend() {
  const rows = await request.get('/admin/stats/userTrend', { params: { days: days.value } })
  const chart = getChart(userRef.value, 'user')
  chart?.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 16, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: (rows || []).map(r => r.date.slice(5)) },
    yAxis: { type: 'value' },
    series: [{
      type: 'line', smooth: true, areaStyle: { opacity: 0.18 }, itemStyle: { color: '#7c5cff' },
      data: (rows || []).map(r => r.count)
    }]
  }, true)
}

async function loadRank() {
  shopRank.value = await request.get('/admin/stats/shopRank') || []
}

function resize() {
  Object.values(charts).forEach(c => c?.resize())
}

onMounted(async () => {
  await nextTick()
  await Promise.all([loadOverview(), loadTrend(), loadRole(), loadAudit(), loadStatus(), loadUserTrend(), loadRank()])
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  Object.values(charts).forEach(c => c?.dispose())
})
</script>

<style scoped>
.stat-card { margin-bottom: 12px; }
.stat-label { font-size: 13px; color: #8a94a6; }
.stat-value { font-size: 24px; font-weight: 700; margin: 6px 0 4px; }
.stat-foot { font-size: 12px; color: #b0b8c4; }
.mt { margin-top: 4px; }
.chart { height: 320px; }
.chart-sm { height: 250px; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.alert-body { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
</style>
