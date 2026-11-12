<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：订单 Tab
  界面名称：我的订单（状态筛选 / 订单列表）
  ============================================
-->
<template>
  <view class="page">
    <!-- 自定义导航栏：返回键 + 标题 -->
    <view class="navbar">
      <view class="nav-left" @tap="goBack">
        <text class="nav-back">←</text>
        <text class="nav-title">我的订单</text>
      </view>
    </view>

    <!-- 搜索栏：按商品名称 / 订单号模糊搜索 -->
    <view class="search-bar">
      <text class="search-icon">🔍</text>
      <input
        class="search-input"
        v-model="searchText"
        placeholder="搜索商品名称 / 订单号"
        placeholder-class="search-placeholder"
        confirm-type="search"
      />
      <text v-if="searchText" class="search-clear" @tap="searchText = ''">✕</text>
    </view>

    <!-- 状态 Tab -->
    <scroll-view scroll-x class="tabs-scroll">
      <view class="tabs-row">
        <view
          v-for="t in tabs"
          :key="t.value"
          class="tab-item"
          :class="{ active: currentTab === t.value }"
          @tap="currentTab = t.value"
        >{{ t.label }}</view>
      </view>
    </scroll-view>

    <!-- 订单列表 -->
    <view v-if="filteredOrders.length > 0" class="order-list">
      <view v-for="o in filteredOrders" :key="o.orderNum" class="order-card" @tap="goDetail(o)">
        <view class="order-head">
          <text class="order-num">订单号：{{ o.orderNum }}</text>
          <text class="order-state" :class="'state-' + o.state">{{ stateText(o.state) }}</text>
        </view>

        <view v-for="i in o.items" :key="i.id" class="order-item">
          <view class="goods-thumb item-thumb">
            <image class="item-thumb-img" :src="imgSrc(i.image)" mode="aspectFill" />
          </view>
          <view class="item-name">{{ i.name }}</view>
          <view class="item-right">
            <text class="item-price">¥{{ i.price.toFixed(2) }}</text>
            <text class="item-num">x{{ i.num }}</text>
          </view>
        </view>

        <view class="order-foot">
          <text class="order-total">合计：<text class="total-num">¥{{ o.total.toFixed(2) }}</text></text>
          <view class="order-actions">
            <view v-if="o.state === 0" class="btn-plain small" @tap.stop="handleCancel(o)">取消订单</view>
            <view v-if="o.state === 0" class="btn-primary small" @tap.stop="handlePay(o)">去支付</view>
            <view v-if="o.state === 2" class="btn-primary small" @tap.stop="handleReceive(o)">确认收货</view>
            <view v-if="canApplyAfterSale(o)" class="btn-plain small" @tap.stop="handleRefund(o)">
              {{ o.state === 1 ? '申请退款' : '申请售后' }}
            </view>
            <view v-if="o.state === 5 || o.refundState === 0" class="refund-tag">售后中</view>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <text class="empty-icon">📦</text>
      <text>暂无相关订单</text>
    </view>

    <!-- 申请售后弹窗（选择售后原因） -->
    <refund-sheet
      v-if="refundTarget"
      :visible="!!refundTarget"
      :order-state="refundTarget.state"
      :order-num="refundTarget.orderNum"
      @close="refundTarget = null"
      @success="onRefundSuccess"
    />

    <!-- 返回顶部悬浮箭头（滚动超过一屏后出现） -->
    <view v-if="showBackTop" class="back-top" @tap="scrollTop">
      <text class="back-top-arrow">↑</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onPageScroll } from '@dcloudio/uni-app'
import { store, ORDER_STATE_TEXT, syncOrders } from '../../common/store'
import { getOrderList, confirmOrderApi, cancelOrderApi, createOrder, openPayUrl, payQuery, imgSrc } from '../../common/api'
import { toast } from '../../common/toast'
import RefundSheet from '../../components/refund-sheet/refund-sheet.vue'

// 订单状态筛选 Tab：value=-1 表示全部，0/1/2/3 对应各状态，5=售后中
const tabs = [
  { label: '全部', value: -1 },
  { label: '待支付', value: 0 },
  { label: '待发货', value: 1 },
  { label: '待收货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '售后中', value: 5 }
]

// 返回：有上一页则返回，否则回到首页
function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.reLaunch({ url: '/pages/index/index' })
}

