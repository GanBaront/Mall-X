<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：支付配置
  界面名称：支付方式配置页（支付方式列表、所属接口联动状态与启停）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <el-table :data="wayList" v-loading="loading" stripe>
        <el-table-column prop="wayCode" label="支付方式编码" width="150" />
        <el-table-column prop="name" label="支付方式名称" width="150" />
        <el-table-column prop="wayType" label="支付类型" width="110" />
        <el-table-column label="所属支付接口" width="180">
          <template #default="{ row }">
            <span class="channel-name">{{ row.channelName }}</span>
            <el-tag
              v-if="row.channelState !== null"
              :type="Number(row.channelState) === 1 ? 'success' : 'danger'"
              effect="light"
              size="small"
              style="margin-left: 8px"
            >
              {{ Number(row.channelState) === 1 ? '启用' : '停用' }}
            </el-tag>
            <el-tag v-else type="info" effect="light" size="small" style="margin-left: 8px">未配置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.state) === 1 ? 'success' : 'info'" effect="light">
              {{ Number(row.state) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户可用" width="150">
          <template #default="{ row }">
            <el-tag :type="row.usable ? 'success' : 'danger'" effect="plain">
              {{ row.usable ? '可用' : '不可用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="基础费率" width="100">
          <template #default="{ row }">{{ row.rate }}%</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.state) === 1"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="(val) => toggleState(row, val)"
            />
          </template>
        </el-table-column>
      </el-table>
      <div class="page-tip">说明：「用户可用」= 支付方式启用 且 所属支付接口启用；任一停用后用户端下单页即时不可见/不可选。</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPayWayList, savePayWayState } from '../../api/admin'

const loading = ref(false) // 列表加载中状态
const wayList = ref([])    // 支付方式列表

// 挂载时拉取支付方式列表
onMounted(async () => {
  loading.value = true
  try {
    const res = await getPayWayList()
    wayList.value = res.records || []
  } catch (e) {
    ElMessage.error(e.message || '支付方式加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
})

// 启用/停用支付方式：调后端接口成功后本地更新状态，无需整表刷新
async function toggleState(row, val) {
  const state = val ? 1 : 0
  try {
    await savePayWayState(row.wayCode, state)
    row.state = state
    row.usable = state === 1 && (row.channelState === null || Number(row.channelState) === 1)
    ElMessage.success(`已${state === 1 ? '启用' : '停用'}支付方式「${row.name}」`)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}
</script>

<style scoped>
.channel-name {
  font-size: 13px;
  color: #1f2329;
}

.page-tip {
  margin-top: 14px;
  font-size: 12px;
  color: #8a8f99;
}
</style>
