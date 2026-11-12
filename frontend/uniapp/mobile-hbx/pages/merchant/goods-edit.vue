<!--
  ============================================
  【前端 - 商家工作台（mobile 端 pages/merchant）】
  所属部分：商品管理
  界面名称：商品新增 / 编辑页
  ============================================
-->
<template>
  <view class="page">
    <view class="card">
      <view class="form-item">
        <text class="form-label">商品名称</text>
        <input v-model="form.name" class="form-input" placeholder="请输入商品名称" placeholder-class="ph" />
      </view>

      <view class="form-item">
        <text class="form-label">商品分类</text>
        <!-- 一级类目（来源：管理员端「商品类目」管理，/api/category/list 实时拉取） -->
        <view class="cat-row">
          <view
            v-for="r in catTree"
            :key="r.id"
            class="cat-chip"
            :class="{ active: activeRoot === r.name }"
            @tap="pickRoot(r)"
          >{{ r.name }}</view>
        </view>
        <!-- 二级类目：跟随选中的一级类目展开（可不选，直接挂在一级类目下） -->
        <view v-if="activeChildren.length" class="cat-row sub">
          <view
            v-for="c in activeChildren"
            :key="c.id"
            class="cat-chip sub"
            :class="{ active: form.category === c.name }"
            @tap="pickChild(c)"
          >{{ c.name }}</view>
        </view>
        <view v-if="categoryInvalid" class="cat-invalid-tip">
          当前分类「{{ form.category }}」已不在平台类目中，请重新选择
        </view>
      </view>

      <view class="form-item">
        <text class="form-label">商品价格（元）</text>
        <input v-model="form.price" class="form-input" type="digit" placeholder="0.00" placeholder-class="ph" />
      </view>

      <view class="form-item">
        <text class="form-label">库存数量</text>
        <input v-model="form.stock" class="form-input" type="number" placeholder="请输入库存" placeholder-class="ph" />
      </view>

      <view class="form-item">
        <text class="form-label">商品图片</text>
        <view class="img-row">
          <view class="goods-thumb" @tap="chooseImage">
            <image class="thumb-img" :src="previewSrc" mode="aspectFill" />
            <view class="thumb-mask">{{ uploading ? '上传中' : '点击上传' }}</view>
          </view>
          <view class="img-tip">点击图片可上传商品实拍图（jpg/png，不超过 2MB），上传后自动替换默认图</view>
        </view>
      </view>

      <view class="form-item">
        <text class="form-label">商品描述</text>
        <textarea v-model="form.desc" class="form-textarea" placeholder="请输入商品描述" placeholder-class="ph" :maxlength="200" />
      </view>

      <view class="form-item row">
        <text class="form-label">上架状态</text>
        <view class="state-switch" @tap="form.state = form.state === 1 ? 0 : 1">
          <view class="switch-track" :class="{ on: form.state === 1 }">
            <view class="switch-dot" />
          </view>
          <text class="switch-text">{{ form.state === 1 ? '上架' : '下架' }}</text>
        </view>
      </view>
    </view>

    <!-- 底部操作按钮组：保存 / 取消 -->
    <view class="bottom-bar">
      <view class="btn-primary save-btn" @tap="handleSave">保存</view>
      <view class="btn-plain cancel-btn" @tap="goBack">取消</view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMerchantGoods, saveMerchantGoods } from '../../common/merchant'
import { toast } from '../../common/toast'
import { getCategoryTree, uploadImage, imgSrc } from '../../common/api'

// 平台类目树（启用中的类目，与管理员端「商品类目」管理同源，实时拉取）
const catTree = ref([])          // 一级类目数组，children 为二级类目
const activeRoot = ref('')       // 当前展开二级类目的一级类目名
// 选中的一级/二级类目名集合（含全部层级），用于回显与校验
const allCatNames = computed(() => {
  const names = new Set()
  for (const r of catTree.value) {
    names.add(r.name)
    for (const c of r.children || []) names.add(c.name)
  }
  return names
})
// 编辑存量商品时分类可能已被管理员删除/停用
const categoryInvalid = computed(() => !!form.category && !allCatNames.value.has(form.category))

// 当前一级类目下的二级类目
const activeChildren = computed(() => (catTree.value.find(r => r.name === activeRoot.value)?.children) || [])

// 选择一级类目：商品挂在一级类目下，同时展开其二级类目
function pickRoot(r) {
  activeRoot.value = r.name
  form.category = r.name
}

// 选择二级类目：商品挂到具体二级类目
function pickChild(c) {
  form.category = c.name
}

// 拉取类目树；失败必须提示（分类数据是下单/浏览链路的一部分，禁止静默兜底为空）
async function loadCategories() {
  try {
    catTree.value = await getCategoryTree()
  } catch (e) {
    uni.showToast({ title: e.message || '分类加载失败', icon: 'none' })
  }
}

// 商品表单（新增/编辑共用；state 1上架 / 0下架）
const form = reactive({
  id: null,
  name: '',
  category: '',
  price: '',
  stock: '',
  image: '/static/goods/goods-1.jpg',
  desc: '',
  state: 1
})

// 图片上传状态与预览地址（/upload/ 开头的后端图片按运行端拼接完整 URL）
const uploading = ref(false)
const previewSrc = computed(() => imgSrc(form.image))

