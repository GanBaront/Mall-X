<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：营销活动
  界面名称：我的卡券（转盘抽到的优惠券 / 现金红包）
  ============================================
-->
<template>
  <view class="page">
    <!-- 状态筛选 -->
    <view class="tab-row">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab-item"
        :class="{ active: currentTab === t.value }"
        @tap="switchTab(t.value)"
      >{{ t.label }}</view>
    </view>

    <!-- 卡券列表 -->
    <view v-for="c in list" :key="c.id" class="coupon-card" :class="{ used: Number(c.state) === 1 }">
      <view class="coupon-left">
        <template v-if="Number(c.type) === 2">
          <view class="coupon-val">¥{{ Number(c.amount).toFixed(0) }}</view>
          <view class="coupon-val-label">现金红包</view>
        </template>
        <template v-else>
          <view class="coupon-val">{{ rateText(c.discountRate) }}<text class="val-zhe">折</text></view>
          <view class="coupon-val-label">折扣券</view>
        </template>
      </view>
      <view class="coupon-divider" />
      <view class="coupon-right">
        <view class="coupon-name">{{ c.name }}</view>
        <view class="coupon-desc">{{ Number(c.type) === 2 ? '支付时直接抵扣现金' : '支付时按券面折扣抵扣' }}</view>
        <view class="coupon-time">获得时间：{{ fmtTime(c.createdAt) }}</view>
        <view class="coupon-state" :class="{ used: Number(c.state) === 1 }">
          {{ Number(c.state) === 1 ? '已使用' + (c.usedOrderNum ? '（订单 ' + c.usedOrderNum + '）' : '') : '未使用 · 下单时可选' }}
        </view>
      </view>
    </view>

    <view v-if="!loading && list.length === 0" class="empty">
      <view class="empty-main">暂无卡券</view>
      <view class="empty-sub">去首页「领券中心」抽一张吧 🎉</view>
    </view>

    <view v-if="list.length" class="foot-tip">卡券自获得起 15 天内有效；下单支付时选择卡券即可抵扣，订单取消后券自动退回</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { myCoupons } from '../../common/api'

const tabs = [
  { label: '全部', value: null },
  { label: '未使用', value: 0 },
  { label: '已过期', value: 'expired' },
  { label: '已使用', value: 1 }
]
const currentTab = ref(null)
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    // 已过期 tab 按「未使用(0) + 过期标记」过滤；「未使用」tab 排除已过期
    let records = await myCoupons(currentTab.value === 'expired' ? 0 : currentTab.value)
    if (currentTab.value === 'expired') {
      records = records.filter(c => c.expired)
    } else if (currentTab.value === 0) {
      records = records.filter(c => !c.expired)
    }
    list.value = records
  } catch (e) {
    uni.showToast({ title: e.message || '卡券加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function switchTab(v) {
  currentTab.value = v
  load()
}

// 折扣率展示：0.85 → 8.5
function rateText(rate) {
  const r = Number(rate || 1) * 10
  return (Math.round(r * 10) / 10).toString()
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

// 有效期展示：优先 expireAt；历史数据无 expireAt 时按获得时间 + 15 天推算
function expireText(c) {
  if (c.expireAt) return fmtTime(c.expireAt)
  if (!c.createdAt) return '-'
  const d = new Date(String(c.createdAt).replace(' ', 'T'))
  if (isNaN(d.getTime())) return '-'
  d.setDate(d.getDate() + 15)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onShow(load)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx 24rpx 60rpx;
}

/* 状态筛选 */
.tab-row {
  display: flex;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.tab-item {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  background: #ffffff;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #4e5560;
}

.tab-item.active {
  background: #2b6cff;
  color: #ffffff;
  font-weight: 700;
}

/* 卡券卡片 */
.coupon-card {
  display: flex;
  align-items: center;
  background: linear-gradient(120deg, #fff7ec 0%, #ffe9d3 100%);
  border-radius: 20rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
}

.coupon-card.used {
  background: #f0f1f5;
}

.coupon-left {
  width: 190rpx;
  padding: 30rpx 0;
  text-align: center;
  flex-shrink: 0;
}

.coupon-val {
  font-size: 52rpx;
  font-weight: 800;
  color: #e64340;
}

.val-zhe {
  font-size: 28rpx;
  margin-left: 4rpx;
}

.used .coupon-val {
  color: #b0b3b8;
}

.coupon-val-label {
  font-size: 22rpx;
  color: #c98c3d;
  margin-top: 4rpx;
}

.used .coupon-val-label {
  color: #b0b3b8;
}

.coupon-divider {
  width: 2rpx;
  align-self: stretch;
  margin: 18rpx 0;
  background: repeating-linear-gradient(180deg, #e8caa4 0 10rpx, transparent 10rpx 20rpx);
}

.used .coupon-divider {
  background: repeating-linear-gradient(180deg, #d8dbe2 0 10rpx, transparent 10rpx 20rpx);
}

.coupon-right {
  flex: 1;
  padding: 26rpx 26rpx;
  min-width: 0;
}

.coupon-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2329;
}

.coupon-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a8f99;
}

.coupon-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b0b3b8;
}

.coupon-state {
  margin-top: 12rpx;
  display: inline-block;
  font-size: 22rpx;
  color: #2ba471;
  background: rgba(43, 164, 113, 0.1);
  border-radius: 8rpx;
  padding: 4rpx 14rpx;
}

.coupon-state.used {
  color: #b0b3b8;
  background: #e8eaef;
}

/* 空态 */
.empty {
  text-align: center;
  padding: 140rpx 0;
}

.empty-main {
  font-size: 30rpx;
  color: #606266;
}

.empty-sub {
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #b0b3b8;
}

.foot-tip {
  text-align: center;
  font-size: 22rpx;
  color: #b0b3b8;
  margin-top: 20rpx;
}
</style>
