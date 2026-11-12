<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：主页看板
  界面名称：主页看板页（统计卡片 + 交易柱状图 + 支付方式饼图）
  ============================================
-->
<template>
  <div class="page-container dashboard">
    <!-- 顶部统计卡片行 -->
    <el-row :gutter="16">
      <el-col :span="8">
        <div class="panel-card stat-card">
          <div class="stat-grid">
            <div class="stat-item">
              <div class="stat-label">平台总盈利金额：</div>
              <div class="stat-value">¥ {{ stats.platformProfit }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">交易总笔数：</div>
              <div class="stat-value">{{ stats.totalCount }}<span class="stat-unit">笔</span></div>
            </div>
            <div class="stat-item">
              <div class="stat-label">交易总金额：</div>
              <div class="stat-value">¥ {{ stats.totalMoney }}</div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel-card stat-card">
          <div class="stat-grid">
            <div class="stat-item">
              <div class="stat-label">商家交易费率：</div>
              <div class="stat-value">{{ stats.mchRate }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">银联费率：</div>
              <div class="stat-value">{{ stats.unionpayRate }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">支付宝费率：</div>
              <div class="stat-value">{{ stats.alipayRate }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">微信费率：</div>
              <div class="stat-value">{{ stats.wechatRate }}</div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel-card greet-card">
          <div class="greet-user">
            <el-avatar :size="48" class="greet-avatar">A</el-avatar>
            <span class="greet-role">操作员</span>
          </div>
          <div class="greet-text">{{ greeting }}，{{ adminName }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表行 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <div class="panel-card chart-card">
          <div class="chart-header">
            <span class="chart-title">交易统计</span>
            <div class="chart-tools">
              <el-select v-model="tradeRange" style="width: 130px">
                <el-option label="最近七天" value="7" />
                <el-option label="最近三十天" value="30" />
              </el-select>
              <span class="unit-text">单位（元）</span>
            </div>
          </div>
          <div class="legend-bar">
            <span class="legend-item"><i class="dot dot-income" />收款</span>
            <span class="legend-item"><i class="dot dot-refund" />退款</span>
          </div>
          <div ref="barChartRef" class="bar-chart" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel-card chart-card">
          <div class="chart-header">
            <span class="chart-title">支付方式</span>
            <el-select v-model="payRange" style="width: 130px">
              <el-option label="最近七天" value="7" />
              <el-option label="最近三十天" value="30" />
            </el-select>
          </div>
          <div ref="pieChartRef" class="pie-chart" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getPlatformStats } from '../../api/admin'

// 当前登录管理员名称（本地缓存读取，缺省 admin）
const adminName = localStorage.getItem('admin_name') || 'admin'

// 根据当前小时计算问候语（凌晨/上午/中午/下午/晚上）
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 9) return '上午好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// 看板统计指标（初始占位，数据加载后由后端/模拟数据填充）
const stats = reactive({
  platformProfit: '0.00', // 平台总盈利
  totalMoney: '0.00',     // 交易总金额
  totalCount: 0,         // 交易总笔数
  mchRate: '--',         // 商家交易费率
  alipayRate: '--',      // 支付宝费率
  wechatRate: '--',      // 微信费率
  unionpayRate: '--',    // 银联费率
  channelRatio: [],      // 支付方式占比数据（饼图）
  sevenDayData: { dates: [], income: [], refund: [] } // 近七日交易走势
})

// 交易柱状图时间范围选择（7 天 / 30 天，演示默认 7 天）
const tradeRange = ref('7')
// 支付方式饼图时间范围选择
const payRange = ref('7')

// 图表 DOM 引用
const barChartRef = ref()
const pieChartRef = ref()
let barChart = null // ECharts 柱状图实例
let pieChart = null // ECharts 饼图实例

// 图表配色常量
const INCOME_COLOR = '#ff9800'  // 收款柱颜色
const REFUND_COLOR = '#ffd08a'  // 退款柱颜色
const PIE_COLORS = ['#3b82f6', '#33c3cf', '#52c41a'] // 饼图各扇区颜色

// 渲染近七日交易柱状图（收款 vs 退款）
function renderBarChart() {
  const { dates, income, refund } = stats.sevenDayData
  barChart.setOption({
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisTick: { show: false },
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: '#606266' }
    },
    series: [
      { name: '收款', type: 'bar', barWidth: 18, data: income, itemStyle: { color: INCOME_COLOR }, label: { show: true, position: 'top', color: INCOME_COLOR, fontSize: 11 } },
      { name: '退款', type: 'bar', barWidth: 18, data: refund, itemStyle: { color: REFUND_COLOR }, label: { show: true, position: 'top', color: '#e6a23c', fontSize: 11 } }
    ]
  })
}

// 渲染支付方式占比饼图：将 channelRatio 映射为 name/value 并循环配色
function renderPieChart() {
  const data = stats.channelRatio.map((c, i) => ({
    name: c.wayCode,
    value: c.count,
    itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] }
  }))
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 笔（{d}%）' },
    legend: { show: false },
    series: [
      {
        type: 'pie',
        radius: ['45%', '68%'],
        center: ['50%', '50%'],
        label: { show: true, formatter: '{b}\n{c}', color: '#606266', fontSize: 12 },
        labelLine: { length: 14, length2: 10 },
        data
      }
    ]
  })
}

