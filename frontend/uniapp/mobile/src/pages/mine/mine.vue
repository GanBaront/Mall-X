<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：我的 Tab
  界面名称：我的（用户头 / 订单入口 / 菜单）
  ============================================
-->
<template>
  <view class="page">
    <!-- 用户信息头（已登录点击进入编辑资料页） -->
    <view class="user-header" @tap="store.user ? goProfile() : goLogin()">
      <view class="avatar">
        <image v-if="avatarSrc" :src="avatarSrc" class="avatar-img" mode="aspectFill" />
        <text v-else class="avatar-text">{{ avatarText }}</text>
      </view>
      <view class="user-meta">
        <view class="user-name">{{ store.user ? displayName : '未登录' }}</view>
        <view class="user-desc">{{ store.user ? '用户编号：' + store.user.userNum : '登录后享受完整购物体验' }}</view>
      </view>
      <view v-if="store.user" class="edit-hint">编辑资料 ›</view>
      <view v-if="!store.user" class="btn-primary small" @tap.stop="goLogin">登录 / 注册</view>
    </view>

    <!-- 我的订单快捷入口 -->
    <view class="card">
      <view class="section-head">
        <text class="section-title">我的订单</text>
        <text class="link-more" @tap="goOrders(-1)">全部订单 ›</text>
      </view>
      <view class="order-shortcuts">
        <view class="shortcut" @tap="goOrders(0)">
          <text class="shortcut-icon">💳</text>
          <text class="shortcut-text">待支付</text>
        </view>
        <view class="shortcut" @tap="goOrders(1)">
          <text class="shortcut-icon">📦</text>
          <text class="shortcut-text">待发货</text>
        </view>
        <view class="shortcut" @tap="goOrders(2)">
          <text class="shortcut-icon">🚚</text>
          <text class="shortcut-text">待收货</text>
        </view>
        <view class="shortcut" @tap="goOrders(3)">
          <text class="shortcut-icon">✅</text>
          <text class="shortcut-text">已完成</text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="card menu-card">
      <view class="menu-item" @tap="goCoupons">
        <text class="menu-icon">🎫</text>
        <text class="menu-text">我的卡券</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="editAddress">
        <text class="menu-icon">📍</text>
        <text class="menu-text">收货地址</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="showAbout">
        <text class="menu-icon">💬</text>
        <text class="menu-text">关于商城</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <view v-if="store.user" class="logout-btn" @tap="handleLogout">退出登录</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onTabItemTap } from '@dcloudio/uni-app'
import { store, logout, updateProfile } from '../../common/store'
import { toast } from '../../common/toast'
import { getUserInfo, assetUrl } from '../../common/api'

// 头像文字：无头像图时取昵称首字（未登录时回退为「客」）
const avatarText = computed(() => {
  const name = store.user?.nickname || '客'
  return name.slice(0, 1).toUpperCase()
})

// 昵称展示：后端 nickname 为空时回退账号（老账号未设置过昵称）
const displayName = computed(() => store.user?.nickname || store.user?.username)

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

// 头像图片地址：后端存的是 /upload/... 相对路径，按运行端拼接后展示
const avatarSrc = ref('')
function refreshAvatar() {
  avatarSrc.value = assetUrl(store.user?.avatar)
}

// 每次进入本页：拉取最新资料（昵称/头像可能已在此端或别端修改）
onShow(() => {
  if (!store.user) { avatarSrc.value = ''; return }
  refreshAvatar()
  getUserInfo().then((res) => {
    updateProfile({
      nickname: res.nickname || store.user.username,
      avatar: res.avatar || ''
    })
    refreshAvatar()
  }).catch(() => {})
})

// 跳转统一登录页
function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' })
}

// 跳转编辑资料页
function goProfile() {
  uni.navigateTo({ url: '/pages/profile/profile' })
}

// 跳转我的卡券页（转盘抽到的优惠券/现金红包）
function goCoupons() {
  uni.navigateTo({ url: '/pages/coupons/coupons' })
}

// 跳转我的订单页：带上状态筛选（-1=全部，0 待支付 / 1 待发货 / 2 待收货 / 3 已完成）
function goOrders(state) {
  uni.navigateTo({ url: '/pages/orders/orders?state=' + state })
}

// 跳转收货地址列表页
function editAddress() {
  uni.navigateTo({ url: '/pages/address/list' })
}

// 关于商城：弹窗展示版本与平台简介
function showAbout() {
  uni.showModal({
    title: '关于商城',
    content: 'Mall-X 多商家电商平台移动端 v1.0.0\n支持多商户入驻、购物车、在线支付、订单履约。',
    showCancel: false
  })
}

// 退出登录：二次确认后清除登录态并回到登录页
function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        logout()
        toast('已退出登录')
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/login/login' })
        }, 600)
      }
    }
  })
}
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

/* 用户头 */
.user-header {
  display: flex;
  align-items: center;
  padding: 40rpx 30rpx;
  background: linear-gradient(135deg, #2b6cff 0%, #6ea1ff 100%);
  color: #ffffff;
}

.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar-img {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
}

.avatar-text {
  font-size: 48rpx;
  font-weight: 700;
  color: #ffffff;
}

.user-meta {
  flex: 1;
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

.edit-hint {
  font-size: 24rpx;
  opacity: 0.9;
}

.small {
  height: 60rpx;
  line-height: 60rpx;
  font-size: 24rpx;
}

/* 订单快捷入口 */
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 26rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
}

.link-more {
  font-size: 24rpx;
  color: #8a8f99;
}

.order-shortcuts {
  display: flex;
}

.shortcut {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.shortcut-icon {
  font-size: 52rpx;
}

.shortcut-text {
  font-size: 24rpx;
  color: #4e5560;
}

/* 菜单 */
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
  color: #1f2329;
}

.menu-arrow {
  color: #c0c4cc;
  font-size: 32rpx;
}

/* 退出登录 */
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
