<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：首页 Tab
  界面名称：首页（轮播 / 推荐商品）
  ============================================
-->
<template>
  <view class="page">
    <!-- 顶部：定位（预留） + 搜索栏 -->
    <view class="top-bar">
      <view class="locate-chip" @tap="handleLocate">
        <text class="locate-icon">📍</text>
        <text class="locate-text">{{ locationCity }}</text>
        <text class="locate-arrow">▾</text>
      </view>
      <input
        v-model="keyword"
        class="search-input"
        placeholder="请输入商品名称搜索"
        placeholder-class="search-placeholder"
        confirm-type="search"
        @confirm="loadProducts"
      />
      <view class="search-btn" @tap="loadProducts">搜索</view>
    </view>

    <!-- 轮播图：商城活动 -->
    <view class="banner-wrap">
      <swiper
        class="banner-swiper"
        autoplay
        circular
        :interval="3500"
        :duration="500"
        indicator-dots
        indicator-color="rgba(255,255,255,0.5)"
        indicator-active-color="#2b6cff"
      >
        <swiper-item v-for="b in banners" :key="b.image" @tap="goBanner(b)">
          <image class="banner-img" :src="b.image" mode="aspectFill" />
          <view class="banner-text">
            <view class="banner-title">{{ b.title }}</view>
            <view class="banner-sub">{{ b.sub }}</view>
            <view class="banner-btn">{{ b.btn }} ›</view>
          </view>
        </swiper-item>
      </swiper>
    </view>

    <!-- 金刚区：分类快捷入口 -->
    <view class="cate-grid">
      <view v-for="g in quickCates" :key="g.label" class="cate-item" @tap="goQuick(g)">
        <view class="cate-icon" :style="{ background: g.bg }">{{ g.icon }}</view>
        <text class="cate-label">{{ g.label }}</text>
      </view>
    </view>

    <!-- 分类标签 -->
    <scroll-view scroll-x class="chips-scroll">
      <view class="chips-row">
        <view
          v-for="c in categories"
          :key="c"
          class="chip"
          :class="{ active: currentCategory === c }"
          @tap="switchCategory(c)"
        >{{ c }}</view>
      </view>
    </scroll-view>

    <!-- 商品双列列表 -->
    <view class="goods-grid">
      <view v-for="p in products" :key="p.id" class="goods-card" @tap="goDetail(p)">
        <view class="goods-thumb thumb">
          <image class="thumb-img" :src="imgSrc(p.image)" mode="aspectFill" />
        </view>
        <view class="goods-info">
          <view class="goods-name">{{ p.name }}</view>
          <view class="goods-price">¥{{ p.price.toFixed(2) }}</view>
          <view class="goods-sales">
            <text class="fire">🔥</text>
            <text>销量 {{ p.sales }}</text>
          </view>
          <view class="goods-actions">
            <view class="btn-plain small" @tap.stop="handleAddCart(p)">加入购物车</view>
            <view class="btn-primary small" @tap.stop="handleBuyNow(p)">立即购买</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="!loading && products.length === 0" class="empty">暂无相关商品</view>

    <!-- 加载更多（点击或上拉触底均可触发） -->
    <view v-if="products.length" class="load-more" @tap="loadMore">
      <text v-if="loadingMore" class="load-hint">正在加载...</text>
      <text v-else-if="hasMore" class="load-hint clickable">点击加载更多</text>
      <text v-else class="load-hint">— 已加载全部 —</text>
    </view>
  </view>
</template>

<script setup>
import { onShow, onReachBottom, onPullDownRefresh, onTabItemTap } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getCategoryTree, getProducts, imgSrc } from '../../common/api'
import { toast } from '../../common/toast'
import { addToCart, isLoggedIn } from '../../common/store'
import { locate } from '../../common/location'

// 分类导航：从后端类目树动态加载（与管理员端「商品类目」同源），首个固定为「全部」
const categories = ref(['全部'])
const currentCategory = ref('全部')
const keyword = ref('')
const products = ref([])
const loading = ref(false)

// 拉取启用中的一级类目作为分类 chips
async function loadCategories() {
  try {
    const roots = await getCategoryTree()
    categories.value = ['全部', ...roots.map(r => r.name)]
  } catch (e) {
    uni.showToast({ title: e.message || '分类加载失败', icon: 'none' })
  }
}
loadCategories()

/* 分页状态：每页 20 条，上拉/点击追加下一页 */
const PAGE_SIZE = 20
const pageNum = ref(1)
const total = ref(0)
const loadingMore = ref(false)
const hasMore = computed(() => products.value.length < total.value)

