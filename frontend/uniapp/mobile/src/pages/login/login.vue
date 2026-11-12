<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：登录
  界面名称：统一登录（登录 / 注册，按角色分流）
  ============================================
-->
<template>
  <view class="page">
    <!-- Logo 区 -->
    <view class="logo-area">
      <view class="logo-box">M</view>
      <view class="logo-text">Mall-X</view>
      <view class="logo-sub">多商家电商平台</view>
    </view>

    <!-- 登录/注册切换 -->
    <view class="mode-tabs">
      <view class="mode-tab" :class="{ active: mode === 'login' }" @tap="mode = 'login'">登录</view>
      <view class="mode-tab" :class="{ active: mode === 'register' }" @tap="mode = 'register'">注册</view>
    </view>

    <!-- 表单 -->
    <view class="form-card">
      <template v-if="mode === 'register'">
        <!-- 账号类型：买家（免入驻资料）/ 商家（需填写入驻资料），置于账号输入框上方 -->
        <view class="reg-type">
          <view class="reg-type-item" :class="{ active: regType === 'buyer' }" @tap="regType = 'buyer'">买家账号</view>
          <view class="reg-type-item" :class="{ active: regType === 'merchant' }" @tap="regType = 'merchant'">商家入驻</view>
        </view>
      </template>
      <view class="form-item">
        <input v-model="form.username" class="form-input" placeholder="请输入账号" placeholder-class="ph" />
      </view>
      <view class="form-item">
        <input v-model="form.password" class="form-input" password placeholder="请输入密码" placeholder-class="ph" />
      </view>
      <template v-if="mode === 'register'">
        <view class="form-item">
          <input v-model="form.confirmPassword" class="form-input" password placeholder="请再次输入密码" placeholder-class="ph" />
        </view>
        <template v-if="regType === 'merchant'">
          <view class="reg-section">入驻资料</view>
          <view class="form-item">
            <input v-model="form.shopName" class="form-input" placeholder="店铺名称" placeholder-class="ph" />
          </view>
          <view class="form-item">
            <input v-model="form.shopPhone" class="form-input" type="number" placeholder="联系电话" placeholder-class="ph" />
          </view>
          <view class="form-item">
            <input v-model="form.shopAddress" class="form-input" placeholder="店铺地址" placeholder-class="ph" />
          </view>
        </template>
      </template>

      <view class="submit-btn" @tap="handleSubmit">{{ mode === 'login' ? '登 录' : '注 册' }}</view>

      <view class="role-tip">
        <view>{{ registerTip }}</view>
        <view class="role-accounts">演示：user / 123456（购物端）　merchant / 123456（商家工作台）</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { uniLogin, userRegister, mchRegister } from '../../common/merchant'
import { toast } from '../../common/toast'
import { login, store } from '../../common/store'

// 当前模式：login=登录，register=注册（买家/商家入驻二选一）
const mode = ref('login')
// 注册账号类型：buyer=买家（免入驻资料，注册后直接进入购物端）/ merchant=商家入驻
const regType = ref('buyer')
// 表单数据：账号、密码、二次确认密码 + 入驻资料（店铺名称/电话/地址）
const form = reactive({ username: '', password: '', confirmPassword: '', shopName: '', shopPhone: '', shopAddress: '' })

// 底部提示文案
const registerTip = computed(() => {
  if (mode.value === 'login') return '用户账号进入购物端，商家账号进入商家工作台'
  return regType.value === 'buyer'
    ? '买家账号无需入驻审核，注册成功后直接进入购物端'
    : '商家入驻需平台审核，审核通过后进入商家工作台'
})

// 已登录则直接进入对应端（商家进工作台，用户进首页）
onShow(() => {
  if (store.user) {
    if (store.user.role === 'merchant') {
      uni.reLaunch({ url: '/pages/merchant/index' })
    } else {
      uni.switchTab({ url: '/pages/index/index' })
    }
  }
})

