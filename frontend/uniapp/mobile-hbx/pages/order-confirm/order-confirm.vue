<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：下单流程
  界面名称：订单确认（地址 / 商品明细 / 支付方式）
  ============================================
-->
<template>
  <view class="page">
    <!-- 收货地址 -->
    <view class="address-card" @tap="editAddress">
      <view v-if="store.address && store.address.detail" class="addr-info">
        <view class="addr-line1">
          <text class="addr-name">{{ store.address.name }}</text>
          <text class="addr-phone">{{ store.address.phone }}</text>
        </view>
        <view class="addr-detail">{{ store.address.detail }}</view>
      </view>
      <view v-else class="addr-info">
        <view class="addr-detail">＋ 请先添加收货地址</view>
      </view>
      <view class="addr-edit">编辑</view>
    </view>

    <!-- 商品明细 -->
    <view class="card">
      <view class="table-head">
        <text class="col-name">商品名称</text>
        <text class="col-num">数量</text>
        <text class="col-price">单价</text>
        <text class="col-subtotal">小计</text>
      </view>
      <view v-for="i in items" :key="i.id" class="table-row">
        <text class="col-name name-text">{{ i.name }}</text>
        <view class="col-num">
          <!-- 立即购买：数量可在本页调整；购物车结算：数量在购物车里改，这里只展示 -->
          <view v-if="from === 'buyNow'" class="num-stepper">
            <view class="step-btn" @tap="changeBuyNum(i, -1)">−</view>
            <text class="step-num">{{ i.num }}</text>
            <view class="step-btn" @tap="changeBuyNum(i, 1)">＋</view>
          </view>
          <text v-else>{{ i.num }}件</text>
        </view>
        <text class="col-price">¥{{ i.price.toFixed(2) }}</text>
        <text class="col-subtotal strong">¥{{ (i.price * i.num).toFixed(2) }}</text>
      </view>
    </view>

    <!-- 订单金额 -->
    <view class="amount-bar">
      <view class="amount-rows">
        <view class="amount-row">
          <text class="amount-label">订单金额</text>
          <text class="amount-value">¥{{ total.toFixed(2) }}</text>
        </view>
        <view v-if="discountAmount > 0" class="amount-row discount">
          <text class="amount-label">券已抵扣</text>
          <text class="discount-value">-¥{{ discountAmount.toFixed(2) }}</text>
        </view>
        <view class="amount-row">
          <text class="amount-label strong-label">应付金额</text>
          <text class="amount-value pay">¥{{ payAmount.toFixed(2) }}</text>
        </view>
      </view>
    </view>

    <!-- 优惠券选择 -->
    <view class="card coupon-card">
      <view class="coupon-row" @tap="showPicker = !showPicker">
        <text class="coupon-row-label">优惠券 / 红包</text>
        <view class="coupon-row-right">
          <text v-if="selectedCoupon" class="coupon-chosen">{{ selectedCoupon.name }}</text>
          <text v-else-if="coupons.length" class="coupon-avail">{{ coupons.length }} 张可用</text>
          <text v-else class="coupon-avail none">暂无可用卡券</text>
          <text class="coupon-arrow">{{ showPicker ? '▴' : '▾' }}</text>
        </view>
      </view>
      <view v-if="showPicker" class="coupon-picker">
        <view
          class="picker-item"
          :class="{ active: !selectedCouponId }"
          @tap="chooseCoupon(null)"
        >
          <text class="picker-name">不使用优惠券</text>
          <text class="picker-check">{{ !selectedCouponId ? '✓' : '' }}</text>
        </view>
        <view
          v-for="c in coupons"
          :key="c.id"
          class="picker-item"
          :class="{ active: selectedCouponId === c.id }"
          @tap="chooseCoupon(c.id)"
        >
          <text class="picker-name">{{ c.name }}（{{ Number(c.type) === 2 ? '抵扣 ¥' + Number(c.amount).toFixed(2) : '按 ' + discountRateText(c.discountRate) + ' 折结算' }}）</text>
          <text class="picker-check">{{ selectedCouponId === c.id ? '✓' : '' }}</text>
        </view>
      </view>
    </view>

    <!-- 支付方式（后端实时返回可用项：方式启用 且 所属支付接口启用） -->
    <view class="card pay-card">
      <view class="pay-title">支付方式</view>
      <radio-group v-if="payWays.length" class="pay-group" @change="onPayWayChange">
        <label class="pay-item" v-for="w in payWays" :key="w.wayCode">
          <radio :value="w.wayCode" :checked="payWay === w.wayCode" color="#2B6CFF" style="transform: scale(0.8)" />
          <text>{{ w.name }}</text>
        </label>
      </radio-group>
      <view v-else class="pay-empty">暂无可用支付方式，请联系平台管理员</view>
    </view>

    <!-- 底部操作 -->
    <view class="bottom-actions">
      <view class="btn-primary submit-btn" @tap="submitOrder">提交订单</view>
      <view class="btn-plain cancel-btn" @tap="cancel">取消</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createOrder, openPayUrl, usableCoupons, couponDiscountOf, getProductById, getPayWays } from '../../common/api'
