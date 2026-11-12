<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：订单管理
  界面名称：订单列表页（待发货 / 发货操作）
  ============================================
-->
<template>
  <view class="page">
    <!-- 状态 Tab -->
    <scroll-view scroll-x class="tabs-scroll">
      <view class="tabs-row">
        <view
          v-for="t in tabs"
          :key="t.value"
          class="tab-item"
          :class="{ active: currentTab === t.value }"
          @tap="currentTab = t.value"
        >
          {{ t.label }}
          <text v-if="t.badge" class="tab-badge">{{ t.badge }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 搜索条：订单号/商品名/买家 -->
    <view class="search-bar">
      <input v-model="keyword" class="search-input" placeholder="搜索订单号/商品名/买家" placeholder-class="ph" confirm-type="search" @confirm="load" />
      <view class="btn-primary search-btn" @tap="load">搜索</view>
    </view>

    <!-- 订单列表 -->
    <view v-if="filteredOrders.length > 0" class="order-list">
      <view v-for="o in filteredOrders" :key="o.orderNum" class="order-card" @tap="goDetail(o)">
        <view class="order-head">
          <text class="order-num">{{ o.orderNum }}</text>
          <text class="order-state" :class="'state-' + o.state">{{ stateText(o.state) }}</text>
        </view>
        <view class="order-body">
          <image class="order-img" :src="imgSrc(o.image)" mode="aspectFill" />
          <view class="order-info">
            <view class="order-goods">{{ o.goodsName }}</view>
            <view class="order-sub">买家：{{ o.buyer }}　x{{ o.num }}</view>
            <view class="order-time">{{ o.createdAt }}</view>
          </view>
          <view class="order-amount">¥{{ o.total.toFixed(2) }}</view>
        </view>
        <view class="order-foot">
          <text class="pay-way">支付方式：{{ wayLabel(o.wayCode) }}</text>
          <!-- 操作按钮组：待发货显示“发货”，已发货显示“查看物流” -->
        <view class="order-actions">
            <view v-if="o.state === 1" class="btn-primary small" @tap.stop="handleShip(o)">发货</view>
            <view v-if="o.state === 2" class="btn-plain small" @tap.stop="goDetail(o)">查看物流</view>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty">📦 暂无相关订单</view>
  </view>
  <MTabBar active="orders" @backtop="scrollTop" />
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import MTabBar from './MTabBar.vue'
import { getMerchantOrders } from '../../common/merchant'
import { imgSrc } from '../../common/api'
import { isLoggedIn } from '../../common/store'

// 状态筛选 Tab（value: -1全部 / 1待发货 / 2已发货 / 3已完成 / 4已关闭）
const tabs = [
  { label: '全部', value: -1 },
  { label: '待发货', value: 1, badge: 0 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已关闭', value: 4 }
]

// 当前选中的状态 Tab
const currentTab = ref(-1)
// 商家订单列表
const orders = ref([])

// 按当前 Tab 过滤订单（全部则直接返回）
const filteredOrders = computed(() => {
  if (currentTab.value === -1) return orders.value
  return orders.value.filter(o => o.state === currentTab.value)
})

// 订单状态文案映射：0待支付/1待发货/2已发货/3已完成/4已关闭
function stateText(state) {
  return { 0: '待支付', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭' }[state] || '未知'
}

// 双击底部 Tab：页面回到顶端
function scrollTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

// 支付方式文案映射：WECHAT/ALIPAY/UNIONPAY
function wayLabel(code) {
  return { WECHAT: '微信支付', ALIPAY: '支付宝', UNIONPAY: '银联' }[code] || code
}

// 跳转订单详情并进入发货模式（传 ship=1）
function handleShip(order) {
  uni.navigateTo({ url: '/pages/merchant/order-detail?orderNum=' + order.orderNum + '&ship=1' })
}

// 跳转订单详情页
function goDetail(order) {
  uni.navigateTo({ url: '/pages/merchant/order-detail?orderNum=' + order.orderNum })
}

// 加载订单列表（金额字段转数字，统计待发货数量写入 Tab 角标）
// 搜索关键字（订单号/商品名/买家）
const keyword = ref('')

async function load() {
  try {
    const res = await getMerchantOrders({ keyword: keyword.value.trim() })
    orders.value = (res.records || []).map(o => ({ ...o, total: Number(o.total || 0), money: Number(o.money || 0), fee: Number(o.fee || 0) }))
    const pending = orders.value.filter(o => o.state === 1).length
    tabs[1].badge = pending || ''
  } catch (e) {
    uni.showToast({ title: e.message || '订单加载失败', icon: 'none' })
  }
}

// 页面显示时校验登录态并加载订单
// 进入页面：支持携带 state 参数直接定位到对应状态 Tab（工作台待办入口）
onLoad((query) => {
  if (query && query.state !== undefined && query.state !== '') {
    const s = Number(query.state)
    if (tabs.some(t => t.value === s)) currentTab.value = s
  }
})

onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  load()
})
</script>

<style scoped>
.page {
  /* 底部留白须大于固定底栏（MTabBar 约 110rpx + 安全区），否则最后一张卡片被遮挡 */
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}

/* 搜索条 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 14rpx;
  background: #ffffff;
  padding: 18rpx 24rpx;
}

.search-input {
  flex: 1;
  height: 64rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  color: #1f2329;
}

.search-btn {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 28rpx;
  font-size: 26rpx;
  flex-shrink: 0;
}

.tabs-scroll {
  white-space: nowrap;
  background: #ffffff;
}

.tabs-row {
  display: inline-flex;
  padding: 0 12rpx;
}

.tab-item {
  display: inline-block;
  padding: 24rpx 28rpx;
  font-size: 28rpx;
  color: #4e5560;
  position: relative;
}

.tab-item.active {
  color: #2b6cff;
  font-weight: 700;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 10rpx;
  width: 48rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: #2b6cff;
}

.tab-badge {
  font-size: 20rpx;
  color: #e64340;
  margin-left: 4rpx;
}

.order-card {
  background: #ffffff;
  border-radius: 20rpx;
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
}

.order-head {
  display: flex;
  justify-content: space-between;
  padding-bottom: 14rpx;
  border-bottom: 2rpx solid #f0f3f9;
}

.order-num {
  font-size: 24rpx;
  color: #606266;
}

.order-state {
  font-size: 26rpx;
  font-weight: 600;
}

.state-1 { color: #ff9800; }
.state-2 { color: #2b6cff; }
.state-3 { color: #2ba471; }
.state-4 { color: #8a8f99; }

.order-body {
  display: flex;
  padding: 18rpx 0;
}

.order-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.order-info {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}

.order-goods {
  font-size: 28rpx;
  font-weight: 600;
}

.order-sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8a8f99;
}

.order-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b0b3b8;
}

.order-amount {
  font-size: 30rpx;
  font-weight: 700;
  color: #2b6cff;
}

.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 14rpx;
  border-top: 2rpx solid #f0f3f9;
}

.pay-way {
  font-size: 22rpx;
  color: #8a8f99;
}

.order-actions {
  display: flex;
  gap: 16rpx;
}

.small {
  height: 56rpx;
  line-height: 56rpx;
  font-size: 24rpx;
  padding: 0 34rpx;
}

.empty {
  text-align: center;
  color: #8a8f99;
  padding: 160rpx 0;
}
</style>
