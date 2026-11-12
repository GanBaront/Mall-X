<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：公共布局
  界面名称：管理后台整体框架页（侧边菜单 + 顶部栏 + 内容区）
  ============================================
-->
<template>
  <el-container class="admin-layout">
    <!-- 左侧菜单 -->
    <el-aside width="200px" class="sidebar">
      <div class="logo-area">
        <span class="logo-icon">支</span>
        <span class="logo-text">MALL-X</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        router
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>主页</span>
        </el-menu-item>

        <el-sub-menu index="merchant">
          <template #title>
            <el-icon><Shop /></el-icon>
            <span>商户管理</span>
          </template>
          <el-menu-item index="/admin/merchant/list">商户列表</el-menu-item>
          <el-menu-item index="/admin/merchant/app">应用列表</el-menu-item>
          <el-menu-item index="/admin/category/list">商品类目</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="isv">
          <template #title>
            <el-icon><Message /></el-icon>
            <span>服务商管理</span>
          </template>
          <el-menu-item index="/admin/isv/list">服务商列表</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="order">
          <template #title>
            <el-icon><Tickets /></el-icon>
            <span>订单管理</span>
          </template>
          <el-menu-item index="/admin/order/pay">支付订单</el-menu-item>
          <el-menu-item index="/admin/order/refund">退款订单</el-menu-item>
          <el-menu-item index="/admin/order/transfer">转账订单</el-menu-item>
          <el-menu-item index="/admin/order/notice">商户通知</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="config">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>支付配置</span>
          </template>
          <el-menu-item index="/admin/config/channel">支付接口</el-menu-item>
          <el-menu-item index="/admin/config/way">支付方式</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="system">
          <template #title>
            <el-icon><Tools /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/admin/system/user">用户角色管理</el-menu-item>
          <el-menu-item index="/admin/system/log">系统日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header height="56px" class="topbar">
        <div class="page-title">
          <span class="title-text">{{ currentTitle }}</span>
        </div>
        <div class="topbar-right">
          <el-dropdown @command="handleCommand">
            <span class="admin-user">
              <el-avatar :size="34" class="admin-avatar">A</el-avatar>
              <span class="admin-name">{{ adminName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

// 当前激活菜单：取当前路由路径，使侧边栏高亮与页面同步
const activeMenu = computed(() => route.path)
// 顶部标题：读取路由 meta.title，缺省显示「平台管理」
const currentTitle = computed(() => route.meta.title || '平台管理')

// 当前登录管理员名称（本地缓存读取，缺省 admin）
const adminName = computed(() => localStorage.getItem('admin_name') || 'admin')

// 处理顶部用户下拉菜单指令：退出登录时清除 token/名称缓存并跳回登录页
function handleCommand(cmd) {
  if (cmd === 'logout') {
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_name')
    ElMessage.success('已退出登录')
    router.push('/admin/login')
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}

/* ===== 侧边栏 ===== */
.sidebar {
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.logo-area {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.logo-icon {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #1f2329;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #1f2329;
}

.sidebar-menu {
  border-right: none;
  padding: 8px 0;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  color: #303133;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: #e8f1ff;
  color: var(--el-color-primary);
  border-right: 2px solid var(--el-color-primary);
}

.sidebar-menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  color: #303133;
}

.sidebar-menu :deep(.el-sub-menu__title:hover),
.sidebar-menu :deep(.el-menu-item:hover) {
  background-color: #f5f8ff;
}

/* ===== 顶部栏 ===== */
.topbar {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.page-title {
  display: flex;
  align-items: center;
}

.title-text {
  font-size: 16px;
  color: var(--el-color-primary);
  font-weight: 600;
  padding-bottom: 4px;
  border-bottom: 2px solid var(--el-color-primary);
}

.topbar-right {
  display: flex;
  align-items: center;
}

.admin-user {
  display: flex;
  align-items: center;
  cursor: pointer;
  outline: none;
}

.admin-avatar {
  background: #9dd3f5;
  color: #fff;
  font-weight: 600;
}

.admin-name {
  margin: 0 6px 0 8px;
  color: #303133;
}

/* ===== 内容区 ===== */
.main-content {
  padding: 0;
  overflow-y: auto;
}
</style>
