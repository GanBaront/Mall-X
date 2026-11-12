<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：收货地址
  界面名称：收货地址列表（管理 / 设为默认 / 删除）
  ============================================
-->
<template>
  <view class="page">
    <!-- 自定义导航栏：返回 + 标题 + 管理/新增地址 -->
    <view class="navbar">
      <view class="nav-left" @tap="goBack">
        <text class="nav-back">←</text>
        <text class="nav-title">收货地址</text>
      </view>
      <view class="nav-right">
        <text class="nav-action" @tap="toggleManage">{{ manageMode ? '完成' : '管理' }}</text>
        <text class="nav-action accent" @tap="goAdd">新增地址</text>
      </view>
    </view>

    <!-- 地址列表 -->
    <view v-if="store.addressList.length > 0" class="addr-list">
      <view v-for="a in store.addressList" :key="a.id" class="addr-card" :class="{ def: a.isDefault }" @tap="goEdit(a)">
        <view class="addr-tags">
          <text v-if="a.tag" class="addr-tag">{{ a.tag }}</text>
          <text v-if="a.isDefault" class="addr-tag default">默认</text>
        </view>
        <view class="addr-line1">
          <text class="addr-name">{{ a.name }} {{ a.gender }}</text>
          <text class="addr-phone">{{ a.phone }}</text>
        </view>
        <view class="addr-detail">{{ a.detail }}</view>
        <view class="addr-ops">
          <template v-if="manageMode">
            <view class="op-btn danger" @tap.stop="handleDelete(a)">
              <text class="op-icon">🗑</text>
              <text>删除</text>
            </view>
          </template>
          <template v-else>
            <view v-if="!a.isDefault" class="op-btn" @tap.stop="handleSetDefault(a)">
              <text class="op-icon">✓</text>
              <text>设为默认</text>
            </view>
            <view v-else class="op-btn is-default">
              <text class="op-icon">★</text>
              <text>默认地址</text>
            </view>
            <view class="op-btn" @tap.stop="goEdit(a)">
              <text class="op-icon">✎</text>
              <text>编辑</text>
            </view>
          </template>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-else class="empty">
      <text class="empty-icon">📍</text>
      <text class="empty-text">还没有收货地址</text>
      <view class="empty-btn" @tap="goAdd">＋ 新增地址</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { store, setDefaultAddress, removeAddress } from '../../common/store'
import { toast } from '../../common/toast'

// 是否处于「管理」模式（管理模式下显示删除按钮）
const manageMode = ref(false)

// 返回：有上一页则返回，否则回到首页
function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.reLaunch({ url: '/pages/index/index' })
}

// 切换管理/完成模式
function toggleManage() {
  manageMode.value = !manageMode.value
}

// 新增地址：跳转地址编辑页
function goAdd() {
  uni.navigateTo({ url: '/pages/address/edit' })
}

// 编辑地址：带 id 跳转地址编辑页
function goEdit(a) {
  uni.navigateTo({ url: '/pages/address/edit?id=' + a.id })
}

// 设为默认地址
function handleSetDefault(a) {
  setDefaultAddress(a.id)
  toast('已设为默认地址')
}

// 删除地址：二次确认后移除，并退出空列表的管理模式
function handleDelete(a) {
  uni.showModal({
    title: '删除地址',
    content: `确定删除「${a.name}」的收货地址？`,
    confirmColor: '#e64340',
    success: (res) => {
      if (res.confirm) {
        removeAddress(a.id)
        toast('已删除')
        if (!store.addressList.length) manageMode.value = false
      }
    }
  })
}

onShow(() => {})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding-bottom: 60rpx;
}

/* 自定义导航栏：返回键 + 标题 + 右侧操作（padding-top 用状态栏高度，避免真机上与系统状态栏重叠） */
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  padding: 20rpx 30rpx 20rpx;
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

.nav-right {
  display: flex;
  align-items: center;
  gap: 30rpx;
}

.nav-action {
  font-size: 27rpx;
  color: #4e5560;
}

.nav-action.accent {
  color: #2b6cff;
  font-weight: 600;
}

/* 地址卡片（不顶格，左右留边距） */
.addr-list {
  padding: 24rpx;
}

.addr-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 26rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid transparent;
  box-shadow: 0 2rpx 10rpx rgba(43, 108, 255, 0.05);
}

.addr-card.def {
  border-color: #2b6cff;
}

.addr-tags {
  display: flex;
  gap: 10rpx;
  min-height: 20rpx;
}

.addr-tag {
  font-size: 20rpx;
  color: #2b6cff;
  background: #eaf1ff;
  border-radius: 6rpx;
  padding: 2rpx 12rpx;
}

.addr-tag.default {
  color: #ffffff;
  background: #2b6cff;
  font-weight: 600;
}

.addr-line1 {
  margin-top: 14rpx;
  display: flex;
  align-items: baseline;
  gap: 20rpx;
}

.addr-name {
  font-size: 30rpx;
  font-weight: 700;
}

.addr-phone {
  font-size: 26rpx;
  color: #8a8f99;
}

.addr-detail {
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #4e5560;
  line-height: 1.5;
}

/* 操作按钮 */
.addr-ops {
  margin-top: 22rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid #f2f4f8;
  display: flex;
  justify-content: flex-end;
  gap: 34rpx;
}

.op-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  font-size: 24rpx;
  color: #606266;
}

.op-btn.is-default {
  color: #2b6cff;
  font-weight: 600;
}

.op-btn.danger {
  color: #e64340;
}

.op-icon {
  font-size: 24rpx;
}

/* 空状态 */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 200rpx 0 0;
}

.empty-icon {
  font-size: 90rpx;
}

.empty-text {
  margin-top: 24rpx;
  font-size: 28rpx;
  color: #8a8f99;
}

.empty-btn {
  margin-top: 40rpx;
  background: #2b6cff;
  color: #ffffff;
  border-radius: 999rpx;
  padding: 18rpx 60rpx;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