/* 轮播图活动数据（category 指向管理员端类目树的一级类目名） */
const banners = [
  { image: '/static/banners/banner-1.png', title: '数码狂欢节', sub: '爆款直降 至高省 300 元', btn: '立即抢购', category: '数码电器' },
  { image: '/static/banners/banner-2.png', title: '智能家电焕新季', sub: '以旧换新 叠加平台补贴', btn: '马上看', category: '数码电器' },
  { image: '/static/banners/banner-3.png', title: '生鲜食品节', sub: '产地直采 新鲜到家', btn: '去逛逛', category: '食品生鲜' }
]

/* 金刚区分类快捷入口（category 指向类目树一级类目名） */
const quickCates = [
  { icon: '👕', label: '服饰鞋包', bg: '#eaf1ff', category: '服饰鞋包' },
  { icon: '📱', label: '数码电器', bg: '#fdeeee', category: '数码电器' },
  { icon: '🍎', label: '食品生鲜', bg: '#e8f7ee', category: '食品生鲜' },
  { icon: '💄', label: '美妆个护', bg: '#f0eaff', category: '美妆个护' },
  { icon: '⏰', label: '限时秒杀', bg: '#fff9e4', activity: '限时秒杀' },
  { icon: '🎁', label: '新人专礼', bg: '#fdeef5', activity: '新人专礼' },
  { icon: '🎟️', label: '领券中心', bg: '#e6f7f2', activity: '领券中心' }
]

// 定位功能：读取当前位置，chip 显示所在城市（结果同时可用于新增地址页的自动填写）
const locationCity = ref('定位')
const locating = ref(false)
async function handleLocate() {
  if (locating.value) return
  locating.value = true
  uni.showLoading({ title: '正在定位...' })
  try {
    const loc = await locate()
    locationCity.value = loc.city || loc.province || '已定位'
    uni.hideLoading()
    uni.showToast({ title: '当前位于：' + (loc.district || loc.city || loc.province), icon: 'none' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '定位失败', icon: 'none' })
  } finally {
    locating.value = false
  }
}

// 点击轮播图：切换到对应分类并刷新商品列表
function goBanner(b) {
  currentCategory.value = b.category
  keyword.value = ''
  loadProducts()
  uni.pageScrollTo({ scrollTop: 400, duration: 200 })
}

// 点击金刚区入口：领券中心进幸运转盘页，其余活动专区提示即将上线，分类入口按分类筛选商品
function goQuick(g) {
  if (g.activity === '领券中心') {
    uni.navigateTo({ url: '/pages/lucky/lucky' })
    return
  }
  if (g.activity) {
    uni.showToast({ title: g.activity + '专区即将上线', icon: 'none' })
    return
  }
  currentCategory.value = g.category
  keyword.value = ''
  loadProducts()
  uni.pageScrollTo({ scrollTop: 400, duration: 200 })
}

// 加载商品列表（分页）：reset=true 重新从第 1 页拉取（切分类/搜索/下拉刷新）；
// reset=false 追加下一页（上拉触底或点击「加载更多」）
// 竞态保护：请求序号丢弃过期响应（快速切分类/搜索时，旧请求晚到不会把旧分类数据拼进新列表）
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
    const res = await getProducts(currentCategory.value, keyword.value.trim(), pageNum.value, PAGE_SIZE)
    if (seq !== requestSeq) return // 过期响应：期间已发起更新的请求，直接丢弃
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

// 触底/点击加载下一页
function loadMore() {
  loadProducts(false)
}

// 上拉触底加载下一页
onReachBottom(() => loadProducts(false))

// 下拉刷新：回到第 1 页重新拉取
onPullDownRefresh(async () => {
  await loadProducts(true)
  uni.stopPullDownRefresh()
})

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

// 切换顶部分类标签，重新拉取商品
function switchCategory(c) {
  currentCategory.value = c
  loadProducts()
}

// 登录校验：未登录则提示并跳转登录页，返回是否通过校验
function checkLogin() {
  if (!isLoggedIn()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return false
  }
  return true
}

// 加入购物车：先校验登录，再加 1 件并Toast提示
function handleAddCart(p) {
  if (!checkLogin()) return
  addToCart(p, 1)
  toast('已加入购物车')
}

// 立即购买：不进购物车，直接带商品 id 进订单确认页结算
function handleBuyNow(p) {
  if (!checkLogin()) return
  uni.navigateTo({ url: '/pages/order-confirm/order-confirm?from=buyNow&id=' + p.id })
}

