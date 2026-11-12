<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：订单管理
  界面名称：退款订单列表页（退款审核与状态查询）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item>
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
          <el-input v-model="filters.refundOrderId" placeholder="请输入退款订单号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.payOrderId" placeholder="请输入原支付订单号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.shopName" placeholder="请输入商户名称" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.refundState" placeholder="退款状态" clearable style="width: 150px">
            <el-option v-for="s in stateOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="refundList" v-loading="loading" stripe>
        <el-table-column label="退款金额" width="120">
          <template #default="{ row }">
            <span class="money-cell">¥ {{ row.refundMoney }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="refundOrderId" label="退款订单号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="payOrderId" label="原支付订单号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="shopName" label="商户名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="退款状态" width="110">
          <template #default="{ row }">
            <el-tag :type="stateTagType(row.refundState)" effect="light">{{ stateLabel(row.refundState) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="refundReason" label="退款原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="applyTime" label="申请时间" width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          layout="prev, pager, next, sizes, total"
          background
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 退款详情抽屉 -->
    <el-drawer v-model="detailVisible" title="退款订单详情" size="45%">
      <el-descriptions :column="2" border v-if="detailRow">
        <el-descriptions-item label="退款订单号">{{ detailRow.refundOrderId }}</el-descriptions-item>
        <el-descriptions-item label="原支付订单号">{{ detailRow.payOrderId }}</el-descriptions-item>
        <el-descriptions-item label="商户名称">{{ detailRow.shopName }}</el-descriptions-item>
        <el-descriptions-item label="商户号">{{ detailRow.shopNum }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">¥ {{ detailRow.refundMoney }}</el-descriptions-item>
        <el-descriptions-item label="退款状态">
          <el-tag :type="stateTagType(detailRow.refundState)">{{ stateLabel(detailRow.refundState) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ detailRow.applyTime }}</el-descriptions-item>
        <el-descriptions-item label="退款完成时间">{{ detailRow.refundTime || '--' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <el-descriptions :column="1">
        <el-descriptions-item label="退款原因">{{ detailRow?.refundReason }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getRefundOrderList } from '../../api/admin'

// 退款单状态选项：0 审核中 / 1 成功 / 2 失败
const stateOptions = [
  { label: '审核中', value: 0 },
  { label: '退款成功', value: 1 },
  { label: '退款失败', value: 2 }
]

// 状态值映射为文字标签
function stateLabel(state) {
  return stateOptions.find(s => s.value === Number(state))?.label ?? '未知'
}

// 状态值映射为 el-tag 颜色类型（审核中-橙/成功-绿/失败-红）
function stateTagType(state) {
  switch (Number(state)) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const loading = ref(false) // 列表加载中状态
const filters = reactive({
  timeRange: null,    // 时间区间
  refundOrderId: '',  // 退款订单号
  payOrderId: '',     // 原支付订单号
  shopName: '',       // 商户名称
  refundState: null   // 退款状态
})

// 分页状态：当前页 / 每页条数 / 总条数
const page = reactive({ current: 1, size: 10, total: 0 })
const refundList = ref([])    // 退款订单列表
const detailVisible = ref(false) // 详情抽屉显隐
const detailRow = ref(null)      // 当前查看的退款单

// 拉取退款订单列表（携带分页与筛选条件；时间区间拆为 startTime/endTime 传后端）
async function loadData() {
  loading.value = true
  try {
    const res = await getRefundOrderList({
      pageNum: page.current,
      pageSize: page.size,
      refundOrderId: filters.refundOrderId,
      payOrderId: filters.payOrderId,
      shopName: filters.shopName,
      refundState: filters.refundState,
      startTime: filters.timeRange?.[0] || '',
      endTime: filters.timeRange?.[1] || ''
    })
    refundList.value = res.records || []
    page.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '退款订单加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

// 点击搜索：回到第一页并重新加载
function handleSearch() {
  page.current = 1
  loadData()
}

// 点击重置：清空全部筛选条件并重新查询
function handleReset() {
  filters.timeRange = null
  filters.refundOrderId = ''
  filters.payOrderId = ''
  filters.shopName = ''
  filters.refundState = null
  handleSearch()
}

// 切换每页条数：重置到第一页并重新加载
function handleSizeChange() {
  page.current = 1
  loadData()
}

// 打开退款详情抽屉
function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>
