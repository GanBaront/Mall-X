<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：资金管理
  界面名称：资金提现页
  ============================================
-->
<template>
  <view class="page">
    <!-- 余额卡 -->
    <view class="balance-card">
      <view class="balance-label">可提现余额（元）</view>
      <view class="balance-num">¥{{ funds.balance.toFixed(2) }}</view>
      <view class="balance-sub">
        <text>累计营收 ¥{{ funds.totalIncome.toFixed(2) }}</text>
        <text>累计手续费 ¥{{ funds.totalServiceFee.toFixed(2) }}</text>
      </view>
    </view>

    <!-- 提现表单 -->
    <view class="card">
      <view class="form-title">申请提现</view>
      <view class="form-item">
        <text class="form-label">提现金额</text>
        <view class="amount-row">
          <text class="yen">¥</text>
          <input v-model="amount" class="amount-input" type="digit" placeholder="0.00" placeholder-class="ph" />
          <view class="all-btn" @tap="amount = String(funds.balance)">全部</view>
        </view>
      </view>
      <view class="form-item">
        <text class="form-label">到账账户</text>
        <view class="account-list">
          <view
            v-for="a in accounts"
            :key="a"
            class="account-item"
            :class="{ active: account === a }"
            @tap="account = a"
          >
            <text class="radio-dot" :class="{ on: account === a }" />
            <text>{{ a }}</text>
          </view>
        </view>
      </view>
      <!-- 提现提交按钮 -->
      <view class="withdraw-btn btn-primary" @tap="submitWithdraw">提现</view>
      <view class="withdraw-tip">预计 1-3 个工作日到账，单笔最低 ¥1.00</view>
    </view>

    <!-- 提现记录 -->
    <view class="card">
      <view class="form-title">提现记录</view>
      <view v-for="r in funds.records" :key="r.id" class="record-row">
        <view class="record-info">
          <view class="record-account">{{ r.account }}</view>
          <view class="record-time">{{ r.time }}</view>
        </view>
        <view class="record-right">
          <view class="record-amount">¥{{ r.amount.toFixed(2) }}</view>
          <view class="record-state" :class="'rs-' + r.state">{{ stateText(r.state) }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getFunds, applyWithdrawMock } from '../../common/merchant'

// 可选到账账户
const accounts = ['余额账户', '招商银行(6222****1234)', '工商银行(6222****5678)']

// 资金数据（可提现余额、累计营收、累计手续费、提现记录等）
const funds = ref({ balance: 0, totalIncome: 0, totalServiceFee: 0, withdrawn: 0, records: [] })
// 提现金额输入
const amount = ref('')
// 当前选中的到账账户
const account = ref(accounts[0])

// 加载资金数据（金额字段转数字）
async function load() {
  try {
    const res = await getFunds()
    funds.value = {
      balance: Number(res.balance || 0),
      totalIncome: Number(res.totalIncome || 0),
      totalServiceFee: Number(res.totalServiceFee || 0),
      withdrawn: Number(res.withdrawn || 0),
      records: (res.records || []).map(r => ({ ...r, amount: Number(r.amount || 0) }))
    }
  } catch (e) {
    uni.showToast({ title: e.message || '资金数据加载失败', icon: 'none' })
  }
}

// 提现状态文案映射：0处理中 / 1已到账 / 2已失败
function stateText(state) {
  return { 0: '处理中', 1: '已到账', 2: '已失败' }[state] || '未知'
}

// 提交提现（校验金额非空、≥1 元且不超过余额，防重入，二次确认后调用后端接口）
const submitting = ref(false)
function submitWithdraw() {
  if (submitting.value) return
  const amt = parseFloat(amount.value)
  if (!amt || amt <= 0) {
    uni.showToast({ title: '请输入提现金额', icon: 'none' })
    return
  }
  if (amt < 1) {
    uni.showToast({ title: '单笔提现最低 ¥1.00', icon: 'none' })
    return
  }
  if (amt > funds.value.balance) {
    uni.showToast({ title: '超过可提现余额', icon: 'none' })
    return
  }
  uni.showModal({
    title: '提现确认',
    content: `提现 ¥${amt.toFixed(2)} 至「${account.value}」？`,
    success: (res) => {
      if (res.confirm) {
        submitting.value = true
        applyWithdrawMock(amt, account.value).then((ret) => {
          uni.showToast({ title: ret.message || '提现申请已提交', icon: 'none' })
          amount.value = ''
          load()
        }).catch((e) => {
          uni.showToast({ title: e.message || '提现失败', icon: 'none' })
        }).finally(() => {
          submitting.value = false
        })
      }
    }
  })
}

// 页面显示时加载资金数据
onShow(load)
</script>

<style scoped>
.page {
  padding: 20rpx 24rpx 60rpx;
}

.balance-card {
  background: linear-gradient(135deg, #2b6cff 0%, #6ea1ff 100%);
  border-radius: 20rpx;
  padding: 40rpx 30rpx;
  color: #ffffff;
}

.balance-label {
  font-size: 24rpx;
  opacity: 0.9;
}

.balance-num {
  margin-top: 12rpx;
  font-size: 64rpx;
  font-weight: 800;
}

.balance-sub {
  margin-top: 20rpx;
  display: flex;
  gap: 40rpx;
  font-size: 22rpx;
  opacity: 0.9;
}

.form-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.form-item {
  margin-bottom: 26rpx;
}

.form-label {
  display: block;
  font-size: 26rpx;
  color: #606266;
  margin-bottom: 12rpx;
}

.amount-row {
  display: flex;
  align-items: center;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 14rpx;
  padding: 0 24rpx;
  height: 88rpx;
}

.yen {
  font-size: 36rpx;
  font-weight: 700;
  color: #2b6cff;
  margin-right: 10rpx;
}

.amount-input {
  flex: 1;
  font-size: 34rpx;
}

.ph {
  color: #b0b3b8;
}

.all-btn {
  color: #2b6cff;
  font-size: 26rpx;
  padding: 8rpx 0 8rpx 20rpx;
}

.account-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.account-item {
  display: flex;
  align-items: center;
  gap: 14rpx;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 14rpx;
  padding: 22rpx 24rpx;
  font-size: 26rpx;
}

.account-item.active {
  border-color: #2b6cff;
  background: #eaf1ff;
}

.radio-dot {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  border: 2rpx solid #c6cdd8;
  box-sizing: border-box;
}

.radio-dot.on {
  border: 8rpx solid #2b6cff;
}

.withdraw-btn {
  margin-top: 10rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 32rpx;
  justify-content: center;
}

.withdraw-tip {
  margin-top: 16rpx;
  text-align: center;
  font-size: 22rpx;
  color: #8a8f99;
}

.record-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 2rpx solid #f2f5fa;
}

.record-row:last-child {
  border-bottom: none;
}

.record-account {
  font-size: 26rpx;
  font-weight: 600;
}

.record-time {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8a8f99;
}

.record-right {
  text-align: right;
}

.record-amount {
  font-size: 28rpx;
  font-weight: 700;
}

.record-state {
  margin-top: 6rpx;
  font-size: 22rpx;
}

.rs-0 { color: #ff9800; }
.rs-1 { color: #2ba471; }
.rs-2 { color: #8a8f99; }
</style>
