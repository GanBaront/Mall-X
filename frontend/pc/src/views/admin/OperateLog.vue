<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：系统管理
  界面名称：系统日志页（操作日志查询）
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
          <el-input v-model="filters.username" placeholder="请输入操作人" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.module" placeholder="操作模块" clearable style="width: 150px">
            <el-option label="商户管理" value="商户管理" />
            <el-option label="订单管理" value="订单管理" />
            <el-option label="支付配置" value="支付配置" />
            <el-option label="系统管理" value="系统管理" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logList" v-loading="loading" stripe>
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="module" label="操作模块" width="130" />
        <el-table-column prop="action" label="操作内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP 地址" width="140" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.result === '成功' ? 'success' : 'danger'" effect="light">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="170" />
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
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getOperateLogs } from '../../api/admin'

const loading = ref(false) // 列表加载中状态
const filters = reactive({ timeRange: null, username: '', module: null }) // 筛选条件
const page = reactive({ current: 1, size: 10, total: 0 }) // 分页状态
const logList = ref([]) // 操作日志列表

// 拉取系统日志（携带分页与筛选条件，时间区间拆为 startTime/endTime）
async function loadData() {
  loading.value = true
  try {
    const res = await getOperateLogs({
      pageNum: page.current,
      pageSize: page.size,
      username: filters.username,
      module: filters.module,
      startTime: filters.timeRange?.[0] || '',
      endTime: filters.timeRange?.[1] || ''
    })
    logList.value = res.records || []
    page.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '日志加载失败，请检查后端服务')
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
  filters.username = ''
  filters.module = null
  handleSearch()
}

// 切换每页条数：重置到第一页并重新加载
function handleSizeChange() {
  page.current = 1
  loadData()
}

onMounted(loadData)
</script>