import {
  store,
  checkedItems,
  checkedTotal,
  createLocalOrder,
  removeCartItems
} from '../../common/store'

// 可用支付方式：从后端动态加载（管理员停用支付接口/支付方式后此处即时生效）
const payWays = ref([])

// 当前选中的支付方式（默认取第一个可用项；列表为空则禁止提交）
const payWay = ref('')
// 待结算商品列表
const items = ref([])
// 订单合计金额
const total = ref(0)
// 来源：cart=购物车结算，buyNow=立即购买
let from = 'cart'

onLoad((query) => {
  from = query.from || 'cart'
  if (from === 'buyNow' && query.id) {
    // 立即购买：不经过购物车，直接按商品 id 拉详情结算 1 件
    loadBuyNowGoods(query.id)
  } else {
    // 购物车结算：取本地已勾选商品及其合计
    items.value = checkedItems.value.map(i => ({ ...i }))
    total.value = checkedTotal.value
  }
  // 拉取可用支付方式（默认选中第一个；接口失败必须提示）
  getPayWays().then((records) => {
    payWays.value = records
    if (records.length && !records.some(w => w.wayCode === payWay.value)) {
      payWay.value = records[0].wayCode
    }
  }).catch((e) => {
    uni.showToast({ title: e.message || '支付方式加载失败', icon: 'none' })
  })
  // 拉取我的未使用卡券（未登录跳过；失败必须提示，否则用户误以为无可用券）
  if (store.user) {
    usableCoupons().then((records) => {
      coupons.value = records
    }).catch((e) => {
      uni.showToast({ title: e.message || '卡券加载失败', icon: 'none' })
    })
  }
})

// 立即购买：按 id 拉取商品详情（价格后端为权威数据），结算 1 件
async function loadBuyNowGoods(id) {
  try {
    const res = await getProductById(id)
    const p = res.record
    if (!p || !p.name) throw new Error('商品不存在或已下架')
    items.value = [{ id: p.id, name: p.name, price: Number(p.price || 0), image: p.image || '', num: 1 }]
    total.value = items.value[0].price
  } catch (e) {
    uni.showToast({ title: e.message || '商品加载失败', icon: 'none' })
  }
}

// 立即购买：调整购买数量（至少 1 件），金额联动刷新
function changeBuyNum(item, delta) {
  const n = item.num + delta
  if (n < 1) return
  item.num = n
  total.value = items.value.reduce((s, i) => s + i.price * i.num, 0)
}

/* ==================== 优惠券选择 ==================== */
const coupons = ref([])          // 我的未使用卡券
const selectedCouponId = ref(null) // 选中的券 id（null=不使用）
const showPicker = ref(false)    // 券选择列表展开状态

const selectedCoupon = computed(() =>
  coupons.value.find(c => String(c.id) === String(selectedCouponId.value)) || null
)

// 券抵扣金额（与后端 PayService 同口径）：现金红包直减、折扣券按折扣率打折
const discountAmount = computed(() => couponDiscountOf(selectedCoupon.value, total.value))

