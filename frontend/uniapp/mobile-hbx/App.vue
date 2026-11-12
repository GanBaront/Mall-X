<!--
  ============================================
  【前端 - 用户购物端（mobile）】
  所属部分：应用入口
  界面名称：App.vue（应用生命周期入口 / 全局样式）
  ============================================
-->
<script>
// 应用入口：定义 uni-app 生命周期（启动/显示/隐藏），仅做启动日志，其余逻辑在各页面
export default {
  onLaunch() {
    console.log('Mall-X 移动端启动')
  },
  onShow() {},
  onHide() {}
}
</script>

<style>
/* 全局样式：主题色与 PC 后台、原型图保持一致 */
page {
  background-color: #f5f7fb;
  color: #1f2329;
  font-size: 28rpx;
  font-family: 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif;
}

/* 主题色工具类 */
.text-primary {
  color: #2b6cff;
}
.bg-primary {
  background-color: #2b6cff;
}

/* 通用卡片 */
.card {
  background: #ffffff;
  border-radius: 20rpx;
  margin: 20rpx 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 10rpx rgba(43, 108, 255, 0.06);
}

/* 通用主按钮 */
.btn-primary {
  background: #2b6cff;
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 28rpx;
  padding: 0 36rpx;
  height: 64rpx;
  line-height: 64rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 通用描边按钮 */
.btn-plain {
  background: #ffffff;
  color: #2b6cff;
  border: 2rpx solid #2b6cff;
  border-radius: 999rpx;
  font-size: 28rpx;
  padding: 0 36rpx;
  height: 64rpx;
  line-height: 62rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 删除/危险操作 */
.btn-danger-plain {
  background: #fff1f0;
  color: #e64340;
  border: 2rpx solid #ffd6d4;
  border-radius: 999rpx;
  font-size: 24rpx;
  padding: 0 24rpx;
  height: 52rpx;
  line-height: 50rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 商品占位图 */
.goods-thumb {
  background: linear-gradient(160deg, #dce9ff 0%, #eef4ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7d9bff;
}
</style>