// 返回顶部：滚动超过一屏（约 500px）显示悬浮箭头，点击平滑回到顶端
const showBackTop = ref(false)
onPageScroll((e) => {
  showBackTop.value = e.scrollTop > 500
})

function scrollTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

/** 点击订单卡片：进入订单详情页 */
function goDetail(order) {
  uni.navigateTo({ url: '/pages/orders/order-detail?orderNum=' + order.orderNum })
}

const currentTab = ref(-1)

// 搜索关键字：命中订单号或任一商品名称（不区分大小写的模糊匹配）
const searchText = ref('')

// 按当前状态 Tab + 搜索关键字过滤订单
const filteredOrders = computed(() => {
  let list = currentTab.value === -1 ? store.orders : store.orders.filter(o => o.state === currentTab.value)
  const kw = searchText.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(o =>
      (o.orderNum || '').toLowerCase().includes(kw) ||
      (o.items || []).some(i => (i.name || '').toLowerCase().includes(kw))
    )
  }
  return list
})

// 状态数字 → 中文文案（映射表见 store.ORDER_STATE_TEXT）
function stateText(state) {
  return ORDER_STATE_TEXT[state] || '未知'
}

/** 服务端记录 → 页面展示结构 */
function mapOrder(r) {
  const price = Number(r.price || 0)
  const num = Number(r.num || 1)
  return {
    orderNum: r.orderNum,
    payOrderId: r.payOrderId,
    wayCode: r.wayCode || 'ALIPAY',
    items: [{ id: r.orderNum, name: r.goodsName, price, image: r.image || '/static/goods/goods-1.jpg', num }],
    total: Number(r.total || 0),
    state: r.state,
    refundState: r.refundState === undefined ? -1 : Number(r.refundState),
    createdAt: r.createdAt
  }
}

// 是否可申请售后：待发货（仅退款）或已完成（售后退款），且无审核中的售后申请
function canApplyAfterSale(o) {
  return (o.state === 1 || o.state === 3) && Number(o.refundState) !== 0
}

// 打开售后弹窗（组件内选择原因后提交）
const refundTarget = ref(null)
function handleRefund(order) {
  refundTarget.value = order
}

// 售后申请提交成功：关闭弹窗并刷新列表
function onRefundSuccess() {
  refundTarget.value = null
  refresh()
}

async function refresh() {
  if (!store.user) return
  try {
    const records = await getOrderList()
    // 待支付单先向网关查询支付结果（后端会向支付宝沙箱主动对账），再刷新列表
    const unpaid = records.filter(r => r.state === 0 && r.payOrderId)
    for (const o of unpaid) {
      try {
        await payQuery(o.payOrderId)
      } catch (e) { /* 单笔对账失败不影响列表展示 */ }
    }
    const fresh = unpaid.length ? await getOrderList() : records
    syncOrders(fresh.map(mapOrder))
  } catch (e) {
    uni.showToast({ title: e.message || '订单加载失败', icon: 'none' })
  }
}

/** 去支付：复用同一商户订单号向网关重新取收银台链接 */
async function handlePay(order) {
  uni.showLoading({ title: '正在获取收银台...' })
  try {
    const res = await createOrder({
      wayCode: order.wayCode || 'ALIPAY',
      payMoney: Number(order.total).toFixed(2),
      subject: order.items[0].name,
      num: order.items.reduce((s, i) => s + i.num, 0),
      orderNum: order.orderNum
    })
    uni.hideLoading()
    if (res.payUrl) openPayUrl(res.payUrl)
    else uni.showToast({ title: res.message || '支付单已创建', icon: 'none' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '支付失败', icon: 'none' })
  }
}

