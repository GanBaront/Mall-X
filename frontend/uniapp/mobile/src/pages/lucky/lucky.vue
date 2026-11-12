<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：营销活动
  界面名称：领券中心（幸运转盘）
  ============================================
-->
<template>
  <view class="page">
    <view class="banner-tip">🎁 每天可免费抽 3 次，现金红包下单直减、折扣券按折抵扣</view>

    <!-- 幸运转盘 -->
    <view class="wheel-wrap">
      <!-- 固定指针 -->
      <view class="pointer" />
      <!-- 转盘（旋转层） -->
      <view class="wheel" :style="wheelStyle">
        <view
          v-for="(p, idx) in prizes"
          :key="idx"
          class="sector-label"
          :style="{ transform: 'rotate(' + (idx * 45 + 22.5) + 'deg) translateY(-190rpx)' }"
        >
          <view class="sector-text">
            <text class="sector-name">{{ p.label }}</text>
            <text class="sector-sub">{{ p.sub }}</text>
          </view>
        </view>
        <!-- 中心抽奖按钮 -->
        <view class="hub" :class="{ spinning }" @tap="draw">
          <text class="hub-text">{{ spinning ? '转动中' : '抽 奖' }}</text>
        </view>
      </view>
    </view>

    <!-- 剩余次数与我的卡券 -->
    <view class="info-card">
      <view class="times">今日剩余抽奖次数：<text class="times-num">{{ leftTimes }}</text> / 3</view>
      <view class="btn-primary go-coupons" @tap="goCoupons">查看我的卡券 ›</view>
    </view>

    <view class="rule-card">
      <view class="rule-title">活动规则</view>
      <view class="rule-line">1. 每位用户每天可免费抽奖 3 次，奖品 100% 中奖；</view>
      <view class="rule-line">2. 现金红包为无门槛红包，下单支付时可直接抵扣现金；</view>
      <view class="rule-line">3. 折扣券按券面折扣对订单金额打折（如 8.5 折即按原价 85% 支付）；</view>
      <view class="rule-line">4. 每笔订单限用一张券，订单取消/超时关闭后券自动退回；</view>
      <view class="rule-line">5. 抽到的券可在「个人中心 - 我的卡券」中查看。</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { drawCoupon } from '../../common/api'
import { isLoggedIn } from '../../common/store'

// 转盘 8 扇区（顺序必须与后端 CouponController.PRIZES 一致，prizeIndex 对应此数组下标）
const prizes = [
  { label: '¥2 红包', sub: '现金红包' },
  { label: '9 折券', sub: '折扣券' },
  { label: '¥5 红包', sub: '现金红包' },
  { label: '8.8 折券', sub: '折扣券' },
  { label: '¥10 红包', sub: '现金红包' },
  { label: '8.5 折券', sub: '折扣券' },
  { label: '¥20 红包', sub: '现金红包' },
  { label: '8 折券', sub: '折扣券' }
]

const spinning = ref(false)
const leftTimes = ref(3)
const rotate = ref(0)

// 转盘样式：交替扇区底色（conic-gradient）+ 旋转角度动画
const wheelStyle = computed(() => ({
  transform: 'rotate(' + rotate.value + 'deg)',
  background: 'conic-gradient(#fff4e0 0deg 45deg, #ffffff 45deg 90deg, #fff4e0 90deg 135deg, #ffffff 135deg 180deg, #fff4e0 180deg 225deg, #ffffff 225deg 270deg, #fff4e0 270deg 315deg, #ffffff 315deg 360deg)'
}))

onShow(() => {
  if (!isLoggedIn()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 500)
  }
})

// 页面卸载时清理抽奖揭晓定时器：抽奖后立即返回仍会弹出中奖 Modal
let revealTimer = null
onUnload(() => {
  if (revealTimer) {
    clearTimeout(revealTimer)
    revealTimer = null
    spinning.value = false
  }
})