// 提交：注册模式先建账号，登录模式校验后按角色分流到购物端/商家工作台
async function handleSubmit() {
  if (!form.username.trim() || !form.password) {
    uni.showToast({ title: '请填写账号和密码', icon: 'none' })
    return
  }
  if (mode.value === 'register' && form.password !== form.confirmPassword) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }
  if (mode.value === 'register' && regType.value === 'merchant'
      && (!form.shopName.trim() || !form.shopPhone.trim() || !form.shopAddress.trim())) {
    uni.showToast({ title: '请完整填写入驻资料', icon: 'none' })
    return
  }
  uni.showLoading({ title: '提交中...' })
  try {
    // 注册-买家：仅账号+密码，成功后自动登录并进入购物端
    if (mode.value === 'register' && regType.value === 'buyer') {
      await userRegister(form.username.trim(), form.password)
      const res = await uniLogin(form.username.trim(), form.password)
      login(form.username.trim(), res)
      uni.hideLoading()
      toast('注册成功')
      setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 800)
      return
    }
    // 注册-商家入驻：提交店铺资料 + 账号密码，等待平台审核后登录
    if (mode.value === 'register') {
      await mchRegister({
        username: form.username.trim(),
        password: form.password,
        shopName: form.shopName.trim(),
        shopPhone: form.shopPhone.trim(),
        shopAddress: form.shopAddress.trim()
      })
      uni.hideLoading()
      toast('注册成功，等待平台审核')
      mode.value = 'login'
      return
    }
    // 登录：后端真实校验账号密码，按角色返回
    const res = await uniLogin(form.username.trim(), form.password)
    if (res.role === 'admin') {
      // 管理员不给移动端写入登录态（否则后续页面把 admin 当普通用户，接口全部查不到数据）
      uni.hideLoading()
      uni.showToast({ title: '管理员请使用 PC 管理后台登录', icon: 'none' })
      return
    }
    login(form.username.trim(), res)
    uni.hideLoading()
    if (res.role === 'merchant') {
      toast('登录成功')
      setTimeout(() => uni.reLaunch({ url: '/pages/merchant/index' }), 800)
    } else {
      toast('登录成功')
      setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 800)
    }
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #2b6cff 0%, #6ea1ff 30%, #f5f7fb 60%);
  padding: 0 50rpx;
}

/* Logo */
.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0 50rpx;
}

.logo-box {
  width: 130rpx;
  height: 130rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64rpx;
  font-weight: 800;
  color: #ffffff;
}

.logo-text {
  margin-top: 24rpx;
  color: #ffffff;
  font-size: 52rpx;
  font-weight: 800;
  letter-spacing: 4rpx;
}

.logo-sub {
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.9);
  font-size: 26rpx;
  letter-spacing: 2rpx;
}

/* 登录/注册 Tab */
.mode-tabs {
  display: flex;
  justify-content: center;
  gap: 60rpx;
  margin: 30rpx 0 26rpx;
}

/* 注册模式入驻资料小节标题 */
.reg-section {
  font-size: 24rpx;
  color: #4e5560;
  font-weight: 600;
  margin: 10rpx 4rpx 2rpx;
}

/* 注册账号类型选择（买家/商家入驻） */
.reg-type {
  display: flex;
  gap: 16rpx;
  margin-bottom: 6rpx;
}

.reg-type-item {
  flex: 1;
  height: 68rpx;
  line-height: 68rpx;
  text-align: center;
  background: #f2f4f8;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: #4e5560;
}

.reg-type-item.active {
  background: #eaf1ff;
  color: #2b6cff;
  font-weight: 700;
  border: 2rpx solid #2b6cff;
}
.mode-tabs {
  display: flex;
  justify-content: center;
  gap: 80rpx;
  margin-bottom: 30rpx;
}

.mode-tab {
  font-size: 32rpx;
  color: rgba(255, 255, 255, 0.85);
  padding-bottom: 12rpx;
  border-bottom: 6rpx solid transparent;
}

.mode-tab.active {
  color: #ffffff;
  font-weight: 700;
  border-bottom-color: #ffffff;
}

/* 表单 */
.form-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 44rpx 36rpx 36rpx;
  box-shadow: 0 12rpx 40rpx rgba(31, 91, 224, 0.12);
}

.form-item {
  margin-bottom: 28rpx;
}

.form-input {
  height: 92rpx;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 16rpx;
  padding: 0 30rpx;
  font-size: 28rpx;
}

.ph {
  color: #b0b3b8;
}

.submit-btn {
  margin-top: 12rpx;
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  background: #2b6cff;
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 600;
  letter-spacing: 8rpx;
}

.role-tip {
  margin-top: 26rpx;
  text-align: center;
  font-size: 22rpx;
  color: #8a8f99;
  line-height: 1.7;
}

.role-accounts {
  color: #2b6cff;
}
</style>
