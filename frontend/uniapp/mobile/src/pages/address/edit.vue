<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：收货地址
  界面名称：收货地址编辑（表单 / 省市区抽屉 / 保存）
  ============================================
-->
<template>
  <view class="page">
    <!-- 表单卡片 -->
    <view class="form-card">
      <!-- 地址（省市区） -->
      <view class="form-row" @tap="openRegion">
        <text class="row-label">地址</text>
        <view class="row-main">
          <text class="region-text" :class="{ placeholder: !regionText }">{{ regionText || '选择收货地址' }}</text>
        </view>
        <view class="locate-btn" @tap.stop="handleLocate">
          <text class="locate-icon">📍</text>
          <text class="locate-text">定位</text>
        </view>
        <text class="row-arrow">›</text>
      </view>

      <!-- 门牌号 / 具体位置 -->
      <view class="form-row">
        <text class="row-label">门牌号</text>
        <input
          v-model="form.doorNo"
          class="row-input"
          placeholder="输入详细地址，如出入口、楼栋、门牌号"
          placeholder-class="ph"
        />
      </view>

      <!-- 联系人 -->
      <view class="form-row">
        <text class="row-label">联系人</text>
        <input v-model="form.name" class="row-input" placeholder="输入收货人姓名" placeholder-class="ph" />
        <view class="gender-group">
          <view class="gender-item" :class="{ active: form.gender === '先生' }" @tap="form.gender = '先生'">
            <view class="radio-dot" :class="{ on: form.gender === '先生' }" />
            <text>先生</text>
          </view>
          <view class="gender-item" :class="{ active: form.gender === '女士' }" @tap="form.gender = '女士'">
            <view class="radio-dot" :class="{ on: form.gender === '女士' }" />
            <text>女士</text>
          </view>
        </view>
      </view>

      <!-- 手机号 -->
      <view class="form-row">
        <text class="row-label">手机号</text>
        <view class="phone-prefix">+86</view>
        <input
          v-model="form.phone"
          class="row-input"
          type="number"
          maxlength="11"
          placeholder="输入收货人手机号"
          placeholder-class="ph"
        />
      </view>

      <!-- 标签 -->
      <view class="form-row">
        <text class="row-label">标签</text>
        <view class="tag-group">
          <view
            v-for="t in tags"
            :key="t"
            class="tag-chip"
            :class="{ active: form.tag === t }"
            @tap="form.tag = t"
          >{{ t }}</view>
        </view>
      </view>
    </view>

    <!-- 保存按钮 -->
    <view class="save-btn" @tap="handleSave">保存地址</view>

    <!-- 省市区侧拉抽屉 -->
    <view class="drawer-mask" :class="{ show: drawerVisible }" @tap="closeRegion" />
    <view class="drawer-panel" :class="{ show: drawerVisible }">
      <view class="drawer-head">
        <text class="drawer-title">选择收货地址</text>
        <text class="drawer-close" @tap="closeRegion">✕</text>
      </view>

      <!-- 层级面包屑：省 / 市 / 区县 -->
      <view class="crumb-bar">
        <text class="crumb-item" :class="{ active: level === 1 }" @tap="backTo(1)">请选择省</text>
        <template v-if="level >= 2">
          <text class="crumb-sep">›</text>
          <text class="crumb-item" :class="{ active: level === 2 }" @tap="backTo(2)">{{ picked.province.name }}</text>
        </template>
        <template v-if="level >= 3">
          <text class="crumb-sep">›</text>
          <text class="crumb-item" :class="{ active: level === 3 }">{{ picked.city.name }}</text>
        </template>
      </view>

      <scroll-view scroll-y class="region-list">
        <template v-if="level === 1">
          <view
            v-for="p in regionTree"
            :key="p.code"
            class="region-item"
            @tap="pickProvince(p)"
          >{{ p.name }}</view>
        </template>
        <template v-else-if="level === 2">
          <view
            v-for="c in picked.province.children"
            :key="c.code"
            class="region-item"
            @tap="pickCity(c)"
          >{{ c.name }}</view>
        </template>
        <template v-else>
          <view
            v-for="d in picked.city.children"
            :key="d.code"
            class="region-item"
            @tap="pickDistrict(d)"
          >{{ d.name }}</view>
        </template>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { regionTree } from '../../common/region-data'
