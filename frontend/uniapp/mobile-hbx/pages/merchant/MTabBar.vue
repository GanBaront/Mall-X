<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  公共组件：商家端底部导航栏
  ============================================
-->
<template>
  <view class="mtab-bar">
    <!-- 底部导航项（图标 + 文字） -->
    <view
      v-for="t in tabs"
      :key="t.key"
      class="mtab-item"
      :class="{ active: active === t.key }"
      @tap="go(t)"
    >
      <text class="mtab-icon">{{ t.icon }}</text>
      <text class="mtab-text">{{ t.label }}</text>
    </view>
  </view>
</template>

<script setup>
// 组件入参：当前激活的 Tab key（高亮对应导航项）
const props = defineProps({
  active: { type: String, default: 'index' }
})

// 双击当前 Tab 时通知页面回到顶部
const emit = defineEmits(['backtop'])

// 底部导航项配置（key / 图标 / 文案 / 跳转地址）
const tabs = [
  { key: 'index', icon: '🏠', label: '工作台', url: '/pages/merchant/index' },
  { key: 'goods', icon: '📦', label: '商品', url: '/pages/merchant/goods' },
  { key: 'orders', icon: '🧾', label: '订单', url: '/pages/merchant/orders' },
  { key: 'message', icon: '🔔', label: '消息', url: '/pages/merchant/message' },
  { key: 'mine', icon: '🏪', label: '我的', url: '/pages/merchant/mine' }
]

// 切换底部导航（reLaunch 跳转；当前页不重复跳转，双击当前 Tab 通知页面回顶）
let lastTap = 0
function go(t) {
  const now = Date.now()
  if (t.key === props.active) {
    if (now - lastTap < 400) emit('backtop') // 400ms 内连点同一 Tab → 回到顶部
    lastTap = now
    return
  }
  lastTap = now
  uni.reLaunch({ url: t.url })
}
</script>

<style scoped>
.mtab-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  border-top: 2rpx solid #eceef3;
  display: flex;
  padding: 12rpx 0 calc(12rpx + env(safe-area-inset-bottom));
  z-index: 999;
}

.mtab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.mtab-icon {
  font-size: 40rpx;
  line-height: 1.1;
}

.mtab-text {
  font-size: 22rpx;
  color: #8a8f99;
}

.mtab-item.active .mtab-text {
  color: #2b6cff;
  font-weight: 700;
}
</style>
