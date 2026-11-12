<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：购物车 Tab
  界面名称：购物车（全选 / 列表 / 结算栏）
  ============================================
-->
<template>
  <view class="page">
    <!-- 全选 -->
    <view class="check-all" @tap="toggleCheckedAll">
      <view class="checkbox" :class="{ checked: isAllChecked }">
        <text v-if="isAllChecked" class="check-mark">✓</text>
      </view>
      <text class="check-all-text">全选</text>
    </view>

    <!-- 购物车列表 -->
    <view v-if="store.cart.length > 0" class="cart-list">
      <view v-for="item in store.cart" :key="item.id" class="cart-row">
        <view class="checkbox" :class="{ checked: item.checked }" @tap="toggleChecked(item.id)">
          <text v-if="item.checked" class="check-mark">✓</text>
        </view>

        <view class="goods-thumb row-thumb">
          <image class="row-thumb-img" :src="imgSrc(item.image)" mode="aspectFill" />
        </view>

        <view class="row-info">
          <view class="row-name">{{ item.name }}</view>
          <view class="row-price">¥{{ item.price.toFixed(2) }}</view>
          <view class="row-bottom">
            <view class="stepper">
              <view class="step-btn" @tap="changeNum(item, -1)">−</view>
              <view class="step-num">{{ item.num }}</view>
              <view class="step-btn plus" @tap="changeNum(item, 1)">+</view>
            </view>
            <view class="btn-danger-plain" @tap="handleRemove(item)">删除</view>
          </view>
        </view>

        <view class="row-subtotal">
          <text class="subtotal-label">小计</text>
          <text class="subtotal-value">¥{{ (item.price * item.num).toFixed(2) }}</text>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <text class="empty-icon">🛒</text>
      <text>购物车还是空的，去逛逛吧</text>
    </view>

    <!-- 底部结算栏 -->
    <view v-if="store.cart.length > 0" class="bottom-bar">
      <view class="total-part">
        <text class="total-label">合计：</text>
        <text class="total-value">¥{{ checkedTotal.toFixed(2) }}</text>
      </view>
      <view class="bar-actions">
        <view class="btn-plain" @tap="handleClear">清空购物车</view>
        <view class="btn-primary" @tap="goSettle">去结算</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { onShow, onTabItemTap } from '@dcloudio/uni-app'
import {
  store,
  updateCartNum,
  removeFromCart,
  toggleChecked,
  toggleCheckedAll,
  clearCart,
  checkedTotal,
  checkedItems
} from '../../common/store'
import { imgSrc } from '../../common/api'

// 是否全选：购物车非空且每个商品都勾选时为 true
const isAllChecked = computed(() => store.cart.length > 0 && store.cart.every(i => i.checked))

/* 双击底部 Tab 回到页面顶端 */
let lastTabTap = 0
onTabItemTap(() => {
  const now = Date.now()
  if (now - lastTabTap < 400) {
    uni.pageScrollTo({ scrollTop: 0, duration: 200 })
    lastTabTap = 0
  } else {
    lastTabTap = now
  }
})

// 调整商品数量：delta 为 +1/-1，数量至少为 1
function changeNum(item, delta) {
  const n = item.num + delta
  if (n < 1) return
  updateCartNum(item.id, n)
}

// 删除单个商品：弹窗二次确认后移除
function handleRemove(item) {
  uni.showModal({
    title: '提示',
    content: `确定删除「${item.name}」吗？`,
    success: (res) => {
      if (res.confirm) removeFromCart(item.id)
    }
  })
}

// 清空购物车：弹窗二次确认后清空（空车直接返回）
function handleClear() {
  if (store.cart.length === 0) return
  uni.showModal({
    title: '提示',
    content: '确定清空购物车吗？',
    success: (res) => {
      if (res.confirm) clearCart()
    }
  })
}

// 去结算：先校验是否勾选商品，再进入订单确认页
function goSettle() {
  if (checkedItems.value.length === 0) {
    uni.showToast({ title: '请先勾选商品', icon: 'none' })
    return
  }
  uni.navigateTo({ url: '/pages/order-confirm/order-confirm?from=cart' })
}

onShow(() => {})
</script>

<style scoped>
.page {
  padding: 24rpx 24rpx 160rpx;
}

/* 全选 */
.check-all {
  display: inline-flex;
  align-items: center;
  background: #ffffff;
  border-radius: 999rpx;
  padding: 14rpx 30rpx;
  margin-bottom: 20rpx;
}

.check-all-text {
  margin-left: 14rpx;
  font-size: 26rpx;
  color: #1f2329;
}

/* 复选框 */
.checkbox {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #c6cdd8;
  border-radius: 8rpx;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.checkbox.checked {
  background: #2b6cff;
  border-color: #2b6cff;
}

.check-mark {
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

/* 列表 */
.cart-row {
  display: flex;
  align-items: flex-start;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 18rpx;
  gap: 18rpx;
}

.row-thumb {
  width: 130rpx;
  height: 130rpx;
  border-radius: 14rpx;
  flex-shrink: 0;
  overflow: hidden;
}

.row-thumb-img {
  width: 100%;
  height: 100%;
}

.row-info {
  flex: 1;
  min-width: 0;
}

.row-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-price {
  color: #2b6cff;
  font-size: 30rpx;
  font-weight: 700;
  margin-top: 8rpx;
}

.row-bottom {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 步进器 */
.stepper {
  display: flex;
  align-items: center;
  border: 2rpx solid #dbe4f5;
  border-radius: 999rpx;
  overflow: hidden;
}

.step-btn {
  width: 52rpx;
  height: 52rpx;
  line-height: 50rpx;
  text-align: center;
  font-size: 30rpx;
  color: #2b6cff;
  background: #f4f8ff;
}

.step-btn.plus {
  background: #2b6cff;
  color: #ffffff;
}

.step-num {
  min-width: 60rpx;
  text-align: center;
  font-size: 26rpx;
}

/* 小计 */
.row-subtotal {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
  flex-shrink: 0;
}

.subtotal-label {
  font-size: 22rpx;
  color: #8a8f99;
}

.subtotal-value {
  color: #1f2329;
  font-size: 28rpx;
  font-weight: 700;
}

/* 空购物车 */
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

/* 底部结算栏 */
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  border-top: 2rpx dashed #dfe5ef;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.total-part {
  display: flex;
  align-items: baseline;
}

.total-label {
  font-size: 28rpx;
  color: #1f2329;
}

.total-value {
  color: #2b6cff;
  font-size: 40rpx;
  font-weight: 800;
}

.bar-actions {
  display: flex;
  gap: 18rpx;
}
</style>