import { store, saveAddress } from '../../common/store'
import { toast } from '../../common/toast'
import { locate } from '../../common/location'

// 地址标签可选项
const tags = ['家', '公司', '学校', '父母家']

// 表单数据：省市区 / 门牌号 / 联系人 / 性别 / 手机号 / 标签
const form = reactive({
  id: null,
  region: { province: '', city: '', district: '' },
  doorNo: '',
  name: '',
  gender: '先生',
  phone: '',
  tag: '家'
})

// 抽屉状态：drawerVisible 是否展开，level 当前选择的层级（1省/2市/3区县）
const drawerVisible = ref(false)
const level = ref(1)
// 已选中的省/市节点
const picked = reactive({ province: null, city: null })

// 省市区回显文本：拼接省/市/区县
const regionText = computed(() => {
  const r = form.region
  if (!r.province) return ''
  return [r.province, r.city, r.district].filter(Boolean).join(' ')
})

// 编辑模式回填（?id=xxx），新增模式不回填
onLoad((query) => {
  if (query.id) {
    uni.setNavigationBarTitle({ title: '编辑收货地址' })
    const a = (store.addressList || []).find(x => x.id === query.id)
    if (a) {
      form.id = a.id
      form.region = { ...(a.region || { province: '', city: '', district: '' }) }
      form.doorNo = a.doorNo || ''
      form.name = a.name || ''
      form.gender = a.gender || '先生'
      form.phone = a.phone || ''
      form.tag = a.tag || '家'
    }
  }
})

// 已有地址回填（兼容直接打开页面的场景）
onShow(() => {
  if (form.id) return
  const a = store.address || {}
  if (a.region && a.region.province && !form.region.province && !form.name) {
    form.region = { ...a.region }
    form.doorNo = a.doorNo || ''
    form.name = a.name || ''
    form.gender = a.gender || '先生'
    form.phone = a.phone || ''
    form.tag = a.tag || '家'
  }
})

/* ---- 省市区抽屉 ---- */
function openRegion() {
  drawerVisible.value = true
  // 已选过则回到对应层级继续选
  if (form.region.province && form.region.city) {
    picked.province = regionTree.find(p => p.name === form.region.province) || null
    picked.city = picked.province
      ? picked.province.children.find(c => c.name === form.region.city) || null
      : null
    level.value = picked.city ? 3 : picked.province ? 2 : 1
  } else {
    level.value = 1
  }
}

// 关闭省市区抽屉
function closeRegion() {
  drawerVisible.value = false
}

// 选择省：记录省份、清空市/区县，进入市级层级
function pickProvince(p) {
  picked.province = p
  picked.city = null
  form.region = { province: p.name, city: '', district: '' }
  level.value = 2
}

// 选择市：记录城市、清空区县，进入区县级层级
function pickCity(c) {
  picked.city = c
  form.region.city = c.name
  form.region.district = ''
  level.value = 3
}

// 选择区县：记录区县并关闭抽屉
function pickDistrict(d) {
  form.region.district = d.name
  drawerVisible.value = false
}

// 面包屑回退：跳转到指定层级（只能往已选过的上级回退）
function backTo(lv) {
  if (lv < level.value) level.value = lv
}

/* ---- 定位：读取当前位置自动填入省市区与街道门牌 ---- */
const locating = ref(false)
async function handleLocate() {
  if (locating.value) return
  locating.value = true
  uni.showLoading({ title: '正在定位...' })
  try {
    const loc = await locate()
    // 回填省市区（与省市区抽屉、保存校验的名称格式一致，均带"省/市/区"后缀）
    form.region = { province: loc.province, city: loc.city, district: loc.district }
    // 门牌号为空时自动填入街道+门牌（如"园东路8号"），已有内容不覆盖用户输入
    if (!form.doorNo.trim() && loc.recommend) form.doorNo = loc.recommend
    uni.hideLoading()
    if (!loc.district) {
      uni.showToast({ title: '未能解析到区县，请手动选择省市区', icon: 'none' })
    } else {
      uni.showToast({ title: '已自动填入定位地址', icon: 'none' })
    }
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '定位失败', icon: 'none' })
  } finally {
    locating.value = false
  }
}

