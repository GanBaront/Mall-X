<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：订单管理
  界面名称：订单详情页（发货 / 物流）
  ============================================
-->
<template>
  <view class="page">
    <view v-if="order" class="content">
      <!-- 状态卡 -->
      <view class="state-card">
        <view class="state-big">{{ stateText(order.state) }}</view>
        <view class="state-desc">{{ stateDesc }}</view>
      </view>

      <!-- 商品信息 -->
      <view class="card">
        <view class="goods-row">
          <image class="goods-img" :src="imgSrc(order.image)" mode="aspectFill" />
          <view class="goods-info">
            <view class="goods-name">{{ order.goodsName }}</view>
            <view class="goods-num">x{{ order.num }}</view>
          </view>
          <view class="goods-total">¥{{ order.total.toFixed(2) }}</view>
        </view>
      </view>

      <!-- 收货信息 -->
      <view class="card">
        <view class="info-row"><text class="info-label">买家</text><text>{{ order.buyer }}</text></view>
        <view class="info-row"><text class="info-label">收货地址</text><text class="addr">{{ order.address }}</text></view>
        <view class="info-row"><text class="info-label">支付方式</text><text>{{ wayLabel(order.wayCode) }}</text></view>
        <view class="info-row"><text class="info-label">支付单号</text><text class="mono">{{ order.payOrderId }}</text></view>
        <view class="info-row"><text class="info-label">下单时间</text><text>{{ order.createdAt }}</text></view>
        <view v-if="order.logisticsNo" class="info-row">
          <text class="info-label">物流单号</text>
          <text class="mono">{{ order.logisticsNo }}</text>
        </view>
      </view>

      <!-- 发货表单 -->
      <view v-if="order.state === 1" class="card">
        <view class="ship-title">订单发货</view>
        <input
          v-model="logisticsNo"
          class="ship-input"
          placeholder="请输入物流单号（如 SF1234567890）"
          placeholder-class="ph"
        />
        <!-- 确认发货按钮 -->
        <view class="ship-btn btn-primary" @tap="confirmShip">确认发货</view>
      </view>
    </view>

    <view v-else class="empty">订单不存在</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getMerchantOrders, shipOrderMock } from '../../common/merchant'
import { toast } from '../../common/toast'
import { imgSrc } from '../../common/api'

// 当前订单详情
const order = ref(null)
// 物流单号（发货时输入/回填）
const logisticsNo = ref('')
// 是否进入发货模式（来自列表页跳转参数 ship=1）
const shipMode = ref(false)

// 状态说明文案（按订单状态给出操作提示）
const stateDesc = computed(() => {
  if (!order.value) return ''
  return {
    1: '买家已付款，请尽快安排发货',
    2: '商品已发出，等待买家确认收货',
    3: '交易已完成，货款将在结算后入账',
    4: '订单已关闭'
  }[order.value.state] || ''
})

// 订单状态文案映射：0待支付/1待发货/2已发货/3已完成/4已关闭
function stateText(state) {
  return { 0: '待支付', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭' }[state] || '未知'
}

// 支付方式文案映射：WECHAT/ALIPAY/UNIONPAY
function wayLabel(code) {
  return { WECHAT: '微信支付', ALIPAY: '支付宝', UNIONPAY: '银联' }[code] || code
}

// 按订单号加载订单详情（金额转数字，回填物流单号）
// 用后端 keyword 检索定位：订单列表接口默认只返回前 50 条，老订单不在第一页时仍可查到
async function load(orderNum) {
  try {
    let res = await getMerchantOrders({ keyword: orderNum, pageNum: 1, pageSize: 10 })
    let found = (res.records || []).find(o => o.orderNum === orderNum) || null
    if (!found) {
      // keyword 检索兼容商户订单号/商品名匹配，未命中再退回全量列表兜底
      res = await getMerchantOrders()
      found = (res.records || []).find(o => o.orderNum === orderNum) || null
    }
    if (found) found.total = Number(found.total || 0)
    order.value = found
    if (order.value) logisticsNo.value = order.value.logisticsNo || ''
    if (!order.value) uni.showToast({ title: '订单不存在', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e.message || '订单加载失败', icon: 'none' })
  }
}

// 确认发货（校验物流单号非空，调用 mock 发货，成功后更新订单状态与单号）
function confirmShip() {
  if (!logisticsNo.value.trim()) {
    uni.showToast({ title: '请输入物流单号', icon: 'none' })
    return
  }
  uni.showLoading({ title: '提交中...' })
  shipOrderMock(order.value.orderNum, logisticsNo.value.trim()).then(() => {
    uni.hideLoading()
    order.value.state = 2
    order.value.logisticsNo = logisticsNo.value.trim()
    toast('发货成功')
  }).catch((e) => {
    uni.hideLoading()
    uni.showToast({ title: e.message || '发货失败', icon: 'none' })
  })
}

// 页面加载：读取订单号加载详情，并标记是否进入发货模式
onLoad((query) => {
  if (query.orderNum) load(query.orderNum)
  shipMode.value = query.ship === '1'
})
</script>

<style scoped>
.page {
  padding: 20rpx 24rpx 60rpx;
}

.state-card {
  background: linear-gradient(135deg, #2b6cff 0%, #6ea1ff 100%);
  border-radius: 20rpx;
  padding: 40rpx 30rpx;
  color: #ffffff;
  margin-bottom: 20rpx;
}

.state-big {
  font-size: 40rpx;
  font-weight: 800;
}

.state-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: 0.9;
}

.goods-row {
  display: flex;
  align-items: center;
}

.goods-img {
  width: 130rpx;
  height: 130rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  margin-left: 18rpx;
}

.goods-name {
  font-size: 28rpx;
  font-weight: 600;
}

.goods-num {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a8f99;
}

.goods-total {
  font-size: 30rpx;
  font-weight: 700;
  color: #2b6cff;
}

.info-row {
  display: flex;
  padding: 16rpx 0;
  font-size: 26rpx;
}

.info-label {
  width: 150rpx;
  color: #8a8f99;
  flex-shrink: 0;
}

.addr {
  flex: 1;
}

.mono {
  font-size: 22rpx;
  color: #606266;
  word-break: break-all;
}

.ship-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 18rpx;
}

.ship-input {
  height: 84rpx;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 14rpx;
  padding: 0 26rpx;
  font-size: 28rpx;
}

.ph {
  color: #b0b3b8;
}

.ship-btn {
  margin-top: 24rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 30rpx;
  justify-content: center;
}

.empty {
  text-align: center;
  color: #8a8f99;
  padding: 160rpx 0;
}
</style>
