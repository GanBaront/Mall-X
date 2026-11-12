/**
 * Mall-X 移动端数据层（真实后端版）
 * 网关地址自动发现：H5 vite 预览走 /gateway 代理；App/模拟器按
 * 「上次成功地址缓存 → 10.0.2.2(AVD 模拟器) → 局域网扫描」动态解析，
 * 不写死任何 IP。所有请求携带登录 token，失败直接报错，不回退模拟数据。
 */

import { store } from './store'

/**
 * ==================== 后端网关地址：自动发现，不写死 IP ====================
 * - vite 预览（localhost:5175）：有 /gateway 代理，直接用（localhost 场景最常用）
 * - App（真机/模拟器）与其他 H5 环境：无代理，按以下顺序自动发现网关：
 *   1) 上次成功地址（storage 缓存，扫描成功一次后后续直连、秒级启动）
 *   2) Android 模拟器宿主机固定地址 https://10.0.2.2:10010（AVD 模拟器）
 *   3) 扫描常见内网网段 192.168.1.x / 192.168.0.x 的 10010 端口
 * 电脑 IP 变化（DHCP 重分配）后无需改任何代码：缓存的地址失效会自动重新扫描。
 * 自签名证书统一通过 sslVerify:false 跳过校验（仅 App 端生效；H5 请用 5175 预览）。
 */
const IS_VITE_PROXY = typeof window !== 'undefined' && window.location && window.location.port === '5175'
const BASE_CACHE_KEY = 'mallx_api_base'

/** 探测某网关地址是否可用（调白名单接口 /api/products，id='0' 即成功） */
function probeBase(base) {
  return new Promise((resolve) => {
    uni.request({
      url: base + '/api/products',
      method: 'POST',
      data: {},
      timeout: 1500,
      sslVerify: false,
      success: (res) => resolve(!!(res.data && String(res.data.id) === '0')),
      fail: () => resolve(false)
    })
  })
}

/** 并发扫描常见内网网段的 10010 端口，返回第一个可用的网关地址（找不到为空串） */
function scanLanBases() {
  const bases = []
  for (const seg of ['192.168.1', '192.168.0']) {
    for (let i = 2; i <= 254; i++) bases.push('https://' + seg + '.' + i + ':10010')
  }
  return (async () => {
    for (let i = 0; i < bases.length; i += 64) {
      const chunk = bases.slice(i, i + 64)
      const results = await Promise.all(chunk.map(probeBase))
      const ok = chunk[results.indexOf(true)]
      if (ok) return ok
    }
    return ''
  })()
}

let basePromise = null

/** 获取当前可用的网关地址（结果缓存；失败时重置以便下次重新探测） */
export function getBaseUrl() {
  if (IS_VITE_PROXY) return Promise.resolve('/gateway')
  if (!basePromise) {
    basePromise = (async () => {
      // 1) 上次成功的地址
      let cached = ''
      try { cached = uni.getStorageSync(BASE_CACHE_KEY) || '' } catch (e) {}
      if (cached && await probeBase(cached)) return cached
      // 2) Android AVD 模拟器宿主机固定地址
      if (await probeBase('https://10.0.2.2:10010')) return 'https://10.0.2.2:10010'
      // 3) 局域网扫描
      const found = await scanLanBases()
      if (found) return found
      throw new Error('未找到后端服务器：请确认手机/模拟器与电脑在同一网络、后端已启动、防火墙已放行 10010 端口')
    })()
    basePromise.then((base) => {
      try { uni.setStorageSync(BASE_CACHE_KEY, base) } catch (e) {}
    }).catch(() => { basePromise = null })
  }
  return basePromise
}

/** 统一请求封装：先自动解析网关地址，处理 token 注入、后端 id='0' 成功判定与登录态失效跳转 */
export function request(path, data = {}) {
  return getBaseUrl().then((base) => new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    if (store.user && store.user.token) {
      header.token = store.user.token
    }
    uni.request({
      url: base + path,
      method: 'POST',
      data,
      header,
      timeout: 8000,
      sslVerify: false,
      success: (res) => {
        const body = res.data
        if (body && String(body.id) === '0') {
          resolve(body)
        } else if (body && /登录|token/i.test(String(body.message || ''))) {
          // 登录态失效：清除本地登录并跳转登录页
          store.user = null
          uni.setStorageSync('mallx_user', null)
          reject(new Error(body.message || '请先登录'))
          uni.navigateTo({ url: '/pages/login/login' })
        } else {
          reject(new Error((body && body.message) || '请求失败'))
        }
      },
      fail: (err) => {
        const msg = (err && err.errMsg) || ''
        if (/ssl|certificate/i.test(msg)) {
          reject(new Error('证书校验失败，请检查网关地址或基座版本'))
        } else {
          // 网络不通：重置已缓存的网关地址，下次请求重新自动发现（应对电脑 IP 变化）
          basePromise = null
          reject(new Error('网络异常：后端连接失败，正在自动重新寻找服务器，请稍后重试'))
        }
      }
    })
  }))
}

