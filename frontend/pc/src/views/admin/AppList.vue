<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：商户管理
  界面名称：商户应用列表页（查看商户支付应用）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item>
          <el-input v-model="filters.shopName" placeholder="请输入商户名称" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.appId" placeholder="请输入应用APPID" clearable style="width: 230px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="appList" v-loading="loading" stripe>
        <el-table-column prop="appId" label="应用APPID" min-width="240" show-overflow-tooltip />
        <el-table-column prop="appName" label="应用名称" min-width="160" />
        <el-table-column prop="shopNum" label="所属商户号" width="140" />
        <el-table-column prop="shopName" label="商户名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.state) === 1 ? 'success' : 'info'" effect="light">
              {{ Number(row.state) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getPayAppList } from '../../api/admin'

const loading = ref(false) // 列表加载中状态
const filters = reactive({ shopName: '', appId: '' }) // 筛选条件
const appList = ref([]) // 商户应用列表数据

// 拉取应用列表（携带筛选条件，Loading 包裹）
async function loadData() {
  loading.value = true
  try {
    const res = await getPayAppList({ ...filters, pageNum: 1, pageSize: 200 })
    appList.value = res.records || []
  } finally {
    loading.value = false
  }
}

// 点击搜索：按当前筛选条件重新加载
function handleSearch() {
  loadData()
}

// 点击重置：清空筛选条件并重新加载
function handleReset() {
  filters.shopName = ''
  filters.appId = ''
  loadData()
}

onMounted(loadData)
</script>
