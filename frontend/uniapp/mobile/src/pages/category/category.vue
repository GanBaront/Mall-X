<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：分类 Tab
  界面名称：分类（左侧分类导航 + 右侧商品列表）
  ============================================
-->
<template>
  <view class="page">
    <!-- 左侧分类导航 -->
    <scroll-view scroll-y class="side-nav">
      <view
        v-for="c in categories"
        :key="c"
        class="nav-item"
        :class="{ active: currentCategory === c }"
        @tap="switchCategory(c)"
      >{{ c }}</view>
    </scroll-view>

    <!-- 右侧商品列表 -->
    <scroll-view scroll-y class="goods-panel" :scroll-top="panelTop" @scrolltolower="loadMore">
      <view class="panel-title">{{ currentCategory === '全部' ? '全部商品' : currentCategory }}</view>
      <view
        v-for="p in products"
        :key="p.id"
        class="goods-row"
        @tap="goDetail(p)"
      >
        <view class="goods-thumb row-thumb">
          <image class="row-thumb-img" :src="imgSrc(p.image)" mode="aspectFill" />
        </view>
        <view class="row-info">
          <view class="row-name">{{ p.name }}</view>
          <view class="row-sales">🔥 销量 {{ p.sales }}</view>
          <view class="row-bottom">
            <view class="row-price">¥{{ p.price.toFixed(2) }}</view>
            <view class="btn-primary tiny" @tap.stop="handleAddCart(p)">加购</view>
          </view>
        </view>
      </view>
      <view v-if="!loading && products.length === 0" class="empty">该分类暂无商品</view>

      <!-- 加载更多（点击或滚动到底部触发） -->
      <view v-if="products.length" class="load-more" @tap="loadMore">
        <text v-if="loadingMore" class="load-hint">正在加载...</text>
        <text v-else-if="hasMore" class="load-hint clickable">点击加载更多</text>
        <text v-else class="load-hint">— 已加载全部 —</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onTabItemTap } from '@dcloudio/uni-app'
import { getCategoryTree, getProducts, imgSrc } from '../../common/api'
import { toast } from '../../common/toast'
import { addToCart, isLoggedIn } from '../../common/store'

// 左侧分类导航：从后端类目树动态加载（一级类目，与管理员端同源），首个固定为「全部」
const categories = ref(['全部'])
const currentCategory = ref('全部')
const products = ref([])
const loading = ref(false)

// 拉取启用中的一级类目作为左侧导航；后端按类目子树过滤商品（一级类目带出全部二级类目商品）
async function loadCategories() {
  try {
    const roots = await getCategoryTree()
    categories.value = ['全部', ...roots.map(r => r.name)]
  } catch (e) {
    uni.showToast({ title: e.message || '分类加载失败', icon: 'none' })
  }
}
loadCategories()

/* 分页状态：滚动到底部或点击「加载更多」追加下一页 */
const PAGE_SIZE = 20
const pageNum = ref(1)
const total = ref(0)
const loadingMore = ref(false)
const hasMore = computed(() => products.value.length < total.value)

// 加载当前分类下的商品列表（分页）：reset=true 从第 1 页重新拉取
// 竞态保护：请求序号丢弃过期响应（快速切分类时旧分类的追加数据不会串进新分类列表）
let requestSeq = 0
async function loadProducts(reset = true) {
  if (reset) {
    loading.value = true
    pageNum.value = 1
  } else {
    if (loadingMore.value || loading.value || !hasMore.value) return
    loadingMore.value = true
    pageNum.value += 1
  }
  const seq = ++requestSeq
  try {
    const res = await getProducts(currentCategory.value, '', pageNum.value, PAGE_SIZE)
    if (seq !== requestSeq) return // 过期响应直接丢弃
    const list = res.records || []
    products.value = reset ? list : products.value.concat(list)
    total.value = res.total || 0
  } catch (e) {
    if (seq === requestSeq && !reset) pageNum.value -= 1 // 追加失败回退页码，可重试
    if (seq === requestSeq) uni.showToast({ title: e.message || '商品加载失败', icon: 'none' })
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

// 滚动到底部/点击加载下一页
function loadMore() {
  loadProducts(false)
}

/* 双击底部 Tab：右侧商品列表回到顶部（scroll-view 需走 scroll-top 绑定） */
const panelTop = ref(0)
let lastTabTap = 0
onTabItemTap(() => {
  const now = Date.now()
  if (now - lastTabTap < 400) {
    panelTop.value = panelTop.value === 0 ? 0.1 : 0 // 在顶部时给微小偏移，保证重复触发
    lastTabTap = 0
  } else {
    lastTabTap = now
  }
})

// 切换左侧分类，重新加载商品
function switchCategory(c) {
  currentCategory.value = c
  loadProducts()
}

// 加入购物车：未登录先跳转登录页，登录后加 1 件
function handleAddCart(p) {
  if (!isLoggedIn()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  addToCart(p, 1)
  toast('已加入购物车')
}

// 跳转商品详情页
function goDetail(p) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + p.id })
}

loadProducts()
</script>

<style scoped>
.page {
  display: flex;
  height: 100vh;
}

/* 左侧分类 */
.side-nav {
  width: 180rpx;
  background: #ffffff;
  height: 100%;
  flex-shrink: 0;
}

.nav-item {
  height: 100rpx;
  line-height: 100rpx;
  text-align: center;
  font-size: 26rpx;
  color: #4e5560;
  position: relative;
}

.nav-item.active {
  color: #2b6cff;
  background: #eaf1ff;
  font-weight: 600;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 26rpx;
  bottom: 26rpx;
  width: 8rpx;
  border-radius: 4rpx;
  background: #2b6cff;
}

/* 右侧商品 */
.goods-panel {
  flex: 1;
  height: 100%;
  padding: 20rpx;
  box-sizing: border-box;
}

.panel-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.goods-row {
  display: flex;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 18rpx;
  margin-bottom: 18rpx;
}

.row-thumb {
  width: 150rpx;
  height: 150rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  overflow: hidden;
}

.row-thumb-img {
  width: 100%;
  height: 100%;
}

.row-info {
  flex: 1;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.row-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #1f2329;
}

.row-sales {
  font-size: 22rpx;
  color: #8a8f99;
}

.row-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.row-price {
  color: #2b6cff;
  font-size: 32rpx;
  font-weight: 700;
}

.tiny {
  height: 52rpx;
  line-height: 52rpx;
  font-size: 22rpx;
  padding: 0 26rpx;
}

.empty {
  text-align: center;
  color: #8a8f99;
  padding: 100rpx 0;
}

/* 加载更多 */
.load-more {
  padding: 22rpx 0;
  text-align: center;
}

.load-hint {
  font-size: 24rpx;
  color: #8a8f99;
}

.load-hint.clickable {
  color: #2b6cff;
}
</style>
