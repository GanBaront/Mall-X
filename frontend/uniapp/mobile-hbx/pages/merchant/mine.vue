<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：账户中心
  界面名称：商家我的页
  ============================================
-->
<template>
  <view class="page">
    <!-- 店铺头 -->
    <view class="user-header">
      <view class="avatar">{{ avatarText }}</view>
      <view class="user-meta">
        <view class="user-name">{{ store.user ? store.user.shopName : '未登录' }}</view>
        <view class="user-desc">{{ store.user ? '商户编号：' + store.user.shopNum : '登录后管理您的店铺' }}</view>
      </view>
    </view>

    <!-- 资金概览 -->
    <view class="card">
      <view class="section-title">经营概览</view>
      <view class="overview-row">
        <view class="ov-item">
          <view class="ov-num primary">¥{{ funds.totalIncome.toFixed(0) }}</view>
          <view class="ov-label">累计营收</view>
        </view>
        <view class="ov-item">
          <view class="ov-num">¥{{ funds.balance.toFixed(0) }}</view>
          <view class="ov-label">可提现余额</view>
        </view>
        <view class="ov-item">
          <view class="ov-num">¥{{ funds.totalServiceFee.toFixed(0) }}</view>
          <view class="ov-label">累计手续费</view>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="card menu-card">
      <view class="menu-item" @tap="goFunds">
        <text class="menu-icon">💰</text>
        <text class="menu-text">资金提现</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goRefund">
        <text class="menu-icon">↩️</text>
        <text class="menu-text">售后退款</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goShop">
        <text class="menu-icon">🏪</text>
        <text class="menu-text">店铺设置</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="showAbout">
        <text class="menu-icon">ℹ️</text>
        <text class="menu-text">关于商家端</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view class="logout-btn" @tap="handleLogout">退出登录</view>
  </view>
  <MTabBar active="mine" @backtop="scrollTop" />
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MTabBar from './MTabBar.vue'
import { getFunds } from '../../common/merchant'
import { store, isLoggedIn, logout } from '../../common/store'

// 经营概览资金数据（累计营收、可提现余额、累计手续费）
const funds = ref({ balance: 0, totalIncome: 0, totalServiceFee: 0 })

// 头像首字（取店铺名首字符）
const avatarText = computed(() => (store.user?.shopName || '店').slice(0, 1))

// 跳转资金提现页
function goFunds() { uni.navigateTo({ url: '/pages/merchant/funds' }) }
// 跳转售后退款页
function goRefund() { uni.navigateTo({ url: '/pages/merchant/refund' }) }
// 跳转店铺设置页（pages.json 注册路径为 /pages/merchant/shop）
function goShop() { uni.navigateTo({ url: '/pages/merchant/shop' }) }

// 双击底部 Tab：页面回到顶端
function scrollTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

// 关于商家端说明弹窗
function showAbout() {
  uni.showModal({
    title: '关于商家端',
    content: 'Mall-X 商家管理中心 v1.0.0\n商品管理、订单发货、售后退款、资金提现、店铺设置。',
    showCancel: false
  })
}

// 退出商家登录（二次确认后清除登录态并跳转登录页）
function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: (res) => {
      if (res.confirm) {
        logout()
        uni.reLaunch({ url: '/pages/login/login' })
      }
    }
  })
}

// 页面显示时校验登录态并加载经营概览
onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  getFunds().then(res => {
    funds.value = {
      balance: Number(res.balance || 0),
      totalIncome: Number(res.totalIncome || 0),
      totalServiceFee: Number(res.totalServiceFee || 0)
    }
  }).catch(() => {})
})
</script>

<style scoped>
.page {
  /* 底部留白须大于固定底栏（MTabBar 约 110rpx + 安全区），否则最后一项被遮挡 */
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}

.user-header {
  display: flex;
  align-items: center;
  padding: 40rpx 30rpx;
  padding-top: calc(var(--status-bar-height) + 40rpx);
  background: linear-gradient(135deg, #2b6cff 0%, #6ea1ff 100%);
  color: #ffffff;
}

.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  font-weight: 700;
}

.user-meta {
  margin-left: 24rpx;
}

.user-name {
  font-size: 34rpx;
  font-weight: 700;
}

.user-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  opacity: 0.9;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 22rpx;
}

.overview-row {
  display: flex;
}

.ov-item {
  flex: 1;
  text-align: center;
}

.ov-num {
  font-size: 34rpx;
  font-weight: 800;
}

.primary { color: #2b6cff; }

.ov-label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a8f99;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 4rpx;
  border-bottom: 2rpx solid #f2f5fa;
}

.menu-item:last-child {
  border-bottom: none;
  padding-bottom: 8rpx;
}

.menu-icon {
  font-size: 36rpx;
  margin-right: 20rpx;
}

.menu-text {
  flex: 1;
  font-size: 28rpx;
}

.menu-arrow {
  color: #c0c4cc;
  font-size: 32rpx;
}

.logout-btn {
  margin: 40rpx 24rpx 0;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: #ffffff;
  color: #e64340;
  border-radius: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