// 应付金额 = 订单金额 - 券抵扣（最低 0.01 元，与后端一致）
const payAmount = computed(() => Math.max(total.value - discountAmount.value, 0.01))

function chooseCoupon(id) {
  selectedCouponId.value = id
  showPicker.value = false
}

function discountRateText(rate) {
  return (Math.round(Number(rate || 1) * 1000) / 100).toString()
}

// 切换支付方式
function onPayWayChange(e) {
  payWay.value = e.detail.value
}

// 跳转收货地址列表，选择/编辑地址
function editAddress() {
  uni.navigateTo({ url: '/pages/address/list' })
}

// 提交订单：校验商品/地址/登录态后调后端下单，成功后本地缓存并引导去收银台
function submitOrder() {
  if (items.value.length === 0) {
    uni.showToast({ title: '没有可结算的商品', icon: 'none' })
    return
  }
  if (!store.address || !store.address.detail) {
    uni.showModal({
      title: '缺少收货地址',
      content: '请先添加收货地址后再提交订单',
      confirmText: '去添加',
      success: (m) => { if (m.confirm) uni.navigateTo({ url: '/pages/address/list' }) }
    })
    return
  }
  if (!payWays.value.length || !payWay.value) {
    uni.showToast({ title: '暂无可用支付方式', icon: 'none' })
    return
  }
  if (!store.user) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  uni.showLoading({ title: '提交中...' })
  const num = items.value.reduce((s, i) => s + i.num, 0)
  const payload = {
    wayCode: payWay.value,
    payMoney: total.value.toFixed(2), // 传商品原价，券抵扣由后端统一计算并核销
    subject: items.value[0].name,
    num
  }
  // 多商品合并结算必须传 items 明细：后端按「商品名精确匹配 + 服务端逐项计价」强校验，
  // 仅传第一件商品名+总件数会因金额对不上被拒（单商品路径仍走 subject/num）
  if (items.value.length > 1) {
    payload.items = items.value.map(i => ({ name: i.name, num: i.num }))
  }
  if (selectedCouponId.value) payload.couponId = selectedCouponId.value
  createOrder(payload).then((res) => {
    // 本地缓存一笔待支付订单（金额为券后应付），支付结果以「我的订单」页从后端刷新为准
    const order = createLocalOrder({ items: items.value, total: payAmount.value, payWay: payWay.value, payOrderId: res.payOrderId, orderNum: res.mchOrderNo })
    // 购物车结算：下单成功即把本次结算的商品从购物车移除（订单可在「我的订单」里继续支付）
    if (from === 'cart') {
      removeCartItems(items.value.map(i => i.id))
    }
    uni.hideLoading()
    uni.showModal({
      title: '下单成功',
      content: '订单号 ' + res.mchOrderNo + '，是否前往收银台完成支付？',
      confirmText: '去支付',
      success: (m) => {
        // 先用 redirectTo 跳到我的订单（替换掉订单确认页，之后点返回不会再回到本页），再拉起收银台
        uni.redirectTo({ url: '/pages/orders/orders' })
        if (m.confirm && res.payUrl) {
          setTimeout(() => openPayUrl(res.payUrl), 300)
        }
      }
    })
  }).catch((e) => {
    uni.hideLoading()
    uni.showToast({ title: e.message || '下单失败', icon: 'none' })
  })
}

// 取消下单，返回上一页
function cancel() {
  uni.navigateBack()
}
</script>

<style scoped>
.page {
  padding: 24rpx 24rpx 180rpx;
}

/* 地址卡 */
.address-card {
  display: flex;
  align-items: center;
  background: #eaf1ff;
  border-radius: 20rpx;
  padding: 28rpx 26rpx;
}

.addr-info {
  flex: 1;
  min-width: 0;
}

.addr-line1 {
  display: flex;
  gap: 24rpx;
  align-items: baseline;
}

.addr-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2329;
}

.addr-phone {
  font-size: 26rpx;
  color: #4e5560;
}

.addr-detail {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #4e5560;
}

.addr-edit {
  background: #ffffff;
  color: #2b6cff;
  border: 2rpx solid #b9cffb;
  border-radius: 999rpx;
  font-size: 24rpx;
  padding: 10rpx 30rpx;
  flex-shrink: 0;
}

