<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：服务商管理
  界面名称：服务商列表页（查看 ISV 服务商信息与状态）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <!-- 筛选区：按服务商名称 / 编号查询 -->
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item>
          <el-input v-model="filters.isvName" placeholder="请输入服务商名称" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.isvNum" placeholder="请输入服务商编号" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="isvList" v-loading="loading" stripe>
        <el-table-column prop="isvNum" label="服务商编号" width="160" />
        <el-table-column prop="isvName" label="服务商名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="110" />
        <el-table-column prop="linkPhone" label="联系电话" width="140" />
        <el-table-column prop="appId" label="服务商应用ID" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.state) === 1 ? 'success' : 'info'" effect="light">
              {{ Number(row.state) === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getIsvList } from '../../api/admin'

const loading = ref(false)
const filters = reactive({ isvName: '', isvNum: '' })
const isvList = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await getIsvList({ ...filters, pageNum: 1, pageSize: 200 })
    isvList.value = res.records || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  filters.isvName = ''
  filters.isvNum = ''
  loadData()
}

onMounted(loadData)
</script>
