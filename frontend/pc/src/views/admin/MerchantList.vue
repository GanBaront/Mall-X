<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：商户管理
  界面名称：商户列表页（审核商户 / 费率配置 / 启用禁用）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item>
          <el-input v-model="filters.shopNum" placeholder="请输入商户号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.shopName" placeholder="请输入商户名称" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.shopState" placeholder="商户状态" clearable style="width: 150px">
            <el-option v-for="s in stateOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 商户表格 -->
      <el-table :data="merchantList" v-loading="loading" stripe>
        <el-table-column prop="shopNum" label="商户号" width="140" />
        <el-table-column prop="shopName" label="商户名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="shopPhone" label="联系手机号" width="130" />
        <el-table-column prop="shopAddress" label="经营地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="stateTagType(row.shopState)" effect="light">{{ stateLabel(row.shopState) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="综合费率" width="100">
          <template #default="{ row }">{{ row.mchRate ? row.mchRate : '--' }}</template>
        </el-table-column>
        <el-table-column prop="applyTime" label="注册/申请时间" width="170" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <template v-if="row.shopState === 0">
              <el-button link type="success" @click="handleAudit(row, 1)">审核通过</el-button>
              <el-button link type="danger" @click="openReject(row)">审核驳回</el-button>
            </template>
            <el-button link type="primary" @click="openRate(row)">费率配置</el-button>
            <el-button v-if="row.shopState === 1" link type="warning" @click="handleToggleState(row, 2)">禁用</el-button>
            <el-button v-else-if="row.shopState === 2" link type="success" @click="handleToggleState(row, 1)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="rejectVisible" title="审核驳回" width="440px">
      <el-form label-width="80px">
        <el-form-item label="商户名称">{{ rejectRow?.shopName }}</el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectRemark" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 费率配置弹窗 -->
    <el-dialog v-model="rateVisible" title="商户费率配置" width="480px">
      <el-form label-width="100px">
        <el-form-item label="商户名称">{{ rateRow?.shopName }}</el-form-item>
        <el-form-item label="支付渠道">
          <el-select v-model="rateForm.wayCode" placeholder="请选择支付渠道" style="width: 220px">
            <el-option label="微信支付" value="WECHAT" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="银联支付" value="UNIONPAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="差异化费率" required>
          <el-input-number v-model="rateForm.mchRate" :min="0" :max="10" :step="0.01" :precision="2" style="width: 220px" />
          <span class="rate-unit">%</span>
        </el-form-item>
        <div class="rate-tip">商户渠道差异化费率优先级高于平台基础费率（指导书 4.5.3）</div>
      </el-form>
      <template #footer>
        <el-button @click="rateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmRate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { auditMerchant, configMerchantRate, getMerchantList, updateMerchantState } from '../../api/admin'

// 商户状态选项：0 待审核 / 1 正常 / 2 禁用
const stateOptions = [
  { label: '待审核', value: 0 },
  { label: '正常', value: 1 },
  { label: '禁用', value: 2 }
]

// 状态值映射为文字标签（用于表格展示）
function stateLabel(state) {
  return stateOptions.find(s => s.value === Number(state))?.label ?? '未知'
}

// 状态值映射为 el-tag 颜色类型（待审核-警告/正常-成功/禁用-危险）
function stateTagType(state) {
  switch (Number(state)) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const loading = ref(false)     // 列表加载中状态
const submitting = ref(false)  // 弹窗提交中状态
const filters = reactive({ shopNum: '', shopName: '', shopState: null }) // 筛选条件
const merchantList = ref([])   // 商户列表数据

// 拉取商户列表（携带筛选条件，Loading 包裹）
async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantList({ ...filters, pageNum: 1, pageSize: 200 })
    merchantList.value = res.records || []
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
  filters.shopNum = ''
  filters.shopName = ''
  filters.shopState = null
  loadData()
}

/* ===== 审核 ===== */
const rejectVisible = ref(false) // 驳回原因弹窗显隐
const rejectRow = ref(null)     // 当前被驳回的商户行
const rejectRemark = ref('')    // 驳回原因输入

// 审核通过：二次确认后调用审核接口（shopState=1）
async function handleAudit(row, shopState) {
  await ElMessageBox.confirm(`确认审核通过商户「${row.shopName}」的入驻申请？`, '审核确认', { type: 'warning' })
  const res = await auditMerchant({ shopNum: row.shopNum, shopState, auditRemark: '' })
  ElMessage.success(res.message || '审核成功')
  loadData()
}

// 打开驳回弹窗并重置原因输入
function openReject(row) {
  rejectRow.value = row
  rejectRemark.value = ''
  rejectVisible.value = true
}

// 确认驳回：校验原因非空后调用审核接口（shopState=2）
async function confirmReject() {
  if (!rejectRemark.value.trim()) {
    ElMessage.warning('驳回时必须填写原因')
    return
  }
  submitting.value = true
  try {
    const res = await auditMerchant({
      shopNum: rejectRow.value.shopNum,
      shopState: 2,
      auditRemark: rejectRemark.value.trim()
    })
    ElMessage.success(res.message || '已驳回')
    rejectVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

/* ===== 费率配置 ===== */
const rateVisible = ref(false) // 费率配置弹窗显隐
const rateRow = ref(null)      // 当前配置费率的商户行
const rateForm = reactive({ wayCode: 'WECHAT', mchRate: 0.6 }) // 费率表单：渠道 + 差异化费率

// 打开费率配置弹窗，预填默认渠道与商户当前费率
function openRate(row) {
  rateRow.value = row
  rateForm.wayCode = 'WECHAT'
  rateForm.mchRate = row.mchRate ? parseFloat(row.mchRate) : 0.6
  rateVisible.value = true
}

// 保存费率配置：调用接口写入商户渠道差异化费率
async function confirmRate() {
  submitting.value = true
  try {
    const res = await configMerchantRate({
      shopNum: rateRow.value.shopNum,
      wayCode: rateForm.wayCode,
      mchRate: rateForm.mchRate
    })
    ElMessage.success(res.message || '费率配置成功')
    rateVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

/* ===== 启用/禁用 ===== */
// 切换商户状态：shopState=1 启用 / 2 禁用，二次确认后调用接口
async function handleToggleState(row, shopState) {
  const action = shopState === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(`确认${action}商户「${row.shopName}」？`, '状态变更', { type: 'warning' })
  const res = await updateMerchantState({ shopNum: row.shopNum, shopState })
  ElMessage.success(res.message || `${action}成功`)
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.rate-unit {
  margin-left: 8px;
  color: #606266;
}

.rate-tip {
  margin-left: 100px;
  font-size: 12px;
  color: #909399;
}
</style>