// 抽奖：先调后端（结果由后端决定），再驱动转盘动画转到对应扇区，最后弹窗揭晓
function draw() {
  if (spinning.value) return
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  // 本地剩余次数用完直接提示（后端每日限 3 次仍会兜底校验）
  if (leftTimes.value <= 0) {
    uni.showToast({ title: '今日抽奖次数已用完，明天再来吧', icon: 'none' })
    return
  }
  spinning.value = true
  uni.showLoading({ title: '抽奖中...', mask: true })
  drawCoupon().then((res) => {
    uni.hideLoading()
    const idx = Number(res.prizeIndex || 0)
    // 目标角度：已有圈数 + 5 整圈 + 转到目标扇区中心（扇区中心 = idx*45+22.5，取 360-角度 使其顺时针到达正上方）
    const target = 360 * 5 + (360 - (idx * 45 + 22.5))
    rotate.value = rotate.value - (rotate.value % 360) + target
    leftTimes.value = Number(res.leftTimes || 0)
    const coupon = res.coupon || {}
    // 等动画转完（transition 4s）再揭晓
    revealTimer = setTimeout(() => {
      revealTimer = null
      spinning.value = false
      const detail = Number(coupon.type) === 2
        ? '恭喜获得「' + (coupon.name || '现金红包') + '」，下单支付时可直接抵扣！'
        : '恭喜获得「' + (coupon.name || '折扣券') + '」，下单支付时按券面折扣抵扣！'
      uni.showModal({
        title: '🎉 中奖啦',
        content: detail + ' 已放入「个人中心-我的卡券」。',
        confirmText: '查看卡券',
        cancelText: '继续逛',
        success: (m) => {
          if (m.confirm) uni.navigateTo({ url: '/pages/coupons/coupons' })
        }
      })
    }, 4100)
  }).catch((e) => {
    uni.hideLoading()
    spinning.value = false
    uni.showModal({ title: '抽奖失败', content: e.message || '请稍后再试', showCancel: false })
  })
}

function goCoupons() {
  uni.navigateTo({ url: '/pages/coupons/coupons' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #2b6cff 0%, #6ea1ff 34%, #f5f7fb 60%);
  padding-bottom: 60rpx;
}

.banner-tip {
  margin: 24rpx 30rpx 0;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 16rpx;
  padding: 20rpx 26rpx;
  font-size: 24rpx;
  color: #4e5560;
  text-align: center;
}

/* 转盘 */
.wheel-wrap {
  position: relative;
  width: 620rpx;
  height: 620rpx;
  margin: 40rpx auto 0;
}

.wheel {
  position: relative;
  width: 600rpx;
  height: 600rpx;
  margin: 10rpx;
  border-radius: 50%;
  border: 16rpx solid #ffb400;
  box-shadow: 0 10rpx 40rpx rgba(0, 0, 0, 0.18);
  transition: transform 4s cubic-bezier(0.2, 0.8, 0.25, 1);
  overflow: hidden;
}

/* 固定指针（不随转盘旋转） */
.pointer {
  position: absolute;
  top: -6rpx;
  left: 50%;
  margin-left: -22rpx;
  width: 0;
  height: 0;
  border-left: 22rpx solid transparent;
  border-right: 22rpx solid transparent;
  border-top: 44rpx solid #e64340;
  z-index: 3;
}

/* 扇区标签：定位在扇区中心方向半径 190rpx 处，内部文字再加 rotate(90deg)
   使文字沿「半径方向」排布（大转盘标准样式），任何扇区都不会出现倒置文字 */
.sector-label {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 0;
  height: 0;
}

.sector-text {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  width: 320rpx;
  margin-left: -160rpx;
  transform: rotate(90deg);
  white-space: nowrap;
}

.sector-name {
  font-size: 30rpx;
  font-weight: 800;
  color: #b25a00;
  white-space: nowrap;
}

.sector-sub {
  font-size: 20rpx;
  color: #c98c3d;
  white-space: nowrap;
}

/* 中心按钮 */
.hub {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 150rpx;
  height: 150rpx;
  margin: -75rpx 0 0 -75rpx;
  border-radius: 50%;
  background: linear-gradient(180deg, #ff7a45 0%, #e64340 100%);
  border: 8rpx solid #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(230, 67, 64, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.hub.spinning {
  opacity: 0.85;
}

.hub-text {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: 4rpx;
}

/* 次数与入口 */
.info-card {
  margin: 36rpx 30rpx 0;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 30rpx;
  text-align: center;
}

.times {
  font-size: 28rpx;
  color: #4e5560;
}

.times-num {
  color: #e64340;
  font-weight: 800;
  font-size: 32rpx;
}

.go-coupons {
  margin-top: 24rpx;
  height: 76rpx;
  line-height: 76rpx;
  font-size: 28rpx;
}

/* 规则 */
.rule-card {
  margin: 24rpx 30rpx 0;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 30rpx;
}

.rule-title {
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 16rpx;
}

.rule-line {
  font-size: 24rpx;
  color: #8a8f99;
  line-height: 1.8;
}
</style>