// 取消订单：二次确认后调用后端接口（已用的优惠券自动退回），刷新列表
function handleCancel(order) {
  uni.showModal({
    title: '取消订单',
    content: '确定取消该待支付订单吗？已使用的优惠券会自动退回。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelOrderApi(order.orderNum)
          toast('订单已取消')
          refresh()
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

// 确认收货：弹窗二次确认后调用后端接口，刷新订单列表
function handleReceive(order) {
  uni.showModal({
    title: '提示',
    content: '确认已收到全部商品？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await confirmOrderApi(order.orderNum)
          toast('交易完成')
          refresh()
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

// 进入页面：支持携带 state 参数直接定位到对应状态 Tab（个人中心快捷入口）
onLoad((query) => {
  if (query && query.state !== undefined && query.state !== '') {
    const s = Number(query.state)
    if (tabs.some(t => t.value === s)) currentTab.value = s
  }
})

onShow(() => {
  refresh()
})
</script>

<style scoped>
.page {
  padding-bottom: 30rpx;
}

/* 申请售后按钮 / 售后中标记 */
.btn-plain.small {
  height: 56rpx;
  line-height: 52rpx;
  font-size: 24rpx;
  padding: 0 24rpx;
  border: 2rpx solid #d4dbe8;
  border-radius: 999rpx;
  color: #4e5560;
  background: #ffffff;
}

.refund-tag {
  font-size: 22rpx;
  color: #ff9800;
  background: #fff4e5;
  border-radius: 8rpx;
  padding: 8rpx 16rpx;
}

/* 返回顶部悬浮箭头 */
.back-top {
  position: fixed;
  right: 32rpx;
  bottom: 140rpx;
  width: 84rpx;
  height: 84rpx;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.15);
  border: 2rpx solid #eef1f6;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.back-top-arrow {
  font-size: 40rpx;
  font-weight: 700;
  color: #2b6cff;
}

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  background: #ffffff;
  margin: 16rpx 24rpx 0;
  border-radius: 999rpx;
  padding: 14rpx 24rpx;
  gap: 14rpx;
  border: 2rpx solid #eef1f6;
}

.search-icon {
  font-size: 28rpx;
}

.search-input {
  flex: 1;
  font-size: 26rpx;
  color: #1f2329;
}

.search-placeholder {
  color: #b0b3b8;
}

.search-clear {
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: #8a8f99;
  background: #f0f1f5;
  border-radius: 50%;
  flex-shrink: 0;
}

/* 状态 Tab */
/* 自定义导航栏：返回键 + 标题（预留系统状态栏高度） */
.navbar {
  display: flex;
  align-items: center;
  background: #ffffff;
  padding: 20rpx 30rpx;
  padding-top: calc(var(--status-bar-height) + 20rpx);
  border-bottom: 2rpx solid #f0f3f9;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.nav-back {
  font-size: 40rpx;
  color: #1f2329;
  padding: 0 8rpx;
}

.nav-title {
  font-size: 34rpx;
  font-weight: 700;
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
  padding: 24rpx 30rpx;
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

/* 订单卡片 */
.order-card {
  background: #ffffff;
  border-radius: 20rpx;
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
}

.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
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

.state-0 { color: #ff9800; }
.state-1 { color: #2b6cff; }
.state-2 { color: #2b6cff; }
.state-3 { color: #34c759; }
.state-4 { color: #8a8f99; }

.order-item {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  gap: 16rpx;
}

.item-thumb {
  width: 90rpx;
  height: 90rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  overflow: hidden;
}

.item-thumb-img {
  width: 100%;
  height: 100%;
}

.item-name {
  flex: 1;
  font-size: 26rpx;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
}

.item-price {
  color: #2b6cff;
  font-size: 26rpx;
  font-weight: 600;
}

.item-num {
  font-size: 22rpx;
  color: #8a8f99;
}

.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16rpx;
  border-top: 2rpx solid #f0f3f9;
}

.order-total {
  font-size: 26rpx;
  color: #1f2329;
}

.total-num {
  color: #2b6cff;
  font-size: 32rpx;
  font-weight: 800;
}

.order-actions {
  display: flex;
  gap: 16rpx;
}

.small {
  height: 56rpx;
  line-height: 56rpx;
  font-size: 24rpx;
  padding: 0 32rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  color: #8a8f99;
  padding: 160rpx 0;
  font-size: 28rpx;
}

.empty-icon {
  font-size: 100rpx;
}
</style>