// 窗口尺寸变化时同步缩放两个图表
function resizeCharts() {
  barChart && barChart.resize()
  pieChart && pieChart.resize()
}

// 拉取平台统计（days=趋势/占比的时间范围天数）；失败必须提示，禁止静默吞错
async function loadStats(days) {
  try {
    return await getPlatformStats({ days: Number(days) })
  } catch (e) {
    ElMessage.error(e.message || '统计数据加载失败，请检查后端服务')
    return null
  }
}

// 顶部统计卡片（全量口径，只在首次加载时填充）
function fillSummary(res) {
  Object.assign(stats, {
    platformProfit: res.platformProfit ?? '0.00',
    totalMoney: res.totalMoney ?? '0.00',
    totalCount: res.totalCount ?? 0,
    mchRate: res.mchRate ?? '--',
    alipayRate: res.alipayRate ?? '--',
    wechatRate: res.wechatRate ?? '--',
    unionpayRate: res.unionpayRate ?? '--'
  })
}

onMounted(async () => {
  // 先初始化图表实例并监听窗口缩放（数据加载失败时页面结构依然完整）
  barChart = echarts.init(barChartRef.value)
  pieChart = echarts.init(pieChartRef.value)
  window.addEventListener('resize', resizeCharts)

  const res = await loadStats(7)
  if (res) {
    fillSummary(res)
    stats.sevenDayData = res.sevenDayData ?? { dates: [], income: [], refund: [] }
    stats.channelRatio = res.channelRatio ?? []
  }
  renderBarChart()
  renderPieChart()
})

// 切换时间范围：交易柱状图按所选天数重新拉取
watch(tradeRange, async (days) => {
  const res = await loadStats(days)
  if (res) {
    stats.sevenDayData = res.sevenDayData ?? { dates: [], income: [], refund: [] }
    renderBarChart()
  }
})

// 切换时间范围：支付方式占比按所选天数重新拉取
watch(payRange, async (days) => {
  const res = await loadStats(days)
  if (res) {
    stats.channelRatio = res.channelRatio ?? []
    renderPieChart()
  }
})

// 组件卸载时移除监听并销毁图表实例，避免内存泄漏
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  barChart && barChart.dispose()
  pieChart && pieChart.dispose()
})
</script>

<style scoped>
/* ===== 顶部统计卡片 ===== */
.stat-card {
  height: 148px;
  display: flex;
  align-items: center;
}

.stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26px 12px;
  width: 100%;
}

.stat-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1f2329;
}

.stat-unit {
  font-size: 14px;
  font-weight: 400;
  margin-left: 4px;
  color: #606266;
}

/* ===== 问候卡片 ===== */
.greet-card {
  height: 148px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 14px;
}

.greet-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.greet-avatar {
  background: #1f2329;
  color: #fff;
  font-weight: 700;
}

.greet-role {
  color: #303133;
}

.greet-text {
  font-size: 18px;
  color: #303133;
}

/* ===== 图表卡片 ===== */
.chart-row {
  margin-top: 16px;
}

.chart-card {
  height: 420px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.chart-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.unit-text {
  color: #909399;
  font-size: 13px;
}

.legend-bar {
  display: flex;
  justify-content: center;
  gap: 28px;
  margin-bottom: 4px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 13px;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  display: inline-block;
}

.dot-income {
  background: #ff9800;
}

.dot-refund {
  background: #ffd08a;
}

.bar-chart {
  width: 100%;
  height: 320px;
}

.pie-chart {
  width: 100%;
  height: 350px;
  margin-top: 12px;
}
</style>
