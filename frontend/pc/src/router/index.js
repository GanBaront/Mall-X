import { createRouter, createWebHistory } from 'vue-router'

/*
 * 路由配置：PC 平台管理后台全部页面路由
 * 平台管理后台（PC 端）路由；用户端购物页面后续迁移至 uni-app 移动端，不再挂载于此。
 * 含登录页、公共布局（侧边栏菜单）及内部各业务页；并设登录守卫校验 admin_token。
 */

const routes = [
  {
    path: '/',
    redirect: '/admin/dashboard'
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('../views/admin/Login.vue'),
    meta: { title: '管理员登录' }
  },
  {
    path: '/admin',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/admin/Dashboard.vue'),
        meta: { title: '主页', icon: 'HomeFilled' }
      },
      // 商户管理
      {
        path: 'merchant/list',
        name: 'MerchantList',
        component: () => import('../views/admin/MerchantList.vue'),
        meta: { title: '商户列表', menu: '商户管理' }
      },
      {
        path: 'merchant/app',
        name: 'AppList',
        component: () => import('../views/admin/AppList.vue'),
        meta: { title: '应用列表', menu: '商户管理' }
      },
      {
        path: 'category/list',
        name: 'CategoryManage',
        component: () => import('../views/admin/CategoryManage.vue'),
        meta: { title: '商品类目', menu: '商户管理' }
      },
      // 服务商管理
      {
        path: 'isv/list',
        name: 'IsvList',
        component: () => import('../views/admin/IsvList.vue'),
        meta: { title: '服务商列表', menu: '服务商管理' }
      },
      // 订单管理
      {
        path: 'order/pay',
        name: 'PayOrderList',
        component: () => import('../views/admin/PayOrderList.vue'),
        meta: { title: '支付订单', menu: '订单管理' }
      },
      {
        path: 'order/refund',
        name: 'RefundOrderList',
        component: () => import('../views/admin/RefundOrderList.vue'),
        meta: { title: '退款订单', menu: '订单管理' }
      },
      {
        path: 'order/transfer',
        name: 'TransferOrderList',
        component: () => import('../views/admin/TransferOrderList.vue'),
        meta: { title: '转账订单', menu: '订单管理' }
      },
      {
        path: 'order/notice',
        name: 'MerchantNotice',
        component: () => import('../views/admin/MerchantNotice.vue'),
        meta: { title: '商户通知', menu: '订单管理' }
      },
      // 支付配置
      {
        path: 'config/channel',
        name: 'ChannelConfig',
        component: () => import('../views/admin/ChannelConfig.vue'),
        meta: { title: '支付接口', menu: '支付配置' }
      },
      {
        path: 'config/way',
        name: 'PayWayList',
        component: () => import('../views/admin/PayWayList.vue'),
        meta: { title: '支付方式', menu: '支付配置' }
      },
      // 系统管理
      {
        path: 'system/user',
        name: 'UserRole',
        component: () => import('../views/admin/UserRole.vue'),
        meta: { title: '用户角色管理', menu: '系统管理' }
      },
      {
        path: 'system/log',
        name: 'OperateLog',
        component: () => import('../views/admin/OperateLog.vue'),
        meta: { title: '系统日志', menu: '系统管理' }
      }
    ]
  },
  // 兼容旧路径，统一回到后台主页
  { path: '/:pathMatch(.*)*', redirect: '/admin/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫：未登录的管理员跳转登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.path.startsWith('/admin') && to.path !== '/admin/login' && !token) {
    next('/admin/login')
  } else if (to.path === '/admin/login' && token) {
    next('/admin/dashboard')
  } else {
    next()
  }
})

export default router
