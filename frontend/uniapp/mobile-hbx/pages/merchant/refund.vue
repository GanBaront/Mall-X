<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：售后管理
  界面名称：售后退款处理页
  ============================================
-->
<template>
  <view class="page">
    <!-- 状态 Tab -->
    <scroll-view scroll-x class="tabs-scroll">
      <view class="tabs-row">
        <view
          v-for="t in tabs"
          :key="t.value"
          class="tab-item"
          :class="{ active: currentTab === t.value }"
          @tap="currentTab = t.value"
        >{{ t.label }}</view>
      </view>
    </scroll-view>

    <!-- 退款列表 -->
    <view v-if="filteredList.length > 0" class="refund-list">
      <view v-for="r in filteredList" :key="r.refundOrderId" class="refund-card">
        <view class="refund-head">
          <text class="refund-id">{{ r.refundOrderId }}</text>
          <text class="refund-state" :class="'rs-' + r.state">{{ stateText(r.state) }}</text>
        </view>

        <view class="refund-body">
          <view class="refund-info">
            <view class="refund-goods">{{ r.goodsName }}</view>
            <view class="refund-order">原订单：{{ r.orderNum }}</view>
            <view class="refund-reason">原因：{{ r.reason }}</view>
            <view class="refund-time">申请时间：{{ r.applyTime }}</view>
          </view>
          <view class="refund-money">¥{{ r.refundMoney.toFixed(2) }}</view>
        </view>

        <!-- 操作按钮组：待处理时显示“拒绝退款 / 同意退款” -->
        <view v-if="r.state === 0" class="refund-actions">
          <view class="btn-danger-plain handle-btn" @tap="handle(r, false)">拒绝退款</view>
          <view class="btn-success-plain handle-btn" @tap="handle(r, true)">同意退款</view>
        </view>
        <view v-else-if="r.state === 1" class="refund-result ok">已同意退款，等待平台审核打款</view>
        <view v-else class="refund-result reject">已拒绝退款</view>
      </view>
    </view>

    <view v-else class="empty">暂无相关售后申请</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getRefunds, handleRefundMock } from '../../common/merchant'

// 状态筛选 Tab（0待处理 / 1已同意 / 2已拒绝 / -1全部）
const tabs = [
  { label: '待处理', value: 0 },
  { label: '已同意', value: 1 },
  { label: '已拒绝', value: 2 },
  { label: '全部', value: -1 }
]

// 当前选中的状态 Tab
const currentTab = ref(0)
// 售后退款单列表
const refundList = ref([])

// 按当前 Tab 过滤退款单（全部则直接返回）
const filteredList = computed(() => {
  if (currentTab.value === -1) return refundList.value
  return refundList.value.filter(r => r.state === currentTab.value)
})

// 退款状态文案映射：0待处理 / 1已同意 / 2已拒绝
function stateText(state) {
  return { 0: '待处理', 1: '已同意', 2: '已拒绝' }[state] || '未知'
}

// 同意/拒绝退款（二次确认后调用后端处理并更新本地状态；防重入避免响应慢时连点重复处理）
const handling = ref(false)
function handle(r, agree) {
  if (handling.value) return
  if (r.state !== 0) {
    uni.showToast({ title: '该退款申请已处理', icon: 'none' })
    return
  }
  const action = agree ? '同意' : '拒绝'
  uni.showModal({
    title: action + '退款',
    content: `确定${action}订单 ${r.orderNum || ''} 的退款申请（¥${Number(r.refundMoney || 0).toFixed(2)}）？`,
    confirmColor: agree ? '#2ba471' : '#e64340',
    success: (res) => {
      if (res.confirm) {
        handling.value = true
        handleRefundMock(r.refundOrderId, agree).then((ret) => {
          r.state = agree ? 1 : 2
          uni.showToast({ title: ret.message || `已${action}`, icon: 'none' })
        }).catch((e) => {
          uni.showToast({ title: e.message || '操作失败', icon: 'none' })
        }).finally(() => {
          handling.value = false
        })
      }
    }
  })
}

// 加载售后退款列表（退款金额转数字）
async function load() {
  try {
    const res = await getRefunds()
    refundList.value = (res.records || []).map(r => ({ ...r, refundMoney: Number(r.refundMoney || 0) }))
  } catch (e) {
    uni.showToast({ title: e.message || '退款数据加载失败', icon: 'none' })
  }
}

// 页面显示时加载退款列表
onShow(load)
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.tabs-scroll {
  white-space: nowrap;
  background: #ffffff;
}

.tabs-row {
  display: inline-flex;
  padding: 0 12rpx;
}

.tab-item {
  display: inline-block;
  padding: 24rpx 30rpx;
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

.refund-card {
  background: #ffffff;
  border-radius: 20rpx;
  margin: 20rpx 24rpx 0;
  padding: 24rpx;
}

.refund-head {
  display: flex;
  justify-content: space-between;
  padding-bottom: 14rpx;
  border-bottom: 2rpx solid #f0f3f9;
}

.refund-id {
  font-size: 22rpx;
  color: #606266;
}

.refund-state {
  font-size: 26rpx;
  font-weight: 600;
}

.rs-0 { color: #ff9800; }
.rs-1 { color: #2ba471; }
.rs-2 { color: #8a8f99; }

.refund-body {
  display: flex;
  padding: 18rpx 0;
}

.refund-info {
  flex: 1;
  min-width: 0;
}

.refund-goods {
  font-size: 28rpx;
  font-weight: 600;
}

.refund-order {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8a8f99;
}

.refund-reason {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #4e5560;
}

.refund-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b0b3b8;
}

.refund-money {
  font-size: 32rpx;
  font-weight: 700;
  color: #e64340;
}

.refund-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18rpx;
  padding-top: 14rpx;
  border-top: 2rpx solid #f0f3f9;
}

.handle-btn {
  height: 60rpx;
  line-height: 58rpx;
  font-size: 26rpx;
  padding: 0 40rpx;
}

.refund-result {
  padding-top: 14rpx;
  border-top: 2rpx solid #f0f3f9;
  font-size: 24rpx;
  text-align: right;
}

.refund-result.ok { color: #2ba471; }
.refund-result.reject { color: #8a8f99; }

.empty {
  text-align: center;
  color: #8a8f99;
  padding: 160rpx 0;
}
</style>
