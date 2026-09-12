<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="reload">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="2">待接单</el-radio-button>
        <el-radio-button :value="3">已接单</el-radio-button>
        <el-radio-button :value="4">配送中</el-radio-button>
        <el-radio-button :value="5">已完成</el-radio-button>
        <el-radio-button :value="6">已取消</el-radio-button>
      </el-radio-group>
      <div class="right">
        <el-tag v-if="!isAdmin" type="info" effect="plain">仅展示本店订单</el-tag>
        <el-tag v-else type="warning" effect="plain">平台代管模式</el-tag>
        <el-button size="small" @click="reload">刷新</el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" row-key="id" empty-text="暂无订单">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail">
            <div class="detail-line"><b>订单号</b>{{ row.number }}</div>
            <div class="detail-line"><b>收货地址</b>{{ row.address }}</div>
            <div class="detail-line"><b>备注</b>{{ row.remark || '无' }}</div>
            <div class="detail-line"><b>下单时间</b>{{ row.createTime }}</div>
            <div class="detail-line"><b>支付时间</b>{{ row.payTime || '未支付' }}</div>
            <el-table :data="row.items || []" size="small" border style="margin-top:8px">
              <el-table-column prop="productName" label="商品" />
              <el-table-column label="单价" width="100">
                <template #default="s">¥{{ fen2yuan(s.row.price) }}</template>
              </el-table-column>
              <el-table-column prop="count" label="数量" width="80" />
              <el-table-column label="小计" width="110">
                <template #default="s">¥{{ fen2yuan(s.row.price * s.row.count) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="number" label="订单号" width="180" />
      <el-table-column prop="userName" label="下单用户" width="110" />
      <el-table-column prop="shopName" label="店铺" min-width="140" show-overflow-tooltip />
      <el-table-column label="金额" width="110">
        <template #default="{ row }"><b style="color:#ff6b35">¥{{ fen2yuan(row.amount) }}</b></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="ORDER_STATUS_TAG[row.status]" size="small">{{ ORDER_STATUS_TEXT[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 2" type="primary" size="small" @click="act(row, 'accept')">接单</el-button>
          <el-button v-if="row.status === 3" type="primary" size="small" @click="act(row, 'deliver')">开始配送</el-button>
          <el-button v-if="row.status === 4" type="success" size="small" @click="act(row, 'finish')">完成</el-button>
          <el-button v-if="row.status === 2 || row.status === 1" type="danger" size="small" plain
                     @click="act(row, 'cancel')">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="onPage" />
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { ORDER_STATUS_TAG, ORDER_STATUS_TEXT, fen2yuan } from '@/utils/roles'
import bus from '@/utils/bus'

const props = defineProps({ shopId: { type: [Number, String], default: null } })
const user = useUserStore()
const isAdmin = computed(() => user.isAdmin)

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref(null)
const loading = ref(false)

function params() {
  const p = { page: page.value, size: size.value }
  if (status.value) p.status = status.value
  if (props.shopId) p.shopId = props.shopId
  return p
}

async function reload() {
  loading.value = true
  try {
    const data = await request.get('/merchant/order/page', { params: params() })
    records.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  page.value = p
  reload()
}

async function act(row, action) {
  const text = { accept: '接单', deliver: '开始配送', finish: '完成订单', cancel: '取消订单' }[action]
  if (action === 'cancel') {
    await ElMessageBox.confirm(`确定取消订单 ${row.number} 吗？取消后会回补库存。`, '确认', { type: 'warning' })
  }
  await request.post(`/merchant/order/${row.id}/${action}`)
  ElMessage.success(`${text}成功`)
  reload()
}

const offOrder = bus.on('MERCHANT_RELOAD_ORDERS', () => reload())
const offShop = bus.on('MERCHANT_SHOP_CHANGED', () => { page.value = 1; reload() })
watch(() => props.shopId, () => { page.value = 1; reload() })

onMounted(reload)
onBeforeUnmount(() => {
  offOrder()
  offShop()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.toolbar .right { display: flex; align-items: center; gap: 8px; }
.detail { padding: 8px 16px; background: #fafbfc; }
.detail-line { font-size: 13px; color: #4a5568; margin-bottom: 4px; }
.detail-line b { display: inline-block; width: 84px; color: #8a94a6; font-weight: 500; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
