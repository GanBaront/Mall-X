<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：经营看板
  界面名称：商家工作台首页（经营数据看板）
  ============================================
-->
<template>
  <view class="page">
    <!-- 店铺头 -->
    <view class="shop-header">
      <view class="shop-avatar">{{ shopInitial }}</view>
      <view class="shop-meta">
        <view class="shop-name">{{ shopName }}</view>
        <view class="shop-state">{{ shopStateText }}</view>
      </view>
    </view>

    <!-- 今日经营 -->
    <view class="card">
      <view class="section-title">今日经营</view>
      <view class="stat-row">
        <view class="stat-item">
          <view class="stat-num primary">¥{{ stats.todayIncome.toFixed(2) }}</view>
          <view class="stat-label">今日营收</view>
        </view>
        <view class="stat-item">
          <view class="stat-num">{{ stats.todayOrders }}</view>
          <view class="stat-label">今日订单</view>
        </view>
        <view class="stat-item">
          <view class="stat-num">{{ stats.goodsCount }}</view>
          <view class="stat-label">在售商品</view>
        </view>
      </view>
    </view>

    <!-- 待办提醒 -->
    <view class="card">
      <view class="section-title">待办提醒</view>
      <view class="todo-row">
        <view class="todo-item" @tap="goOrders(1)">
          <view class="todo-num warn">{{ stats.pendingShip }}</view>
          <view class="todo-text">待发货订单</view>
        </view>
        <view class="todo-item" @tap="goRefund">
          <view class="todo-num danger">{{ stats.refunding }}</view>
          <view class="todo-text">待处理售后</view>
        </view>
        <view class="todo-item" @tap="goFunds">
          <view class="todo-num success">¥{{ stats.balance }}</view>
          <view class="todo-text">可提现余额</view>
        </view>
      </view>
    </view>

    <!-- 近七日营收 -->
    <view class="card">
      <view class="section-title">近七日营收（元）</view>
      <view class="bars">
        <view v-for="(b, idx) in bars" :key="idx" class="bar-col">
          <view class="bar-value">{{ b.value }}</view>
          <view class="bar-wrap">
            <view class="bar" :style="{ height: b.h + '%' }" />
          </view>
          <view class="bar-day">{{ b.day }}</view>
        </view>
      </view>
      <view class="month-line">本月营收：¥{{ stats.monthIncome.toFixed(2) }}　交易成功率：{{ stats.successRate }}</view>
    </view>

    <!-- 渠道占比 -->
    <view class="card">
      <view class="section-title">渠道交易占比</view>
      <view v-for="c in stats.channelTradeData" :key="c.wayCode" class="channel-row">
        <text class="channel-name">{{ c.wayCode }}</text>
        <view class="channel-bar-wrap">
          <view class="channel-bar" :style="{ width: c.percent + '%' }" />
        </view>
        <text class="channel-money">¥{{ c.tradeMoney.toFixed(0) }}</text>
      </view>
    </view>

    <!-- 快捷入口 -->
    <view class="quick-grid">
      <view class="quick-item" @tap="goGoods">📦<text>商品管理</text></view>
      <view class="quick-item" @tap="goOrders(-1)">🧾<text>订单管理</text></view>
      <view class="quick-item" @tap="goRefund">↩️<text>售后退款</text></view>
      <view class="quick-item" @tap="goFunds">💰<text>资金提现</text></view>
    </view>
  </view>
  <MTabBar active="index" @backtop="scrollTop" />
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MTabBar from './MTabBar.vue'
import { getWorkbench } from '../../common/merchant'
import { store, isLoggedIn } from '../../common/store'

// 经营看板数据（今日/本月营收、订单数、待办、渠道占比等）
const stats = ref({
  todayIncome: 0, todayOrders: 0, monthIncome: 0, totalIncome: 0,
  totalServiceFee: 0, pendingShip: 0, refunding: 0, goodsCount: 0,
  successRate: '--', balance: 0,
  channelTradeData: [],
  sevenDayTradeData: { days: [], values: [] }
})

// 店铺名称（取登录用户店铺名，未登录回退提示）
const shopName = computed(() => store.user?.shopName || '未登录店铺')
// 店铺头像首字（取店铺名首字符）
const shopInitial = computed(() => (store.user?.shopName || '店').slice(0, 1))
// 店铺状态文案：0待审核 / 1正常 / 2禁用
const shopStateText = computed(() => {
  const s = store.user?.shopState
  return s === 1 ? '正常经营' : s === 0 ? '待审核' : '已禁用'
})

// 近七日营收柱状图：按最大值归一化高度（百分比）
const bars = computed(() => {
  const { days = [], values = [] } = stats.value.sevenDayTradeData
  const max = Math.max(...values, 1)
  return days.map((d, i) => ({ day: d, value: values[i], h: Math.round(values[i] / max * 100) }))
})

