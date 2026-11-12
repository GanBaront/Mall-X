<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：商品管理
  界面名称：商品管理列表页
  ============================================
-->
<template>
  <view class="page">
    <!-- 搜索 + 新增 -->
    <view class="toolbar">
      <view class="search-bar">
        <input v-model="keyword" class="search-input" placeholder="搜索商品名称/描述" placeholder-class="ph" confirm-type="search" @confirm="load" />
        <view class="btn-primary search-btn" @tap="load">搜索</view>
      </view>
      <view class="btn-primary add-btn" @tap="goEdit()">+ 新增商品</view>
    </view>

    <!-- 商品列表 -->
    <view v-if="goodsList.length > 0">
      <view v-for="g in goodsList" :key="g.id" class="goods-card">
        <image class="goods-img" :src="imgSrc(g.image)" mode="aspectFill" />
        <view class="goods-info">
          <view class="goods-name">{{ g.name }}</view>
          <view class="goods-meta">
            <text class="goods-price">¥{{ g.price.toFixed(2) }}</text>
            <text class="goods-sales">销量 {{ g.sales }}</text>
          </view>
          <view class="goods-stock" :class="{ danger: g.stock === 0 }">
            库存：{{ g.stock === 0 ? '缺货' : g.stock }}
          </view>
        </view>
        <!-- 操作按钮组：上/下架开关、编辑、删除 -->
        <view class="goods-ops">
          <view class="state-switch" @tap="toggleState(g)">
            <view class="switch-track" :class="{ on: g.state === 1 }">
              <view class="switch-dot" />
            </view>
            <text class="switch-text">{{ g.state === 1 ? '上架' : '下架' }}</text>
          </view>
          <view class="op-row">
            <view class="btn-plain tiny" @tap="goEdit(g)">编辑</view>
            <view class="btn-danger-plain tiny" @tap="handleDelete(g)">删除</view>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty">📦 暂无商品，点击上方按钮新增</view>
  </view>
  <MTabBar active="goods" @backtop="scrollTop" />
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MTabBar from './MTabBar.vue'
import { getMerchantGoods, saveMerchantGoods, deleteMerchantGoods } from '../../common/merchant'
import { toast } from '../../common/toast'
import { imgSrc } from '../../common/api'
import { isLoggedIn } from '../../common/store'

// 商家商品列表
const goodsList = ref([])

// 搜索关键字（商品名称/描述）
const keyword = ref('')

// 加载商家商品列表（价格字段转数字，带关键字筛选）
async function load() {
  try {
    const res = await getMerchantGoods(keyword.value.trim())
    goodsList.value = (res.records || []).map(g => ({ ...g, price: Number(g.price || 0) }))
  } catch (e) {
    uni.showToast({ title: e.message || '商品加载失败', icon: 'none' })
  }
}

// 双击底部 Tab：页面回到顶端
function scrollTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

// 商品上/下架切换（二次确认后调用保存接口，并同步本地状态）
function toggleState(g) {
  const action = g.state === 1 ? '下架' : '上架'
  uni.showModal({
    title: '提示',
    content: `确定${action}商品「${g.name}」？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await saveMerchantGoods({ id: g.id, state: g.state === 1 ? 0 : 1 })
          g.state = g.state === 1 ? 0 : 1
          toast(`已${action}`)
        } catch (e) {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }
      }
    }
  })
}

// 删除商品（二次确认后调用后端删除接口，真实删除并刷新列表）
function handleDelete(g) {
  uni.showModal({
    title: '删除商品',
    content: `确定删除「${g.name}」？删除后不可恢复。`,
    confirmColor: '#e64340',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteMerchantGoods(g.id)
          goodsList.value = goodsList.value.filter(x => x.id !== g.id)
          toast('已删除')
        } catch (e) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    }
  })
}

// 跳转新增/编辑页（传入商品对象则为编辑，带 id 参数）
function goEdit(g) {
  uni.navigateTo({ url: '/pages/merchant/goods-edit' + (g ? '?id=' + g.id : '') })
}

// 页面显示时校验登录态并加载商品列表
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
  padding: 20rpx 24rpx calc(160rpx + env(safe-area-inset-bottom));
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

/* 搜索条 */
.search-bar {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.search-input {
  flex: 1;
  height: 68rpx;
  background: #ffffff;
  border-radius: 12rpx;
  border: 2rpx solid #e4e8ef;
  padding: 0 20rpx;
  font-size: 26rpx;
  color: #1f2329;
}

.search-btn {
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 30rpx;
  font-size: 26rpx;
  flex-shrink: 0;
}

.add-btn {
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 40rpx;
  flex-shrink: 0;
}

.goods-card {
  display: flex;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 20rpx;
  margin-bottom: 18rpx;
}

.goods-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 14rpx;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.goods-name {
  font-size: 28rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-meta {
  margin-top: 10rpx;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.goods-price {
  color: #2b6cff;
  font-size: 32rpx;
  font-weight: 700;
}

.goods-sales {
  font-size: 22rpx;
  color: #8a8f99;
}

.goods-stock {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8a8f99;
}

.goods-stock.danger {
  color: #e64340;
}

.goods-ops {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  margin-left: 16rpx;
}

.state-switch {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.switch-track {
  width: 76rpx;
  height: 40rpx;
  border-radius: 20rpx;
  background: #d6dbe4;
  padding: 4rpx;
  box-sizing: border-box;
  display: flex;
}

.switch-track.on {
  background: #2b6cff;
  justify-content: flex-end;
}

.switch-dot {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: #ffffff;
}

.switch-text {
  font-size: 20rpx;
  color: #606266;
}

.op-row {
  display: flex;
  gap: 12rpx;
}

.tiny {
  height: 52rpx;
  line-height: 50rpx;
  font-size: 22rpx;
  padding: 0 24rpx;
}

.empty {
  text-align: center;
  color: #8a8f99;
  padding: 160rpx 0;
}
</style>