/* 商品明细表 */
.table-head,
.table-row {
  display: flex;
  align-items: center;
}

.table-head {
  background: #eef3ff;
  border-radius: 12rpx;
  padding: 18rpx 20rpx;
  font-size: 24rpx;
  color: #4e5560;
  font-weight: 600;
}

.table-row {
  padding: 22rpx 20rpx;
  border-bottom: 2rpx solid #f2f5fa;
  font-size: 26rpx;
  color: #1f2329;
}

.table-row:last-child {
  border-bottom: none;
}

.col-name {
  flex: 2.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name-text {
  font-weight: 600;
}

.col-num {
  flex: 0.8;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 立即购买数量步进器 */
.num-stepper {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.step-btn {
  width: 44rpx;
  height: 44rpx;
  line-height: 40rpx;
  text-align: center;
  border: 2rpx solid #d4dbe8;
  border-radius: 10rpx;
  font-size: 28rpx;
  color: #4e5560;
  background: #ffffff;
}

.step-num {
  min-width: 40rpx;
  text-align: center;
  font-size: 26rpx;
  font-weight: 600;
  color: #1f2329;
}

.col-price {
  flex: 1;
  text-align: center;
}

.col-subtotal {
  flex: 1;
  text-align: right;
}

.strong {
  font-weight: 700;
}

/* 订单金额（多行：订单金额 / 券抵扣 / 应付金额） */
.amount-bar {
  background: #eaf1ff;
  border-radius: 20rpx;
  padding: 26rpx 30rpx;
  margin: 20rpx 0;
}

.amount-rows {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.amount-row {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 24rpx;
}

.amount-label {
  font-size: 28rpx;
  color: #1f2329;
}

.strong-label {
  font-weight: 700;
}

.discount-row .amount-label {
  color: #e64340;
}

.amount-row.discount .amount-label {
  color: #e64340;
}

.discount-value {
  color: #e64340;
  font-size: 28rpx;
  font-weight: 700;
}

.amount-value {
  color: #2b6cff;
  font-size: 34rpx;
  font-weight: 700;
}

.amount-value.pay {
  color: #2b6cff;
  font-size: 44rpx;
  font-weight: 800;
}

/* 优惠券选择 */
.coupon-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.coupon-row-label {
  font-size: 28rpx;
  font-weight: 700;
}

.coupon-row-right {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.coupon-chosen {
  font-size: 26rpx;
  color: #e64340;
  font-weight: 700;
}

.coupon-avail {
  font-size: 26rpx;
  color: #4e5560;
}

.coupon-avail.none {
  color: #b0b3b8;
}

.coupon-arrow {
  font-size: 24rpx;
  color: #b0b3b8;
}

.coupon-picker {
  margin-top: 20rpx;
  border-top: 2rpx solid #f2f5fa;
}

.picker-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22rpx 6rpx;
  border-bottom: 2rpx solid #f5f7fb;
  font-size: 26rpx;
  color: #1f2329;
}

.picker-item:last-child {
  border-bottom: none;
}

.picker-item.active {
  color: #2b6cff;
  font-weight: 700;
}

.picker-check {
  width: 40rpx;
  text-align: center;
  color: #2b6cff;
}

/* 支付方式 */
.pay-title {
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.pay-group {
  display: flex;
  gap: 40rpx;
}

.pay-empty {
  font-size: 26rpx;
  color: #b0b3b8;
}

.pay-item {
  display: flex;
  align-items: center;
  font-size: 26rpx;
  color: #1f2329;
}

/* 底部按钮 */
.bottom-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  padding: 20rpx 40rpx calc(20rpx + env(safe-area-inset-bottom));
  display: flex;
  justify-content: center;
  gap: 30rpx;
}

.submit-btn {
  min-width: 280rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 30rpx;
}

.cancel-btn {
  min-width: 200rpx;
  height: 80rpx;
  line-height: 78rpx;
  font-size: 30rpx;
  color: #4e5560;
  border-color: #d4dbe8;
}
</style>