/* ---- 保存 ---- */
function handleSave() {
  if (!form.region.district) {
    uni.showToast({ title: '请先选择省市区', icon: 'none' })
    return
  }
  if (!form.doorNo.trim()) {
    uni.showToast({ title: '请填写门牌号等详细地址', icon: 'none' })
    return
  }
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入收货人姓名', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(form.phone.trim())) {
    uni.showToast({ title: '请输入正确的 11 位手机号', icon: 'none' })
    return
  }
  saveAddress({
    id: form.id || undefined,
    name: form.name.trim(),
    phone: form.phone.trim(),
    gender: form.gender,
    tag: form.tag,
    region: { ...form.region },
    doorNo: form.doorNo.trim(),
    // detail 保持完整地址文本，供订单确认页等直接展示
    detail: regionText.value + ' ' + form.doorNo.trim()
  })
  toast('地址已保存')
  setTimeout(() => uni.navigateBack(), 800)
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx 24rpx 60rpx;
}

.form-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 8rpx 26rpx;
}

.form-row {
  display: flex;
  align-items: center;
  padding: 30rpx 0;
  border-bottom: 2rpx solid #f2f4f8;
}

.form-row:last-child {
  border-bottom: none;
}

.row-label {
  width: 130rpx;
  font-size: 28rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.row-main {
  flex: 1;
  min-width: 0;
}

.region-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2329;
}

.region-text.placeholder {
  color: #b0b3b8;
  font-weight: 400;
}

.row-arrow {
  color: #c0c4cc;
  font-size: 36rpx;
  margin-left: 10rpx;
}

/* 定位按钮（预留功能位） */
.locate-btn {
  display: flex;
  align-items: center;
  gap: 4rpx;
  background: #eaf1ff;
  border-radius: 999rpx;
  padding: 8rpx 20rpx;
  margin-right: 12rpx;
}

.locate-icon {
  font-size: 24rpx;
}

.locate-text {
  font-size: 22rpx;
  color: #2b6cff;
}

.row-input {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
}

.ph {
  color: #b0b3b8;
}

/* 联系人性别 */
.gender-group {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.gender-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 26rpx;
  color: #8a8f99;
}

.gender-item.active {
  color: #1f2329;
}

.radio-dot {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  border: 2rpx solid #c6cdd8;
  box-sizing: border-box;
}

.radio-dot.on {
  border: 9rpx solid #ffb800;
}

/* 手机号前缀 */
.phone-prefix {
  font-size: 30rpx;
  font-weight: 700;
  margin-right: 16rpx;
  flex-shrink: 0;
}

/* 标签 */
.tag-group {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}

.tag-chip {
  background: #f2f5fb;
  border-radius: 12rpx;
  padding: 12rpx 30rpx;
  font-size: 26rpx;
  color: #4e5560;
}

.tag-chip.active {
  background: #eaf1ff;
  color: #2b6cff;
  font-weight: 600;
}

/* 保存 */
.save-btn {
  margin-top: 44rpx;
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  background: #2b6cff;
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 600;
}

/* ---- 省市区侧拉抽屉 ---- */
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.25s;
  z-index: 998;
}

.drawer-mask.show {
  opacity: 1;
  pointer-events: auto;
}

.drawer-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 78%;
  background: #ffffff;
  z-index: 999;
  transform: translateX(100%);
  transition: transform 0.25s ease-out;
  display: flex;
  flex-direction: column;
  border-radius: 24rpx 0 0 24rpx;
  overflow: hidden;
}

.drawer-panel.show {
  transform: translateX(0);
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 34rpx 30rpx 20rpx;
}

.drawer-title {
  font-size: 32rpx;
  font-weight: 700;
}

.drawer-close {
  font-size: 34rpx;
  color: #8a8f99;
  padding: 0 10rpx;
}

/* 面包屑 */
.crumb-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8rpx;
  padding: 0 30rpx 20rpx;
  border-bottom: 2rpx solid #f2f4f8;
}

.crumb-item {
  font-size: 26rpx;
  color: #8a8f99;
  padding: 6rpx 12rpx;
  border-radius: 8rpx;
}

.crumb-item.active {
  color: #2b6cff;
  font-weight: 700;
  background: #eaf1ff;
}

.crumb-sep {
  color: #c0c4cc;
  font-size: 26rpx;
}

.region-list {
  flex: 1;
  height: 0;
}

.region-item {
  padding: 28rpx 34rpx;
  font-size: 28rpx;
  border-bottom: 2rpx solid #f7f8fb;
}

.region-item:active {
  background: #f2f6ff;
}
</style>
