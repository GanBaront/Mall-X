<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：订单管理
  界面名称：商户通知列表页（平台向商户推送的通知消息）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <!-- 筛选区：通知标题 / 商户 / 通知类型 -->
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item>
          <el-input v-model="filters.title" placeholder="请输入通知标题" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.shopName" placeholder="请输入商户名称" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.noticeType" placeholder="通知类型" clearable style="width: 150px">
            <el-option label="转账通知" value="转账通知" />
            <el-option label="退款通知" value="退款通知" />
            <el-option label="审核通知" value="审核通知" />
            <el-option label="系统通知" value="系统通知" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="noticeList" v-loading="loading" stripe>
        <el-table-column prop="title" label="通知标题" min-width="170" show-overflow-tooltip />
        <el-table-column prop="noticeType" label="通知类型" width="110" />
        <el-table-column prop="shopName" label="接收商户" min-width="170">
          <template #default="{ row }">
            <el-tag v-if="!row.shopNum" type="primary" effect="light">全部商户</el-tag>
            <span v-else>{{ row.shopName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="通知内容" min-width="300" show-overflow-tooltip />
        <el-table-column label="推送状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.state) === 1 ? 'success' : 'warning'" effect="light">
              {{ Number(row.state) === 1 ? '已推送' : '待推送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
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
import { getMerchantNoticeList } from '../../api/admin'

const loading = ref(false)
const filters = reactive({ title: '', shopName: '', noticeType: null })
const page = reactive({ current: 1, size: 10, total: 0 })
const noticeList = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantNoticeList({
      pageNum: page.current,
      pageSize: page.size,
      title: filters.title,
      shopName: filters.shopName,
      noticeType: filters.noticeType
    })
    noticeList.value = res.records || []
    page.total = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '通知列表加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.current = 1
  loadData()
}

function handleReset() {
  filters.title = ''
  filters.shopName = ''
  filters.noticeType = null
  handleSearch()
}

function handleSizeChange() {
  page.current = 1
  loadData()
}

onMounted(loadData)
</script>
