<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：订单管理
  界面名称：转账订单列表页（商户转账记录查询）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
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
          <el-input v-model="filters.transferId" placeholder="请输入转账单号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.shopName" placeholder="请输入商户名称" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.state" placeholder="转账状态" clearable style="width: 150px">
            <el-option v-for="s in stateOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="transferList" v-loading="loading" stripe>
        <el-table-column prop="transferId" label="转账单号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="shopName" label="商户名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="entryAccount" label="入账账户" min-width="180" show-overflow-tooltip />
        <el-table-column prop="entryName" label="收款人" width="110" />
        <el-table-column label="转账金额" width="120">
          <template #default="{ row }">
            <span class="money-cell">¥ {{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="转账状态" width="110">
          <template #default="{ row }">
            <el-tag :type="stateTagType(row.state)" effect="light">{{ stateLabel(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="170" />
        <el-table-column label="完成时间" width="170">
          <template #default="{ row }">{{ row.finishTime || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="Number(row.state) === 0">
              <el-button type="primary" size="small" @click="handleTransfer(row, true)">同意打款</el-button>
              <el-button type="danger" size="small" plain @click="handleTransfer(row, false)">驳回</el-button>
            </template>
            <span v-else class="done-text">已处理</span>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getTransferOrderList, handleTransferOrder } from '../../api/admin'

// 转账状态选项：0 处理中 / 1 成功 / 2 失败
const stateOptions = [
  { label: '处理中', value: 0 },
  { label: '转账成功', value: 1 },
  { label: '转账失败', value: 2 }
]

// 状态值映射为文字标签
function stateLabel(state) {
  return stateOptions.find(s => s.value === Number(state))?.label ?? '未知'
}

// 状态值映射为 el-tag 颜色类型（处理中-橙/成功-绿/失败-红）
function stateTagType(state) {
  switch (Number(state)) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const loading = ref(false) // 列表加载中状态
const filters = reactive({ timeRange: null, transferId: '', shopName: '', state: null }) // 筛选条件
const page = reactive({ current: 1, size: 10, total: 0 }) // 分页状态
const transferList = ref([]) // 转账订单列表

// 拉取转账订单（携带分页与筛选条件，时间区间拆为 startTime/endTime）
async function loadData() {
  loading.value = true
  try {
    const res = await getTransferOrderList({
      pageNum: page.current,
      pageSize: page.size,
      transferId: filters.transferId,
      shopName: filters.shopName,
      state: filters.state,
      startTime: filters.timeRange?.[0] || '',
      endTime: filters.timeRange?.[1] || ''
    })
    transferList.value = res.records || []
    page.total = res.total || 0
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
  filters.transferId = ''
  filters.shopName = ''
  filters.state = null
  handleSearch()
}

// 切换每页条数：重置到第一页并重新加载
function handleSizeChange() {
  page.current = 1
  loadData()
}

// 平台处理提现申请：同意打款 / 驳回（驳回后金额退回商户余额），带二次确认
async function handleTransfer(row, approve) {
  const tip = approve
    ? `确认向商户「${row.shopName}」打款 ¥${row.amount}？`
    : `驳回该笔提现申请？¥${row.amount} 将退回商户可提现余额。`
  try {
    await ElMessageBox.confirm(tip, '提现处理', { type: approve ? 'warning' : 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  } catch (e) {
    return
  }
  try {
    const res = await handleTransferOrder(row.transferId, approve)
    ElMessage.success(res.message || '处理成功')
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '处理失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.done-text {
  color: #b0b3b8;
  font-size: 12px;
}
</style>
