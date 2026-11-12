<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：订单 Tab / 下单流程
  界面名称：订单详情页（收货地址 + 商品信息 + 订单详细信息）
  ============================================
-->
<template>
  <view class="page">
    <!-- 自定义导航栏：返回键 + 订单状态标题 -->
    <view class="navbar">
      <view class="nav-left" @tap="goBack">
        <text class="nav-back">←</text>
        <text class="nav-title">{{ stateText(order.state) }}</text>
      </view>
    </view>

    <template v-if="order.orderNum">
      <!-- 收货地址卡片 -->
      <view class="card address-card">
        <view class="addr-row">
          <text class="addr-icon">📍</text>
          <view class="addr-body">
            <view class="addr-text">{{ order.address || '暂无收货地址' }}</view>
            <view class="addr-buyer">收货人：{{ order.buyer || '-' }}</view>
          </view>
        </view>
        <!-- 物流单号（商户发货后展示） -->
        <view v-if="order.logisticsNo" class="addr-logistics">
          <text class="info-label">物流单号</text>
          <text class="info-value">{{ order.logisticsNo }}</text>
        </view>
      </view>

      <!-- 商品信息卡片 -->
      <view class="card goods-card">
        <view class="shop-row">
          <text class="shop-icon">🏪</text>
          <text class="shop-name">{{ order.shopName || 'Mall-X 商铺' }}</text>
        </view>
        <view class="goods-row">
          <image class="goods-img" :src="imgSrc(order.image)" mode="aspectFill" />
          <view class="goods-body">
            <view class="goods-name">{{ order.goodsName }}</view>
            <view class="goods-meta">
              <text class="goods-price">¥{{ money(order.price) }}</text>
              <text class="goods-num">x{{ order.num }}</text>
            </view>
          </view>
        </view>
        <view class="goods-total">
          <text>共 {{ order.num }} 件</text>
          <text class="total-text">实付款：<text class="total-num">¥{{ money(order.total) }}</text></text>
        </view>
      </view>

      <!-- 订单信息卡片 -->
      <view class="card info-card">
        <view class="info-title">订单信息</view>
        <view class="info-row">
          <text class="info-label">订单编号</text>
          <view class="info-right">
            <text class="info-value">{{ order.orderNum }}</text>
            <text class="copy-btn" @tap="copyText(order.orderNum)">复制</text>
          </view>
        </view>
        <view class="info-row">
          <text class="info-label">支付方式</text>
          <text class="info-value">{{ wayText(order.wayCode) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">收货信息</text>
          <text class="info-value multiline">{{ order.address || '-' }}</text>
        </view>
        <view v-if="order.channelOrderNo" class="info-row">
          <text class="info-label">渠道交易号</text>
          <text class="info-value">{{ order.channelOrderNo }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">创建时间</text>
          <text class="info-value">{{ order.createdAt || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">付款时间</text>
          <text class="info-value">{{ order.payTime || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">发货时间</text>
          <text class="info-value">{{ order.shipTime || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">成交时间</text>
          <text class="info-value">{{ order.finishTime || '-' }}</text>
        </view>
      </view>
    </template>

    <!-- 加载失败/空态 -->
    <view v-else-if="loadError" class="empty">
      <text class="empty-icon">📄</text>
      <text>{{ loadError }}</text>
    </view>

    <!-- 底部操作栏：状态对应的操作按钮（售后中 state=5 也展示提示条） -->
    <view v-if="order.orderNum && (order.state === 0 || order.state === 2 || canApplyAfterSale || order.state === 5 || Number(order.refundState) === 0)" class="foot-bar">
      <view v-if="order.state === 0" class="btn-plain" @tap="handleCancel">取消订单</view>
      <view v-if="order.state === 0" class="btn-primary" @tap="handlePay">去支付</view>
      <view v-if="order.state === 2" class="btn-primary" @tap="handleReceive">确认收货</view>
      <view v-if="canApplyAfterSale" class="btn-plain" @tap="showRefundSheet = true">
        {{ order.state === 1 ? '申请退款' : '申请售后' }}
      </view>
      <view v-if="order.state === 5 || Number(order.refundState) === 0" class="refund-tag">售后处理中，等待商家确认</view>
    </view>

    <!-- 申请售后弹窗（选择售后原因） -->
    <refund-sheet
      :visible="showRefundSheet"
      :order-state="Number(order.state || 1)"
      :order-num="order.orderNum || ''"
      @close="showRefundSheet = false"
      @success="onRefundSuccess"
    />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ORDER_STATE_TEXT } from '../../common/store'
import { getOrderDetail, createOrder, openPayUrl, confirmOrderApi, cancelOrderApi, imgSrc } from '../../common/api'
import { toast } from '../../common/toast'
import RefundSheet from '../../components/refund-sheet/refund-sheet.vue'

// 订单详情数据（后端 /api/order/detail 合并履约单与支付单信息）
const order = ref({})
const loadError = ref('')

// 是否可申请售后：待发货（仅退款）或已完成（售后退款），且无审核中的售后申请
const canApplyAfterSale = computed(() => {
  const s = order.value.state
  return (s === 1 || s === 3) && Number(order.value.refundState ?? -1) !== 0
})

/** 金额格式化（后端金额为字符串） */
function money(v) {
  return Number(v || 0).toFixed(2)
}

/** 状态数字 → 中文文案 */
function stateText(state) {
  const t = ORDER_STATE_TEXT[state]
  return t ? t : '订单详情'
}

/** 支付方式编码 → 中文 */
function wayText(wayCode) {
  const map = { WECHAT: '微信支付', ALIPAY: '支付宝', ALIPAY_WAP: '支付宝', UNIONPAY: '银联' }
  return map[wayCode] || wayCode || '-'
}

/** 复制文本到剪贴板 */
function copyText(text) {
  if (!text) return
  uni.setClipboardData({ data: text, success: () => uni.showToast({ title: '已复制', icon: 'none' }) })
}

/** 返回：有上一页则返回，否则回到首页 */
function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.reLaunch({ url: '/pages/index/index' })
}

/** 加载订单详情 */
async function load(orderNum) {
  try {
    const record = await getOrderDetail(orderNum)
    order.value = record || {}
  } catch (e) {
    loadError.value = e.message || '订单详情加载失败'
  }
}

/** 去支付：复用同一商户订单号向网关重新取收银台链接 */
async function handlePay() {
  uni.showLoading({ title: '正在获取收银台...' })
  try {
    const res = await createOrder({
      wayCode: order.value.wayCode || 'ALIPAY',
      payMoney: Number(order.value.total).toFixed(2),
      subject: order.value.goodsName,
      num: order.value.num,
      orderNum: order.value.orderNum
    })
    uni.hideLoading()
    if (res.payUrl) openPayUrl(res.payUrl)
    else uni.showToast({ title: res.message || '支付单已创建', icon: 'none' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '支付失败', icon: 'none' })
  }
}

/** 确认收货：二次确认后调用后端接口并刷新详情 */
function handleReceive() {
  uni.showModal({
    title: '提示',
    content: '确认已收到全部商品？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await confirmOrderApi(order.value.orderNum)
          toast('交易完成')
          load(order.value.orderNum)
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

/** 取消订单：二次确认后调用后端接口（已用优惠券自动退回）并刷新详情 */
function handleCancel() {
  uni.showModal({
    title: '取消订单',
    content: '确定取消该待支付订单吗？已使用的优惠券会自动退回。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelOrderApi(order.value.orderNum)
          toast('订单已取消')
          load(order.value.orderNum)
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

/** 申请售后/退款：打开「选择售后原因」底部弹窗（组件内提交），成功后刷新详情 */
const showRefundSheet = ref(false)

function onRefundSuccess() {
  showRefundSheet.value = false
  load(order.value.orderNum)
}

onLoad((query) => {
  if (query && query.orderNum) load(query.orderNum)
  else loadError.value = '缺少订单号参数'
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding-bottom: 140rpx;
}

/* 自定义导航栏：返回键 + 状态标题（预留系统状态栏高度） */
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

/* 通用卡片 */
.card {
  background: #ffffff;
  border-radius: 20rpx;
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
}

/* 收货地址卡片 */
.addr-row {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
}

.addr-icon {
  font-size: 36rpx;
}

.addr-body {
  flex: 1;
}

.addr-text {
  font-size: 28rpx;
  color: #1f2329;
  line-height: 1.5;
}

.addr-buyer {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #8a8f99;
}

.addr-logistics {
  display: flex;
  justify-content: space-between;
  margin-top: 18rpx;
  padding-top: 18rpx;
  border-top: 2rpx solid #f0f3f9;
}

/* 商品卡片 */
.shop-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding-bottom: 16rpx;
  border-bottom: 2rpx solid #f0f3f9;
}

.shop-icon {
  font-size: 30rpx;
}

.shop-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2329;
}

.goods-row {
  display: flex;
  gap: 16rpx;
  padding: 18rpx 0;
}

.goods-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  background: #f0f3f9;
}

.goods-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.goods-name {
  font-size: 27rpx;
  color: #1f2329;
  line-height: 1.4;
}

.goods-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.goods-price {
  color: #2b6cff;
  font-size: 28rpx;
  font-weight: 600;
}

.goods-num {
  font-size: 24rpx;
  color: #8a8f99;
}

.goods-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16rpx;
  border-top: 2rpx solid #f0f3f9;
  font-size: 24rpx;
  color: #8a8f99;
}

.total-text {
  color: #1f2329;
}

.total-num {
  color: #2b6cff;
  font-size: 32rpx;
  font-weight: 800;
}

/* 订单信息卡片 */
.info-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2329;
  padding-bottom: 16rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24rpx;
  padding: 12rpx 0;
}

.info-label {
  font-size: 25rpx;
  color: #8a8f99;
  flex-shrink: 0;
}

.info-value {
  font-size: 25rpx;
  color: #1f2329;
  text-align: right;
  word-break: break-all;
}

.info-value.multiline {
  max-width: 480rpx;
}

.info-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.copy-btn {
  font-size: 22rpx;
  color: #2b6cff;
  border: 2rpx solid #2b6cff;
  border-radius: 999rpx;
  padding: 2rpx 16rpx;
}

/* 底部操作栏 */
.foot-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  background: #ffffff;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.btn-primary {
  background: #2b6cff;
  color: #ffffff;
  font-size: 27rpx;
  font-weight: 600;
  border-radius: 999rpx;
  padding: 16rpx 56rpx;
}

.btn-plain {
  background: #ffffff;
  color: #4e5560;
  border: 2rpx solid #d4dbe8;
  font-size: 27rpx;
  border-radius: 999rpx;
  padding: 14rpx 44rpx;
}

.refund-tag {
  flex: 1;
  font-size: 24rpx;
  color: #ff9800;
  background: #fff4e5;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  text-align: center;
  margin-right: 8rpx;
}

/* 加载失败/空态 */
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
