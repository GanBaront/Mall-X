/**
 * 商家工作台数据层（真实后端版）
 * 统一登录后按角色路由到商家工作台；token 由 api.js 统一携带。
 * 后端：mall-mgr（指导书接口规范），商户身份取自登录态 shopNum。
 */

import { request } from './api'

/* ==================== 统一登录 ==================== */

/**
 * 统一登录：调用后端 /api/login，按返回角色路由
 * 成功返回 { role, token, shopNum?, shopName?, shopState?, userNum? }
 */
export async function uniLogin(username, password) {
  if (!username || !password) throw new Error('请输入账号密码')
  const res = await request('/api/login', { username, password })
  return res
}

/**
 * 买家账号注册（POST /api/register）：仅账号+密码，注册后角色 ROLE_USER
 */
export function userRegister(username, password) {
  return request('/api/register', { username, password })
}

/**
 * 商户入驻注册（指导书 4.1.1）
 */
export function mchRegister(data) {
  return request('/mch/register', data)
}

/* ==================== 工作台统计（指导书 4.6.2） ==================== */

// 工作台统计：获取商家经营概览数据
export function getWorkbench() {
  return request('/statis/mch', {})
}

/* ==================== 商品管理 ==================== */

// 获取商家商品列表
// 获取商家商品列表（keyword 商品名称/描述模糊筛选）
export function getMerchantGoods(keyword = '') {
  return request('/api/mch/goods', { keyword })
}

/** 商品新增/编辑/上下架 */
export function saveMerchantGoods(goods) {
  return request('/api/mch/goods/save', goods)
}

/** 删除商品（后端校验归属后物理删除） */
export function deleteMerchantGoods(id) {
  return request('/api/mch/goods/delete', { id })
}

/* ==================== 订单管理（指导书 4.3.2） ==================== */

// 获取商家订单列表（默认分页 pageNum=1,pageSize=50）
export function getMerchantOrders(params = {}) {
  return request('/pay/list/mch', { pageNum: 1, pageSize: 50, ...params })
}

/** 订单发货 */
export function shipOrderMock(orderNum, logisticsNo) {
  return request('/api/mch/ship', { orderNum, logisticsNo })
}

/* ==================== 售后退款（指导书 4.4） ==================== */

// 获取售后退款申请列表
export function getRefunds() {
  return request('/refund/query', {})
}

/** 商户处理退款申请：agree=true 同意 / false 拒绝 */
export function handleRefundMock(refundOrderId, agree) {
  return request('/api/mch/refund/handle', { refundOrderId, agree })
}

/* ==================== 消息通知 ==================== */

// 获取商家消息通知
export function getNotices() {
  return request('/api/mch/notices', {})
}

/** 标记消息已读：{ id } 单条 或 { all: true } 全部（落库，刷新不回退） */
export function markNoticeRead(payload) {
  return request('/api/mch/notice/read', payload)
}

/* ==================== 资金提现 ==================== */

// 获取商家资金/提现账户信息
export function getFunds() {
  return request('/api/mch/funds', {})
}

export function applyWithdrawMock(amount, account) {
  return request('/api/mch/withdraw', { amount, account })
}

/* ==================== 店铺信息 ==================== */

// 获取店铺信息
export function getShopInfo() {
  return request('/api/mch/shop', {})
}

export function saveShopInfoMock(data) {
  return request('/api/mch/shop/save', data)
}