// 跳转商品详情页
function goDetail(p) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + p.id })
}

// 页面显示：首次进入加载；从详情页返回时不重置已加载的分页列表（下拉刷新可手动更新）
onShow(() => {
  if (!products.value.length) loadProducts()
})
</script>

<style scoped>
.page {
  padding-bottom: 30rpx;
}

/* 加载更多 */
.load-more {
  padding: 26rpx 0 10rpx;
  text-align: center;
}

.load-hint {
  font-size: 24rpx;
  color: #8a8f99;
}

.load-hint.clickable {
  color: #2b6cff;
}

/* 顶部定位 + 搜索 */
.top-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #ffffff;
}

.locate-chip {
  display: flex;
  align-items: center;
  gap: 6rpx;
  flex-shrink: 0;
  margin-right: 18rpx;
  background: #eaf1ff;
  border-radius: 999rpx;
  padding: 0 22rpx;
  height: 60rpx;
}

.locate-icon {
  font-size: 24rpx;
}

.locate-text {
  font-size: 25rpx;
  color: #2b6cff;
  font-weight: 600;
}

.locate-arrow {
  font-size: 20rpx;
  color: #2b6cff;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 999rpx;
  padding: 0 34rpx;
  font-size: 26rpx;
}

.search-placeholder {
  color: #b0b3b8;
}

.search-btn {
  margin-left: 18rpx;
  background: #2b6cff;
  color: #ffffff;
  font-size: 28rpx;
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 44rpx;
  border-radius: 999rpx;
}

/* 轮播图 */
.banner-wrap {
  padding: 20rpx 24rpx 0;
}

.banner-swiper {
  height: 280rpx;
  border-radius: 20rpx;
  overflow: hidden;
}

.banner-img {
  width: 100%;
  height: 100%;
}

.banner-text {
  position: absolute;
  left: 40rpx;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
}

.banner-title {
  font-size: 40rpx;
  font-weight: 800;
  color: #1f2329;
  letter-spacing: 2rpx;
}

.banner-sub {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #4e5560;
}

.banner-btn {
  margin-top: 20rpx;
  display: inline-block;
  background: #2b6cff;
  color: #ffffff;
  font-size: 24rpx;
  padding: 10rpx 30rpx;
  border-radius: 999rpx;
}

/* 金刚区分类入口 */
.cate-grid {
  display: flex;
  flex-wrap: wrap;
  background: #ffffff;
  margin: 20rpx 24rpx 0;
  border-radius: 20rpx;
  padding: 26rpx 0 10rpx;
}

.cate-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 24rpx;
}

.cate-icon {
  width: 92rpx;
  height: 92rpx;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
}

.cate-label {
  font-size: 24rpx;
  color: #4e5560;
}

/* 分类 chips */
.chips-scroll {
  white-space: nowrap;
  background: #ffffff;
  padding: 6rpx 0 20rpx;
}

.chips-row {
  display: inline-flex;
  padding: 0 24rpx;
  gap: 18rpx;
}

.chip {
  display: inline-block;
  padding: 0 34rpx;
  height: 60rpx;
  line-height: 60rpx;
  background: #eef1f6;
  color: #4e5560;
  border-radius: 999rpx;
  font-size: 26rpx;
}

.chip.active {
  background: #2b6cff;
  color: #ffffff;
}

/* 商品网格 */
.goods-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: 20rpx 24rpx 0;
}

.goods-card {
  width: 340rpx;
  background: #ffffff;
  border-radius: 20rpx;
  overflow: hidden;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 10rpx rgba(43, 108, 255, 0.06);
}

.thumb {
  width: 340rpx;
  height: 250rpx;
  overflow: hidden;
}

.thumb-img {
  width: 100%;
  height: 100%;
}

.goods-info {
  padding: 18rpx 20rpx 20rpx;
}

.goods-name {
  font-size: 26rpx;
  color: #1f2329;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 68rpx;
}

.goods-price {
  margin-top: 10rpx;
  color: #2b6cff;
  font-size: 34rpx;
  font-weight: 700;
}

.goods-sales {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8a8f99;
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.fire {
  font-size: 22rpx;
}

.goods-actions {
  margin-top: 16rpx;
  display: flex;
  justify-content: space-between;
  gap: 12rpx;
}

.small {
  height: 56rpx;
  line-height: 54rpx;
  font-size: 21rpx;
  padding: 0 8rpx;
  flex: 1;
  white-space: nowrap;
  justify-content: center;
}

.empty {
  text-align: center;
  color: #8a8f99;
  padding: 120rpx 0;
}
</style>
