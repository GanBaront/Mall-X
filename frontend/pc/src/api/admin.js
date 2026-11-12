import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 平台管理端 API 封装
 * 网关地址遵循《行业工程实践》实验指导书：https://localhost:10010
 * 全部走真实后端接口，失败/业务错误统一抛出，由页面捕获提示。
 */

const gateway = axios.create({
  baseURL: '/gateway',
  timeout: 3000
})

gateway.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.token = token
  }
  return config
})

/**
 * 登录失效统一处理：清除无效 token 并跳转登录页。
 * 否则坏 token 会让路由守卫把用户锁死在业务页（有 token 时 /admin/login 会被重定向回来）。
 */
function handleAuthExpired(message) {
  localStorage.removeItem('admin_token')
  if (!window.location.pathname.startsWith('/admin/login')) {
    ElMessage.error(message || '登录状态无效，请重新登录')
    window.location.href = '/admin/login'
  }
}

/**
 * 响应拦截器：统一解包 axios response → 业务 JSON。
 * 【关键修复】此前 invoke 收到的是 AxiosResponse 包装对象，'id' in res 永远为 false，
 * 业务字段全部读成 undefined，页面只能显示 0.00/0/-- 占位值。
 */
gateway.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response && error.response.status === 401) {
      // 401 由 handleAuthExpired 统一提示并跳转，这里直接终止避免二次弹窗
      handleAuthExpired('登录已过期，请重新登录')
      return Promise.reject(error)
    }
    // HTTP 层错误（网关 502/超时等）统一转中文提示，避免页面显示英文原始报错
    const msg = error.response?.data?.message
      || (error.code === 'ECONNABORTED' ? '请求超时，请检查后端服务是否启动'
        : error.response ? `请求失败（HTTP ${error.response.status}）` : '网络异常，请检查后端服务')
    ElMessage.error(msg)
    error.displayMessage = msg
    return Promise.reject(error)
  }
)

/**
 * 调用后端接口：校验统一响应 { id:'0', message, ... }，成功返回业务数据，失败抛错。
 * 网关鉴权失败（HTTP 200 + id=-1）自动清除 token 并引导重新登录；
 * 业务失败在此统一 toast 提示（页面无需逐个 catch 提示，避免静默失败）。
 */
async function invoke(realCall) {
  const res = await realCall()
  if (res && typeof res === 'object' && 'id' in res) {
    if (String(res.id) === '0') {
      return res
    }
    if (String(res.id) === '-1' || /重新登录|登录状态无效|未登录/.test(res.message || '')) {
      // 登录失效由 handleAuthExpired 统一提示并跳转，这里直接抛错避免二次弹窗
      handleAuthExpired(res.message)
      throw new Error(res.message || '登录状态无效，请重新登录')
    }
    ElMessage.error(res.message || '接口返回异常')
    throw new Error(res.message || '接口返回异常')
  }
  return res
}

/* ==================== 数据统计（4.6） ==================== */

// 平台交易统计：总交易额/总笔数/渠道占比/七日走势
export const getPlatformStats = (params = {}) => invoke(() => gateway.post('/statis/platform', params))

/* ==================== 商户管理（4.1 / 4.5） ==================== */

// 商户列表（含审核状态；筛选条件随 params 传给后端，由后端实现过滤）
export const getMerchantList = (params = {}) => invoke(() => gateway.post('/admin/mch/list', params))

// 商户审核：shopState 1-审核通过 2-审核驳回
export const auditMerchant = (data) => invoke(() => gateway.post('/admin/mch/audit', data))

// 商户费率配置：shopNum + wayCode + mchRate
export const configMerchantRate = (data) => invoke(() => gateway.post('/admin/mch/rate', data))

// 商户启用/禁用
export const updateMerchantState = (data) => invoke(() => gateway.post('/admin/mch/state', data))

/* ==================== 订单管理（4.3） ==================== */

// 平台支付订单分页查询
export const getPayOrderList = (params = {}) => invoke(() => gateway.post('/admin/pay/order/list', params))

// 退款订单分页查询
export const getRefundOrderList = (params = {}) => invoke(() => gateway.post('/admin/refund/order/list', params))

/* ==================== 支付渠道配置（4.5.2） ==================== */

// 查询渠道配置
export const getChannelConfig = () => invoke(() => gateway.post('/admin/channel/list'))

// 保存渠道配置：接口参数、开关、基础费率
export const saveChannelConfig = (data) => invoke(() => gateway.post('/admin/channel/config', data))

// 支付方式列表（含所属支付接口状态与综合可用状态）
export const getPayWayList = () => invoke(() => gateway.post('/admin/pay/way/list'))

// 启用/停用支付方式：wayCode + state（1-启用 0-停用）
export const savePayWayState = (wayCode, state) => invoke(() => gateway.post('/admin/pay/way/state', { wayCode, state }))

/* ==================== 商品类目 ==================== */

export const getCategoryTree = () => invoke(() => gateway.post('/admin/category/tree'))

export const saveCategory = (data) => invoke(() => gateway.post('/admin/category/save', data))

export const deleteCategory = (data) => invoke(() => gateway.post('/admin/category/delete', data))

/* ==================== 系统管理 ==================== */

export const getUserList = (params = {}) => invoke(() => gateway.post('/admin/sys/user/list', params))

export const getRoleList = () => invoke(() => gateway.post('/admin/sys/role/list'))

export const getOperateLogs = (params = {}) => invoke(() => gateway.post('/admin/sys/log/list', params))

// 当前登录操作人（写入后台操作日志的 _opUser 字段）
const opUser = () => ({ _opUser: localStorage.getItem('admin_name') || 'admin' })

// 新增/编辑后台操作员账号（编辑时 data.id 必传，密码留空表示不修改）
export const saveUser = (data) => invoke(() => gateway.post('/admin/sys/user/save', { ...data, ...opUser() }))

// 启用/禁用后台操作员账号（state: 1-启用 0-禁用）
export const updateUserState = (data) => invoke(() => gateway.post('/admin/sys/user/state', { ...data, ...opUser() }))

// 删除后台操作员账号（后端防呆：不允许删除当前登录账号）
export const deleteUser = (data) => invoke(() => gateway.post('/admin/sys/user/delete', { ...data, ...opUser() }))

/* ==================== 管理员登录 ==================== */

export const adminLogin = (data) => invoke(() => gateway.post('/admin/login', data))

/* ==================== 商户应用 / 服务商 / 转账 / 商户通知 ==================== */

// 商户应用列表
export const getPayAppList = (params = {}) => invoke(() => gateway.post('/admin/mch/app/list', params))

// 服务商列表
export const getIsvList = (params = {}) => invoke(() => gateway.post('/admin/isv/list', params))

// 转账订单分页
export const getTransferOrderList = (params = {}) => invoke(() => gateway.post('/admin/transfer/order/list', params))

// 平台处理提现申请（approve: true=打款成功 / false=驳回退回余额）
export const handleTransferOrder = (transferId, approve) => invoke(() => gateway.post('/admin/transfer/order/handle', { transferId, approve }))

// 商户通知分页
export const getMerchantNoticeList = (params = {}) => invoke(() => gateway.post('/admin/mch/notice/list', params))
