<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  公共组件：申请售后弹窗（选择售后原因底部弹层，淘宝式）
  用法：<refund-sheet :visible="show" :order-state="state" :order-num="num"
          @close="show=false" @success="onSuccess" />
  ============================================
-->
<template>
  <view v-if="visible" class="rs-mask" @tap="close">
    <view class="rs-sheet" @tap.stop>
      <!-- 标题栏 -->
      <view class="rs-head">
        <text class="rs-title">选择售后原因</text>
        <text class="rs-close" @tap="close">✕</text>
      </view>

      <!-- 售后类型页签：待发货只有「退款」，已完成可选「退货退款」（推荐） -->
      <view class="rs-tabs">
        <view class="rs-tab" :class="{ active: applyType === 'refund' }" @tap="applyType = 'refund'">退款</view>
        <view v-if="orderState === 3" class="rs-tab" :class="{ active: applyType === 'return' }" @tap="applyType = 'return'">
          退货退款 <text class="rs-rec">荐</text>
        </view>
      </view>

      <!-- 原因单选列表 -->
      <scroll-view scroll-y class="rs-list">
        <view v-for="(r, i) in reasons" :key="r" class="rs-item" @tap="selected = i">
          <text class="rs-item-text">{{ r }}</text>
          <view class="rs-radio" :class="{ on: selected === i }" />
        </view>
      </scroll-view>

      <!-- 底部操作 -->
      <view class="rs-foot">
        <view class="rs-btn plain" @tap="close">上一步</view>
        <view class="rs-btn primary" @tap="submit">{{ submitting ? '提交中...' : '提交申请' }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'
import { applyRefund } from '../../common/api'

const props = defineProps({
  visible: { type: Boolean, default: false },
  orderState: { type: Number, default: 1 },
  orderNum: { type: String, default: '' }
})
const emit = defineEmits(['close', 'success'])

// 预设售后原因（对齐主流电商话术）
const reasons = [
  '买贵了/少用优惠',
  '不想要了',
  '材质与描述不符',
  '颜色/款式/大小/尺寸与描述不符',
  '尺码没选对',
  '卖家发错货',
  '假冒品牌',
  '收到商品破损、污渍',
  '质量问题',
  '少件（含缺少配件）'
]

const applyType = ref('refund') // refund=仅退款 / return=退货退款
const selected = ref(-1)
const submitting = ref(false)

// 每次打开重置选择；已完成订单默认选中「退货退款」（推荐）
watch(() => props.visible, (v) => {
  if (v) {
    selected.value = -1
    applyType.value = props.orderState === 3 ? 'return' : 'refund'
    submitting.value = false
  }
})

function close() {
  emit('close')
}

// 提交申请：原因带上售后类型前缀（如「退货退款-质量问题」）入库
function submit() {
  if (submitting.value) return
  if (selected.value < 0) {
    uni.showToast({ title: '请选择售后原因', icon: 'none' })
    return
  }
  submitting.value = true
  const typeText = applyType.value === 'return' ? '退货退款' : '退款'
  const reason = typeText + '-' + reasons[selected.value]
  applyRefund(props.orderNum, reason).then((r) => {
    uni.showModal({
      title: '申请已提交',
      content: '退款金额 ¥' + Number(r.refundMoney || 0).toFixed(2) + '，等待商家处理',
      showCancel: false
    })
    emit('success')
    close()
  }).catch((e) => {
    uni.showToast({ title: e.message || '申请失败', icon: 'none' })
  }).finally(() => {
    submitting.value = false
  })
}
</script>

<style scoped>
.rs-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
}

.rs-sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  border-radius: 28rpx 28rpx 0 0;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  animation: rsUp 0.25s ease-out;
}

@keyframes rsUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.rs-head {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 30rpx 30rpx 10rpx;
}

.rs-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2329;
}

.rs-close {
  position: absolute;
  right: 36rpx;
  top: 30rpx;
  font-size: 36rpx;
  color: #b0b3b8;
  padding: 0 10rpx;
}

/* 售后类型页签 */
.rs-tabs {
  display: flex;
  gap: 0;
  margin: 20rpx 30rpx 0;
  background: #f2f4f8;
  border-radius: 14rpx;
  overflow: hidden;
}

.rs-tab {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  font-size: 27rpx;
  color: #4e5560;
}

.rs-tab.active {
  background: #ffffff;
  color: #1f2329;
  font-weight: 700;
  border-radius: 14rpx;
}

.rs-rec {
  display: inline-block;
  font-size: 20rpx;
  color: #ffffff;
  background: #ff9800;
  border-radius: 6rpx;
  padding: 0 8rpx;
  line-height: 30rpx;
  height: 30rpx;
  vertical-align: middle;
  margin-left: 6rpx;
}

/* 原因列表 */
.rs-list {
  max-height: 56vh;
}

.rs-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30rpx 36rpx;
  border-bottom: 2rpx solid #f5f7fb;
}

.rs-item-text {
  font-size: 29rpx;
  color: #1f2329;
}

.rs-radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 3rpx solid #d0d4dc;
  box-sizing: border-box;
  flex-shrink: 0;
}

.rs-radio.on {
  border: 10rpx solid #ff9800;
}

/* 底部按钮 */
.rs-foot {
  display: flex;
  gap: 24rpx;
  padding: 24rpx 30rpx 16rpx;
}

.rs-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 16rpx;
  font-size: 29rpx;
}

.rs-btn.plain {
  background: #f2f4f8;
  color: #4e5560;
}

.rs-btn.primary {
  background: #2b6cff;
  color: #ffffff;
  font-weight: 700;
}
</style>
