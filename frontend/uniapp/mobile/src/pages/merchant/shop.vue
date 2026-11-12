<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：店铺管理
  界面名称：店铺设置页
  ============================================
-->
<template>
  <view class="page">
    <!-- 店铺基础信息表单 -->
    <view class="card">
      <view class="form-title">店铺基础信息</view>

      <view class="form-item">
        <text class="form-label">店铺名称</text>
        <input v-model="form.shopName" class="form-input" placeholder="请输入店铺名称" placeholder-class="ph" />
      </view>

      <view class="form-item">
        <text class="form-label">联系电话</text>
        <input v-model="form.shopPhone" class="form-input" type="number" placeholder="请输入联系电话" placeholder-class="ph" />
      </view>

      <view class="form-item">
        <text class="form-label">经营地址</text>
        <input v-model="form.shopAddress" class="form-input" placeholder="请输入经营地址" placeholder-class="ph" />
      </view>

      <view class="form-item">
        <text class="form-label">店铺简介</text>
        <textarea v-model="form.shopDesc" class="form-textarea" placeholder="介绍一下你的店铺" placeholder-class="ph" :maxlength="120" />
      </view>

      <view class="btn-primary save-btn" @tap="handleSave">保存修改</view>
    </view>

    <!-- 账号信息（只读展示） -->
    <view class="card">
      <view class="form-title">账号信息</view>
      <view class="info-row"><text class="info-label">商户编号</text><text class="mono">{{ form.shopNum }}</text></view>
      <view class="info-row"><text class="info-label">入驻时间</text><text>{{ form.createdAt }}</text></view>
      <view class="info-row">
        <text class="info-label">店铺状态</text>
        <text :class="form.shopState === 1 ? 'ok' : 'bad'">{{ form.shopState === 1 ? '正常经营' : '异常' }}</text>
      </view>
    </view>

    <!-- 退出登录危险区 -->
    <view class="card danger-zone">
      <view class="danger-btn" @tap="handleLogout">退出登录</view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getShopInfo, saveShopInfoMock } from '../../common/merchant'
import { toast } from '../../common/toast'
import { store, logout } from '../../common/store'

// 店铺信息表单（基础信息 + 账号信息）
const form = reactive({
  shopNum: '', shopName: '', shopPhone: '', shopAddress: '', shopDesc: '', shopState: 1, createdAt: ''
})

// 加载店铺信息（回填表单）
async function load() {
  try {
    const res = await getShopInfo()
    Object.assign(form, res.record)
  } catch (e) {
    uni.showToast({ title: e.message || '店铺信息加载失败', icon: 'none' })
  }
}

// 保存店铺信息（校验名称、电话必填，调用 mock 保存）
function handleSave() {
  if (!form.shopName.trim() || !form.shopPhone.trim()) {
    uni.showToast({ title: '店铺名称和电话必填', icon: 'none' })
    return
  }
  saveShopInfoMock({
    shopName: form.shopName,
    shopPhone: form.shopPhone,
    shopAddress: form.shopAddress,
    shopDesc: form.shopDesc
  }).then(() => {
    toast('保存成功')
  }).catch((e) => {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  })
}

// 退出商家登录（二次确认后清除登录态并跳转到登录页）
function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出商家登录？',
    success: (res) => {
      if (res.confirm) {
        logout()
        uni.reLaunch({ url: '/pages/login/login' })
      }
    }
  })
}

// 页面显示时加载店铺信息
onShow(load)
</script>

<style scoped>
.page {
  padding: 20rpx 24rpx 60rpx;
}

.form-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 24rpx;
}

.form-item {
  margin-bottom: 28rpx;
}

.form-label {
  display: block;
  font-size: 26rpx;
  color: #606266;
  margin-bottom: 12rpx;
}

.form-input {
  height: 84rpx;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 14rpx;
  padding: 0 26rpx;
  font-size: 28rpx;
}

.ph {
  color: #b0b3b8;
}

.form-textarea {
  width: 100%;
  height: 160rpx;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 14rpx;
  padding: 20rpx 26rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.save-btn {
  height: 84rpx;
  line-height: 84rpx;
  font-size: 30rpx;
  justify-content: center;
}

.info-row {
  display: flex;
  padding: 18rpx 0;
  font-size: 26rpx;
  border-bottom: 2rpx solid #f2f5fa;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  width: 160rpx;
  color: #8a8f99;
}

.mono {
  font-size: 24rpx;
}

.ok { color: #2ba471; }
.bad { color: #e64340; }

.danger-zone {
  padding: 0;
}

.danger-btn {
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  color: #e64340;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