/** 打开第三方支付收银台（H5 新窗口 / App 浏览器）。App 端把 localhost/模拟器地址动态替换为自动发现的网关主机 */
export async function openPayUrl(url) {
  if (!url) return
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  let target = String(url)
  try {
    const base = await getBaseUrl()
    // 收银台在 mall-alipay(10012)：把链接里的 localhost/127.0.0.1/10.0.2.2/任意IP:10012 替换为自动发现的网关主机
    const alipayOrigin = base.replace(':10010', ':10012')
    target = target.replace(/^(https?:\/\/)[^/]+:10012/, (_m, scheme) => scheme + alipayOrigin.replace(/^https?:\/\//, ''))
  } catch (e) { /* 解析失败保持原链接 */ }
  if (typeof plus !== 'undefined' && plus.runtime) {
    plus.runtime.openURL(target)
  } else {
    uni.setClipboardData({ data: target, success: () => uni.showToast({ title: '支付链接已复制', icon: 'none' }) })
  }
  // #endif
}

/* ==================== 商品 & 分类 ==================== */

/**
 * 商品类目树（启用中的类目，与管理员端「商品类目」管理同源）。
 * 返回 roots 数组：[{ id, name, sort, state, children: [{ id, name, ... }] }]
 * 供首页/分类 Tab 分类导航、商户端商品分类选择使用。
 */
export async function getCategoryTree() {
  const res = await request('/api/category/list', {})
  return res.records || []
}

/** 价格字符串 → 数字（后端金额为字符串，模板里要做数值运算/格式化） */
function toNumberPrice(list) {
  return (list || []).map(p => ({ ...p, price: Number(p.price || 0) }))
}

/**
 * 商品列表（数据库分页版）：pageNum 从 1 起，pageSize 默认 20。
 * 返回 { records(价格已转数字), total(总数) }，调用方按 total 判断是否还有更多。
 */
export async function getProducts(category = '全部', keyword = '', pageNum = 1, pageSize = 20) {
  const res = await request('/api/products', { category, keyword, pageNum, pageSize })
  return {
    ...res,
    records: toNumberPrice(res.records),
    total: Number(res.total || 0)
  }
}

export async function getProductById(id) {
  const res = await request('/api/products/detail', { id })
  if (res.record) {
    res.record = { ...res.record, price: Number(res.record.price || 0) }
  }
  return res
}

/* ==================== 订单 & 支付（指导书 4.2 / 4.3） ==================== */

/**
 * 当前可用支付方式（后端实时计算）：支付方式启用 且 所属支付接口启用。
 * 管理员在 PC 后台「支付接口」/「支付方式」页停用后，本列表即时生效。
 * 返回 records：[{ wayCode, name, wayType, rate }]
 */
export async function getPayWays() {
  const res = await request('/api/pay/ways', {})
  return res.records || []
}

/**
 * 统一支付下单（真实接口）
 * 后端负责：创建支付单 + 履约单 + 计费；返回 payOrderId/payUrl
 */
export async function createOrder(payload) {
  const data = { ...payload }
  if (store.address && store.address.detail) {
    data.address = (store.address.name ? store.address.name + ' ' : '')
      + (store.address.phone ? store.address.phone + ' ' : '')
      + (store.address.region && store.address.region.province
        ? (store.address.region.province + store.address.region.city + store.address.region.district) : '')
      + (store.address.doorNo || '') + (store.address.detail || '')
  }
  const res = await request('/pay/create', data)
  return {
    payOrderId: res.payOrderId,
    mchOrderNo: res.mchOrderNo,
    payUrl: res.payUrl,
    orderState: res.orderState,
    message: res.message
  }
}

/** 我的订单列表（真实接口，state 可选筛选） */
export async function getOrderList(state = null) {
  const res = await request('/api/orders', state === null ? {} : { state })
  return res.records || []
}

/** 订单详情（后端合并履约单 + 支付单信息：店铺名/渠道交易号/付款·发货·成交时间等） */
export function getOrderDetail(orderNum) {
  return request('/api/order/detail', { orderNum }).then(res => res.record)
}

/** 确认收货 */
export function confirmOrderApi(orderNum) {
  return request('/api/order/confirm', { orderNum })
}

/** 取消订单 */
export function cancelOrderApi(orderNum) {
  return request('/api/order/cancel', { orderNum })
}

/* ==================== 营销卡券（领券中心/幸运转盘） ==================== */

/** 幸运转盘抽奖：返回 { prizeIndex(扇区0-7), leftTimes(今日剩余), coupon } */
export function drawCoupon() {
  return request('/api/coupon/draw')
}

/** 我的卡券列表（state 可选：0 未使用 / 1 已使用），返回 records */
export async function myCoupons(state = null) {
  const res = await request('/api/coupon/list', state === null ? {} : { state })
  return res.records || []
}

/** 我的未使用卡券（下单选券用），返回 records */
export async function usableCoupons() {
  const res = await request('/api/coupon/usable')
  return res.records || []
}

/** 券抵扣金额（与后端 PayService 口径一致）：现金红包直减、折扣券按折扣率打折 */
export function couponDiscountOf(coupon, total) {
  if (!coupon) return 0
  const t = Number(total) || 0
  if (Number(coupon.type) === 2) return Math.min(Number(coupon.amount || 0), t)
  const rate = Number(coupon.discountRate || 1)
  return Math.round(t * (1 - rate) * 100) / 100
}

/* ==================== 售后 / 退款（用户侧） ==================== */

/** 申请售后/退款（待发货=仅退款，已完成=售后退款），返回 { refundOrderId, refundMoney } */
export function applyRefund(orderNum, refundReason) {
  return request('/api/refund/apply', { orderNum, refundReason })
}

/** 我的售后申请列表，返回 records */
export async function myRefunds() {
  const res = await request('/api/refund/my')
  return res.records || []
}

/**
 * 支付结果查询（指导书 4.3.1）
 * 后端对待支付单会自动向支付宝沙箱主动对账，已支付则同步记账
 */
export function payQuery(payOrderId) {
  return request('/pay/query', { payOrderNum: payOrderId })
}

/* ==================== 用户资料（个人中心） ==================== */

/**
 * 站内资源地址拼接：后端返回的 /upload/... 相对路径 → 当前运行端可访问的完整 URL。
 * H5 预览走 /gateway 代理；App 端用自动发现并缓存下来的网关地址（请求过任何接口后缓存必已存在）。
 */
export function assetUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  if (IS_VITE_PROXY) return '/gateway' + path
  let base = ''
  try { base = uni.getStorageSync(BASE_CACHE_KEY) || '' } catch (e) {}
  return base + path
}

/**
 * 图片展示地址统一解析：/upload/ 开头的后端上传图 → 按运行端拼接完整地址；
 * 本地静态图（/static/...）与完整 URL 原样返回；空值回退默认商品图。
 */
export function imgSrc(path) {
  if (!path) return '/static/goods/goods-1.jpg'
  if (path.indexOf('/upload/') === 0) return assetUrl(path)
  return path
}

/** 我的资料（昵称/头像/账号/编号），后端按 token 识别当前用户 */
export function getUserInfo() {
  return request('/api/user/info')
}

/** 修改个人资料：nickname（2-20 字符）、avatar（/upload/ 开头的站内路径），均可选 */
export function saveProfile(payload) {
  return request('/api/user/profile/save', payload)
}

/**
 * 上传图片（multipart，字段名 file），返回后端相对路径 res.path（配合 imgSrc/assetUrl 使用）。
 * 头像、商品图等所有站内图片上传共用此入口。
 */
export function uploadImage(filePath) {
  return getBaseUrl().then((base) => new Promise((resolve, reject) => {
    uni.uploadFile({
      url: base + '/api/upload',
      filePath,
      name: 'file',
      header: { token: (store.user && store.user.token) || '' },
      success: (res) => {
        let body = null
        try { body = JSON.parse(res.data) } catch (e) {}
        if (body && String(body.id) === '0') {
          resolve(body)
        } else {
          reject(new Error((body && body.message) || '上传失败，请稍后重试'))
        }
      },
      fail: () => reject(new Error('上传失败，请检查网络'))
    })
  }))
}
