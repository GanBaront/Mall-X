<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：消息中心
  界面名称：消息通知页
  ============================================
-->
<template>
  <view class="page">
    <!-- 类型筛选 -->
    <scroll-view scroll-x class="tabs-scroll">
      <view class="tabs-row">
        <view
          v-for="t in tabs"
          :key="t"
          class="tab-item"
          :class="{ active: currentTab === t }"
          @tap="currentTab = t"
        >{{ t }}</view>
      </view>
      <view class="read-all" @tap="markAllRead">全部已读</view>
    </scroll-view>

    <!-- 消息列表 -->
    <view v-if="filteredList.length > 0" class="msg-list">
      <!-- 单条消息卡片（图标 + 标题/内容 + 未读标记，点击标记已读并落库） -->
      <view v-for="m in filteredList" :key="m.id" class="msg-card" :class="{ unread: m.unread }" @tap="markRead(m)">
        <view class="msg-icon" :class="iconClass(m.type)">{{ iconText(m.type) }}</view>
        <view class="msg-body">
          <view class="msg-title-row">
            <text class="msg-title">{{ m.title }}</text>
            <view v-if="m.unread" class="unread-dot" />
          </view>
          <view class="msg-content">{{ m.content }}</view>
          <view class="msg-meta">
            <text class="msg-type">{{ m.type }}</text>
            <text class="msg-time">{{ m.time }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty">🔔 暂无相关消息</view>
  </view>
  <MTabBar active="message" @backtop="scrollTop" />
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MTabBar from './MTabBar.vue'
import { getNotices, markNoticeRead } from '../../common/merchant'
import { toast } from '../../common/toast'

// 消息类型筛选（全部/订单/售后/资金/系统）
const tabs = ['全部', '订单', '售后', '资金', '系统']
// 当前选中的消息类型
const currentTab = ref('全部')
// 消息通知列表
const noticeList = ref([])

// 按当前类型过滤消息（“全部”则直接返回）
const filteredList = computed(() => {
  if (currentTab.value === '全部') return noticeList.value
  return noticeList.value.filter(m => m.type === currentTab.value)
})

// 消息类型对应的图标 emoji
function iconText(type) {
  return { 订单: '🧾', 售后: '↩️', 资金: '💰', 系统: '📢' }[type] || '📢'
}

// 消息类型对应的图标样式类（用于背景色区分）
function iconClass(type) {
  return { 订单: 'ic-order', 售后: 'ic-refund', 资金: 'ic-funds', 系统: 'ic-sys' }[type] || 'ic-sys'
}

// 双击底部 Tab：页面回到顶端
function scrollTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

// 点击单条消息：标记已读并落库（后端持久化，刷新不回退）
function markRead(m) {
  if (!m.unread) return
  markNoticeRead({ id: m.id }).then(() => {
    m.unread = false
  }).catch((e) => {
    uni.showToast({ title: e.message || '标记失败', icon: 'none' })
  })
}

// 将所有消息标记为已读（落库，刷新不回退）
function markAllRead() {
  markNoticeRead({ all: true }).then((res) => {
    noticeList.value.forEach(m => { m.unread = false })
    toast('已全部标记为已读')
  }).catch((e) => {
    uni.showToast({ title: e.message || '标记失败', icon: 'none' })
  })
}

// 加载消息通知列表
async function load() {
  try {
    const res = await getNotices()
    noticeList.value = res.records || []
  } catch (e) {
    uni.showToast({ title: e.message || '消息加载失败', icon: 'none' })
  }
}

// 页面显示时加载消息列表
onShow(load)
</script>

<style scoped>
.page {
  /* 底部留白须大于固定底栏（MTabBar 约 110rpx + 安全区），否则最后一条消息被遮挡 */
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}

.tabs-scroll {
  white-space: nowrap;
  background: #ffffff;
  position: relative;
}

.tabs-row {
  display: inline-flex;
  padding: 0 12rpx;
}

.tab-item {
  display: inline-block;
  padding: 24rpx 28rpx;
  font-size: 28rpx;
  color: #4e5560;
  position: relative;
}

.tab-item.active {
  color: #2b6cff;
  font-weight: 700;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 10rpx;
  width: 48rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: #2b6cff;
}

.read-all {
  position: absolute;
  right: 24rpx;
  top: 24rpx;
  font-size: 24rpx;
  color: #2b6cff;
}

.msg-card {
  display: flex;
  background: #ffffff;
  border-radius: 20rpx;
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
  gap: 18rpx;
}

.msg-card.unread {
  background: #f4f8ff;
}

.msg-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  flex-shrink: 0;
}

.ic-order { background: #eaf1ff; }
.ic-refund { background: #fff1f0; }
.ic-funds { background: #f0fff4; }
.ic-sys { background: #fff7e6; }

.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.msg-title {
  font-size: 28rpx;
  font-weight: 600;
}

.unread-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #e64340;
}

.msg-content {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #606266;
  line-height: 1.5;
}

.msg-meta {
  margin-top: 12rpx;
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
  color: #b0b3b8;
}

.empty {
  text-align: center;
  color: #8a8f99;
  padding: 160rpx 0;
}
</style>