// 加载经营看板数据（失败回退提示）
async function load() {
  try {
    const res = await getWorkbench()
    const chans = (res.channelTradeData || []).map(c => ({
      wayCode: c.wayCode,
      tradeMoney: Number(c.tradeMoney || 0),
      tradeCount: Number(c.tradeCount || 0)
    }))
    const total = chans.reduce((s, c) => s + c.tradeMoney, 0) || 1 // 渠道总金额，用于计算各渠道占比
    stats.value = {
      ...stats.value,
      ...res,
      todayIncome: Number(res.todayIncome || 0),
      todayOrders: Number(res.todayOrders || 0),
      monthIncome: Number(res.monthIncome || 0),
      totalIncome: Number(res.totalIncome || 0),
      totalServiceFee: Number(res.totalServiceFee || 0),
      actualIncome: Number(res.actualIncome || 0),
      // 可提现余额取后端实际净收益
      balance: Number(res.actualIncome || 0),
      pendingShip: Number(res.pendingShip || 0),
      refunding: Number(res.refunding || 0),
      goodsCount: Number(res.goodsCount || 0),
      successRate: res.successRate || '--',
      channelTradeData: chans.map(c => ({
        ...c,
        percent: Math.round(c.tradeMoney / total * 100) // 渠道交易占比（百分比，四舍五入）
      })),
      sevenDayTradeData: {
        days: (res.sevenDayTradeData && res.sevenDayTradeData.days) || [],
        values: ((res.sevenDayTradeData && res.sevenDayTradeData.values) || []).map(Number)
      }
    }
  } catch (e) {
    uni.showToast({ title: e.message || '经营数据加载失败', icon: 'none' })
  }
}

// 跳转订单列表页：带上状态筛选（1=待发货等，与订单页 Tab 的 value 对应）
function goOrders(state) {
  uni.reLaunch({ url: '/pages/merchant/orders?state=' + state })
}

// 双击底部 Tab：页面回到顶端
function scrollTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

// 跳转商品管理页
function goGoods() { uni.reLaunch({ url: '/pages/merchant/goods' }) }
// 跳转售后退款页
function goRefund() { uni.navigateTo({ url: '/pages/merchant/refund' }) }
// 跳转资金提现页
function goFunds() { uni.navigateTo({ url: '/pages/merchant/funds' }) }

// 页面显示时校验登录态与角色（非商家账号直接进工作台会频繁接口报错）并加载看板数据
onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  if (store.user && store.user.role !== 'merchant') {
    uni.showToast({ title: '该页面仅商家账号可用', icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 800)
    return
  }
  load()
})
</script>

<style scoped>
.page {
  /* 底部留白须大于固定底栏（MTabBar 约 110rpx + 安全区），否则最后一行被遮挡 */
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}

.shop-header {
  display: flex;
  align-items: center;
  padding: 36rpx 30rpx;
  padding-top: calc(var(--status-bar-height) + 36rpx);
  background: linear-gradient(135deg, #2b6cff 0%, #6ea1ff 100%);
  color: #ffffff;
}

.shop-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 700;
}

.shop-meta {
  margin-left: 24rpx;
}

.shop-name {
  font-size: 34rpx;
  font-weight: 700;
}

.shop-state {
  margin-top: 8rpx;
  font-size: 24rpx;
  opacity: 0.9;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 22rpx;
}

.stat-row {
  display: flex;
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 36rpx;
  font-weight: 800;
}

.primary { color: #2b6cff; }
.warn { color: #ff9800; }
.danger { color: #e64340; }
.success { color: #2ba471; }

.stat-label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a8f99;
}

.todo-row {
  display: flex;
}

.todo-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.todo-num {
  font-size: 40rpx;
  font-weight: 800;
}

.todo-text {
  font-size: 24rpx;
  color: #4e5560;
}

/* 七日柱状图 */
.bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 240rpx;
  padding: 0 6rpx;
}

.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.bar-value {
  font-size: 20rpx;
  color: #606266;
}

.bar-wrap {
  flex: 1;
  width: 36rpx;
  display: flex;
  align-items: flex-end;
}

.bar {
  width: 100%;
  background: linear-gradient(180deg, #6ea1ff 0%, #2b6cff 100%);
  border-radius: 8rpx 8rpx 0 0;
}

.bar-day {
  font-size: 20rpx;
  color: #8a8f99;
  padding: 8rpx 0;
}

.month-line {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #606266;
}

/* 渠道占比 */
.channel-row {
  display: flex;
  align-items: center;
  padding: 14rpx 0;
}

.channel-name {
  width: 110rpx;
  font-size: 26rpx;
}

.channel-bar-wrap {
  flex: 1;
  height: 20rpx;
  background: #eef1f6;
  border-radius: 10rpx;
  overflow: hidden;
  margin: 0 16rpx;
}

.channel-bar {
  height: 100%;
  background: linear-gradient(90deg, #6ea1ff 0%, #2b6cff 100%);
  border-radius: 10rpx;
}

.channel-money {
  width: 160rpx;
  text-align: right;
  font-size: 24rpx;
  color: #606266;
}

/* 快捷入口 */
.quick-grid {
  display: flex;
  background: #ffffff;
  margin: 20rpx 24rpx;
  border-radius: 20rpx;
  padding: 26rpx 0;
}

.quick-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  font-size: 40rpx;
}

.quick-item text {
  font-size: 24rpx;
  color: #4e5560;
}
</style>
