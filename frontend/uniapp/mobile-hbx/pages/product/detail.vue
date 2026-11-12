<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：商品详情
  界面名称：商品详情（大图 / 价格 / 加入购物车 / 立即购买）
  ============================================
-->
<template>
  <view class="page">
    <!-- 商品大图 -->
    <view class="hero">
      <image class="hero-img" :src="imgSrc(product.image)" mode="aspectFill" />
    </view>

    <view class="card">
      <view class="price-line">
        <text class="price">¥{{ product.price ? product.price.toFixed(2) : '--' }}</text>
        <text class="sales">🔥 销量 {{ product.sales }}</text>
      </view>
      <view class="name">{{ product.name }}</view>
      <view class="desc">{{ product.desc }}</view>
    </view>

    <view class="card info-card">
      <view class="info-row">
        <text class="info-label">分类</text>
        <text>{{ product.category }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">配送</text>
        <text>快递包邮 · 48 小时内发货</text>
      </view>
      <view class="info-row">
        <text class="info-label">售后</text>
        <text>7 天无理由退货 · 支持售后退款</text>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="btn-plain bar-btn" @tap="handleAddCart">加入购物车</view>
      <view class="btn-primary bar-btn" @tap="handleBuyNow">立即购买</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProductById, imgSrc } from '../../common/api'
import { toast } from '../../common/toast'
import { addToCart, isLoggedIn } from '../../common/store'

const product = ref({})
let loaded = false

// 登录校验：未登录跳转登录页，返回是否通过
function checkLogin() {
  if (!isLoggedIn()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return false
  }
  return true
}

// 加入购物车：校验登录后加 1 件
function handleAddCart() {
  if (!checkLogin()) return
  addToCart(product.value, 1)
  toast('已加入购物车')
}

// 立即购买：不进购物车，直接带商品 id 进订单确认页结算
function handleBuyNow() {
  if (!checkLogin()) return
  uni.navigateTo({ url: '/pages/order-confirm/order-confirm?from=buyNow&id=' + product.value.id })
}

// 页面加载：按 id 拉取商品详情并设置导航栏标题
onLoad(async (query) => {
  const res = await getProductById(query.id)
  product.value = res.record || {}
  loaded = true
  uni.setNavigationBarTitle({ title: '商品详情' })
})
</script>

<style scoped>
.page {
  padding-bottom: 160rpx;
}

.hero {
  width: 100%;
  height: 560rpx;
  overflow: hidden;
}

.hero-img {
  width: 100%;
  height: 100%;
}

.price-line {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.price {
  color: #2b6cff;
  font-size: 48rpx;
  font-weight: 800;
}

.sales {
  font-size: 24rpx;
  color: #8a8f99;
}

.name {
  margin-top: 14rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2329;
}

.desc {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #606266;
  line-height: 1.6;
}

.info-card {
  padding: 8rpx 24rpx;
}

.info-row {
  display: flex;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #f2f5fa;
  font-size: 26rpx;
  color: #1f2329;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  width: 120rpx;
  color: #8a8f99;
}

/* 底部操作栏 */
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  display: flex;
  gap: 24rpx;
}

.bar-btn {
  flex: 1;
  height: 84rpx;
  line-height: 82rpx;
  font-size: 30rpx;
}
</style>
