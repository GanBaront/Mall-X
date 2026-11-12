<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：个人中心
  界面名称：编辑资料（修改头像 / 昵称）
  ============================================
-->
<template>
  <view class="page">
    <!-- 头像 -->
    <view class="card">
      <view class="row" @tap="chooseAvatar">
        <text class="row-label">头像</text>
        <view class="row-right">
          <view class="avatar-preview">
            <image v-if="avatarSrc" :src="avatarSrc" class="avatar-img" mode="aspectFill" />
            <text v-else class="avatar-text">{{ avatarText }}</text>
          </view>
          <text class="row-hint">{{ uploading ? '上传中...' : '点击更换' }}</text>
        </view>
      </view>
      <view class="row">
        <text class="row-label">账号</text>
        <text class="row-value readonly">{{ store.user?.username || '-' }}</text>
      </view>
      <view class="row">
        <text class="row-label">用户编号</text>
        <text class="row-value readonly">{{ store.user?.userNum || '-' }}</text>
      </view>
    </view>

    <!-- 昵称 -->
    <view class="card">
      <view class="row">
        <text class="row-label">昵称</text>
        <input
          v-model="form.nickname"
          class="row-input"
          type="text"
          maxlength="20"
          placeholder="2-20 个字符"
          placeholder-class="ph"
        />
      </view>
    </view>

    <view class="save-btn" :class="{ disabled: saving }" @tap="handleSave">
      {{ saving ? '保存中...' : '保存' }}
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { store, updateProfile } from '../../common/store'
import { toast } from '../../common/toast'
import { getUserInfo, saveProfile, uploadImage, assetUrl } from '../../common/api'

const form = reactive({ nickname: '', avatar: '' })
const saving = ref(false)
const uploading = ref(false)

// 无头像图时展示昵称首字
const avatarText = computed(() => (form.nickname || store.user?.username || '客').slice(0, 1).toUpperCase())

// 头像预览地址（后端 /upload/... 相对路径 → 按运行端拼接）
const avatarSrc = ref('')
function refreshAvatar() {
  avatarSrc.value = assetUrl(form.avatar)
}

// 进入页面：拉取最新资料回填（防止本地缓存陈旧）
onShow(() => {
  if (!store.user) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
    return
  }
  getUserInfo().then((res) => {
    form.nickname = res.nickname || store.user.username || ''
    form.avatar = res.avatar || ''
    refreshAvatar()
  }).catch((e) => {
    uni.showToast({ title: e.message || '资料加载失败', icon: 'none' })
  })
})

// 更换头像：选图 → 上传后端 → 预览（点保存时才写库）
function chooseAvatar() {
  if (uploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: (res) => {
      const filePath = res.tempFilePaths && res.tempFilePaths[0]
      if (!filePath) return
      uploading.value = true
      uni.showLoading({ title: '上传头像中...' })
      uploadImage(filePath).then((body) => {
        form.avatar = body.path || ''
        refreshAvatar()
        uni.hideLoading()
        uni.showToast({ title: '头像已上传，请保存', icon: 'none' })
      }).catch((e) => {
        uni.hideLoading()
        uni.showToast({ title: e.message || '头像上传失败', icon: 'none' })
      }).finally(() => {
        uploading.value = false
      })
    }
  })
}

// 保存：昵称校验 + 提交后端（昵称/头像一并写入）→ 更新本地登录态
function handleSave() {
  if (saving.value) return
  const nickname = form.nickname.trim()
  if (nickname.length < 2 || nickname.length > 20) {
    uni.showToast({ title: '昵称需为 2-20 个字符', icon: 'none' })
    return
  }
  saving.value = true
  saveProfile({ nickname, avatar: form.avatar || '' }).then((res) => {
    updateProfile({
      nickname: res.nickname || nickname,
      avatar: res.avatar || ''
    })
    toast('资料已保存')
    setTimeout(() => uni.navigateBack(), 600)
  }).catch((e) => {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  }).finally(() => {
    saving.value = false
  })
}
</script>

<style scoped>
.page {
  padding: 24rpx;
}

.card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 0 30rpx;
  margin-bottom: 24rpx;
}

.row {
  display: flex;
  align-items: center;
  min-height: 108rpx;
  border-bottom: 2rpx solid #f2f5fa;
}

.row:last-child {
  border-bottom: none;
}

.row-label {
  width: 160rpx;
  font-size: 28rpx;
  color: #1f2329;
  flex-shrink: 0;
}

.row-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16rpx;
}

.row-hint {
  font-size: 24rpx;
  color: #8a8f99;
}

.avatar-preview {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #eaf1ff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.avatar-img {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
}

.avatar-text {
  font-size: 40rpx;
  font-weight: 700;
  color: #2b6cff;
}

.row-value {
  flex: 1;
  font-size: 28rpx;
  text-align: right;
}

.row-value.readonly {
  color: #8a8f99;
}

.row-input {
  flex: 1;
  font-size: 28rpx;
  text-align: right;
  color: #1f2329;
}

:deep(.ph),
.ph {
  color: #c0c4cc;
}

.save-btn {
  margin-top: 48rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: #2b6cff;
  color: #ffffff;
  border-radius: 20rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.save-btn.disabled {
  opacity: 0.6;
}
</style>
