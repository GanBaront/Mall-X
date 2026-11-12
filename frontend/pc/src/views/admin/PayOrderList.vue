<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：订单管理
  界面名称：支付订单列表页（分页筛选 / 查看订单详情）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <!-- 筛选区：时间区间 / 订单号 / 商户 / 支付方式 / 状态 -->
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item label="">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            range-separator="~"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.payOrderId" placeholder="请输入订单号" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.shopName" placeholder="请输入商户名称" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.wayCode" placeholder="支付方式" clearable style="width: 150px">
            <el-option v-for="w in wayOptions" :key="w.value" :label="w.label" :value="w.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.orderState" placeholder="支付状态" clearable style="width: 150px">
            <el-option v-for="s in stateOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 订单表格 -->
      <el-table :data="orderList" v-loading="loading" stripe>
        <el-table-column label="支付金额" width="110">
          <template #default="{ row }">
            <span class="money-cell">¥ {{ row.money }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退款金额" width="110">
          <template #default="{ row }">¥ {{ row.refundMoney }}</template>
        </el-table-column>
        <el-table-column label="手续费" width="100">
          <template #default="{ row }">¥ {{ row.fee }}</template>
        </el-table-column>
        <el-table-column prop="payOrderId" label="订单号" min-width="200" show-overflow-tooltip />
        <el-table-column label="支付方式" width="100">
          <template #default="{ row }">{{ wayLabel(row.wayCode) }}</template>
        </el-table-column>
        <el-table-column label="支付状态" width="100">
          <template #default="{ row }">
            <el-tag :type="stateTagType(row.orderState)" effect="light">{{ stateLabel(row.orderState) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建日期" width="170" />
        <el-table-column prop="shopName" label="商户名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50]"
          layout="prev, pager, next, sizes, total"
          background
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="detailVisible" title="订单详情" size="55%">
      <div class="detail-body" v-if="detailRow">
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="商户号">{{ detailRow.shopNum }}</el-descriptions-item>
          <el-descriptions-item label="支付订单号">{{ detailRow.payOrderId }}</el-descriptions-item>
          <el-descriptions-item label="商户名称">{{ detailRow.shopName }}</el-descriptions-item>
          <el-descriptions-item label="应用APPID">{{ detailRow.appId }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="stateTagType(detailRow.orderState)">{{ stateLabel(detailRow.orderState) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="支付金额">{{ detailRow.money }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ wayLabel(detailRow.wayCode) }}</el-descriptions-item>
          <el-descriptions-item label="商家费率">{{ detailRow.rate }}%</el-descriptions-item>
          <el-descriptions-item label="手续费">{{ detailRow.fee }}</el-descriptions-item>
          <el-descriptions-item label="支付成功时间">{{ detailRow.payTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailRow.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailRow.updatedAt }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <el-descriptions :column="2">
          <el-descriptions-item label="商品标题">{{ detailRow.subject }}</el-descriptions-item>
          <el-descriptions-item label="商品描述">{{ detailRow.body }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getPayOrderList } from '../../api/admin'

const wayOptions = [
  { label: '微信', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: '银联', value: 'UNIONPAY' }
]

const stateOptions = [
  { label: '待支付', value: -1 },
  { label: '支付成功', value: 0 },
  { label: '支付失败', value: 1 },
  { label: '已退款', value: 2 },
  { label: '订单关闭', value: 3 }
]

function wayLabel(code) {
  return wayOptions.find(w => w.value === code)?.label || code
}

function stateLabel(state) {
  return stateOptions.find(s => s.value === Number(state))?.label ?? '未知'
}

function stateTagType(state) {
  switch (Number(state)) {
    case 0: return 'success'
    case 1: return 'danger'
    case 2: return 'warning'
    case 3: return 'info'
    default: return 'info'
  }
}

const loading = ref(false)
const filters = reactive({
  timeRange: null,
  payOrderId: '',
  shopName: '',
  wayCode: null,
  orderState: null
})

const page = reactive({ current: 1, size: 10, total: 0 })
const orderList = ref([])
const detailVisible = ref(false)
const detailRow = ref(null)

async function loadData() {
  loading.value = true
  try {
    const res = await getPayOrderList({
      pageNum: page.current,
      pageSize: page.size,
      ...filters,
      startTime: filters.timeRange?.[0] || '',
      endTime: filters.timeRange?.[1] || ''
    })
    orderList.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.current = 1
  loadData()
}

function handleReset() {
  filters.timeRange = null
  filters.payOrderId = ''
  filters.shopName = ''
  filters.wayCode = null
  filters.orderState = null
  handleSearch()
}

function handleSizeChange() {
  page.current = 1
  loadData()
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<style scoped>
.detail-body {
  padding: 0 8px;
}
</style>