// 选图并上传商品图：成功后把后端返回的相对路径写入表单（随保存接口入库）
function chooseImage() {
  if (uploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: (res) => {
      const filePath = res.tempFilePaths && res.tempFilePaths[0]
      if (!filePath) return
      uploading.value = true
      uni.showLoading({ title: '上传图片中...' })
      uploadImage(filePath).then((body) => {
        if (body.path) form.image = body.path
        uni.hideLoading()
        toast('图片已上传')
      }).catch((e) => {
        uni.hideLoading()
        uni.showToast({ title: e.message || '图片上传失败', icon: 'none' })
      }).finally(() => {
        uploading.value = false
      })
    }
  })
}

// 页面加载：携带 id 为编辑（回填商品数据），否则为新增
onLoad(async (query) => {
  // 先加载类目树再回填，保证编辑时能正确回显类目选中态
  await loadCategories()
  if (query.id) {
    uni.setNavigationBarTitle({ title: '编辑商品' })
    try {
      const res = await getMerchantGoods()
      const g = (res.records || []).find(x => String(x.id) === String(query.id))
      if (g) {
        Object.assign(form, {
          id: g.id, name: g.name, category: g.category,
          price: String(g.price), stock: String(g.stock),
          image: g.image, desc: g.desc, state: g.state
        })
        // 回显类目选中态：命中的是二级类目时展开其父类目
        for (const r of catTree.value) {
          if (r.name === form.category) { activeRoot.value = r.name; break }
          if ((r.children || []).some(c => c.name === form.category)) { activeRoot.value = r.name; break }
        }
      } else {
        uni.showToast({ title: '商品不存在或已被删除', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 800)
        return
      }
    } catch (e) {
      // 回填失败必须提示并退出：form.id 保持为空时继续保存会把编辑误存为新增、产生重复商品
      uni.showToast({ title: e.message || '商品数据加载失败', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 800)
      return
    }
  } else {
    uni.setNavigationBarTitle({ title: '新增商品' })
  }
})

// 保存商品（表单校验：名称、价格必填且为有效正数，库存为非负整数，调用保存接口后返回列表）
async function handleSave() {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入商品名称', icon: 'none' })
    return
  }
  if (!form.category || categoryInvalid.value) {
    uni.showToast({ title: '请选择商品分类', icon: 'none' })
    return
  }
  const priceNum = parseFloat(form.price)
  // Number.isFinite 拦住 parseFloat 为 NaN 的输入（NaN <= 0 为 false，旧校验拦不住）
  if (!form.price || !Number.isFinite(priceNum) || priceNum <= 0) {
    uni.showToast({ title: '请输入正确的价格', icon: 'none' })
    return
  }
  const stockNum = Number(form.stock === '' || form.stock === null ? 0 : form.stock)
  // Number.isInteger 拦住 5.5 之类的小数（parseInt 会静默截断为 5，与提示不符）
  if (!Number.isInteger(stockNum) || stockNum < 0) {
    uni.showToast({ title: '库存必须为非负整数', icon: 'none' })
    return
  }
  try {
    await saveMerchantGoods({
      id: form.id || '',
      name: form.name.trim(),
      category: form.category,
      price: priceNum, // 价格：字符串转数字
      stock: stockNum, // 库存：非负整数
      image: form.image,
      desc: form.desc,
      state: form.state
    })
    toast('保存成功')
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  }
}

// 返回上一页（取消编辑）
function goBack() {
  uni.navigateBack()
}
</script>

<style scoped>
.page {
  padding: 20rpx 24rpx 180rpx;
}

.form-item {
  margin-bottom: 30rpx;
}

.form-item.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form-label {
  display: block;
  font-size: 26rpx;
  color: #606266;
  margin-bottom: 14rpx;
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

.cat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

/* 二级类目行：缩进展示在一级类目下方 */
.cat-row.sub {
  margin-top: 16rpx;
  padding-left: 24rpx;
}

.cat-chip.sub {
  background: #f7f9fd;
  border: 2rpx solid #e3eaf6;
}

.cat-invalid-tip {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #e64340;
}

.cat-chip {
  padding: 0 30rpx;
  height: 60rpx;
  line-height: 60rpx;
  background: #eef1f6;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #4e5560;
}

.cat-chip.active {
  background: #2b6cff;
  color: #ffffff;
}

.img-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.goods-thumb {
  position: relative;
  width: 150rpx;
  height: 150rpx;
  border-radius: 14rpx;
  overflow: hidden;
}

.thumb-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  color: #ffffff;
  font-size: 20rpx;
  text-align: center;
  padding: 6rpx 0;
}

.thumb-img {
  width: 100%;
  height: 100%;
}

.img-tip {
  flex: 1;
  font-size: 22rpx;
  color: #8a8f99;
}

.form-textarea {
  width: 100%;
  height: 180rpx;
  background: #f2f5fb;
  border: 2rpx solid #e3eaf6;
  border-radius: 14rpx;
  padding: 20rpx 26rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

/* 上架开关 */
.state-switch {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.switch-track {
  width: 84rpx;
  height: 44rpx;
  border-radius: 22rpx;
  background: #d6dbe4;
  padding: 4rpx;
  box-sizing: border-box;
  display: flex;
}

.switch-track.on {
  background: #2b6cff;
  justify-content: flex-end;
}

.switch-dot {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: #ffffff;
}

.switch-text {
  font-size: 24rpx;
  color: #606266;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  padding: 20rpx 40rpx calc(20rpx + env(safe-area-inset-bottom));
  display: flex;
  justify-content: center;
  gap: 30rpx;
}

.save-btn {
  min-width: 280rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 30rpx;
}

.cancel-btn {
  min-width: 200rpx;
  height: 80rpx;
  line-height: 78rpx;
  font-size: 30rpx;
  color: #4e5560;
  border-color: #d4dbe8;
}
</style>
