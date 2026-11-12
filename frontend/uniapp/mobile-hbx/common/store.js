import { reactive, computed } from 'vue'

/**
 * 全局状态：用户 / 购物车 / 收货地址 / 订单
 * 使用 uni.setStorageSync 持久化，重启不丢失。
 * 除登录态外，购物车/订单/地址均按「账号」隔离存储（key 带用户名后缀），
 * 切换账号互不串数据。
 */

const CART_KEY = 'mallx_cart'
const USER_KEY = 'mallx_user'
const ADDR_KEY = 'mallx_address'
const ORDER_KEY = 'mallx_orders'

function load(key, fallback) {
  try {
    const v = uni.getStorageSync(key)
    return v === '' || v === null || v === undefined ? fallback : v
  } catch (e) {
    return fallback
  }
}

function save(key, value) {
  try {
    uni.setStorageSync(key, value)
  } catch (e) {
    /* ignore */
  }
}

function genAddrId() {
  return 'A' + Date.now() + Math.floor(Math.random() * 1000)
}

export const store = reactive({
  user: load(USER_KEY, null),
  addressList: [],
  address: { name: '', phone: '', detail: '', region: { province: '', city: '', district: '' }, doorNo: '', gender: '先生', tag: '家' },
  cart: [],
  orders: []
})

/* ==================== 按账号隔离的本地存储 ==================== */

// 作用域 key：未登录时归入 guest（登录页可用地址等场景极少）
function scopeKey(base) {
  return base + '@' + (store.user?.username || 'guest')
}

// 从当前账号作用域读取并填充内存数据（登录/登出切换账号时调用）
function reloadScope() {
  store.cart = load(scopeKey(CART_KEY), [])
  store.orders = load(scopeKey(ORDER_KEY), [])
  store.addressList = loadScopedAddressList()
  syncAddress()
}

function saveScopedCart() {
  save(scopeKey(CART_KEY), store.cart)
}

function saveScopedOrders() {
  save(scopeKey(ORDER_KEY), store.orders)
}

/**
 * 读取地址列表（兼容旧版单地址数据，自动迁移为列表）
 */
function loadScopedAddressList() {
  const v = load(scopeKey(ADDR_KEY), null)
  if (Array.isArray(v)) return v
  if (v && v.name) {
    return [{
      id: genAddrId(),
      isDefault: true,
      name: v.name,
      phone: v.phone,
      gender: v.gender || '先生',
      tag: v.tag || '家',
      region: v.region || { province: '', city: '', district: '' },
      doorNo: v.doorNo || '',
      detail: v.detail || ''
    }]
  }
  return []
}

/**
 * 把默认地址同步到 store.address（订单确认页等直接使用）
 */
function syncAddress() {
  const def = store.addressList.find(a => a.isDefault) || store.addressList[0] || null
  store.address = def
    ? { ...def }
    : { name: '', phone: '', detail: '', region: { province: '', city: '', district: '' }, doorNo: '', gender: '先生', tag: '家' }
  save(scopeKey(ADDR_KEY), store.addressList)
}

// 初始化：登录态恢复后按该账号加载数据（未登录则为 guest 空数据）
if (store.user) {
  reloadScope()
}

/* ==================== 用户 ==================== */

// 是否已登录：存在登录用户即为已登录
export function isLoggedIn() {
  return !!store.user
}

// 登录成功：写入用户态（昵称/头像/令牌/角色/店铺等）并持久化，随后加载该账号的专属数据
export function login(username, extra = {}) {
  store.user = {
    username,
    nickname: extra.nickname || extra.realName || username,
    avatar: extra.avatar || '',
    token: extra.token || '',
    userNum: extra.userNum || 'U' + username,
    role: extra.role || 'user',
    shopNum: extra.shopNum || '',
    shopName: extra.shopName || '',
    shopState: extra.shopState !== undefined ? extra.shopState : 1
  }
  save(USER_KEY, store.user)
  reloadScope() // 切换到新账号的数据作用域（购物车/订单/地址按账号隔离）
}

// 更新个人资料（昵称/头像）：合并进当前用户态并持久化
export function updateProfile(patch = {}) {
  if (!store.user) return
  store.user = { ...store.user, ...patch }
  save(USER_KEY, store.user)
}

// 退出登录：清空用户态并切回 guest 作用域（原账号数据仍保存在其作用域下，再次登录即恢复）
export function logout() {
  store.user = null
  save(USER_KEY, null)
  reloadScope()
}

/**
 * 保存地址：带 id 为编辑，不带为新增（第一个地址自动设为默认）
 */
export function saveAddress(addr) {
  const list = store.addressList
  if (addr.id) {
    const idx = list.findIndex(a => a.id === addr.id)
    if (idx > -1) {
      list[idx] = { ...list[idx], ...addr }
    } else {
      list.push({ ...addr, id: genAddrId(), isDefault: list.length === 0 })
    }
  } else {
    list.push({ ...addr, id: genAddrId(), isDefault: list.length === 0 })
  }
  if (list.length && !list.some(a => a.isDefault)) list[0].isDefault = true
  syncAddress()
}

/**
 * 设置默认地址
 */
export function setDefaultAddress(id) {
  store.addressList.forEach(a => { a.isDefault = a.id === id })
  syncAddress()
}

/**
 * 删除地址：删除默认地址后自动把第一个设为默认
 */
export function removeAddress(id) {
  const idx = store.addressList.findIndex(a => a.id === id)
  if (idx === -1) return
  const wasDefault = store.addressList[idx].isDefault
  store.addressList.splice(idx, 1)
  if (wasDefault && store.addressList.length) store.addressList[0].isDefault = true
  syncAddress()
}

/* ==================== 购物车 ==================== */

// 加入购物车：已存在则数量累加（位置不动），新商品插入到列表最前（最新加入的排在前面）
export function addToCart(product, num = 1) {
  const item = store.cart.find(i => i.id === product.id)
  if (item) {
    item.num += num
  } else {
    store.cart.unshift({
      id: product.id,
      name: product.name,
      price: product.price,
      image: product.image || '/static/goods/goods-1.jpg',
      num,
      checked: false // 默认不勾选，用户在购物车手动勾选后结算
    })
  }
  saveScopedCart()
}

// 修改购物车中某商品的数量（不低于 1）
export function updateCartNum(id, num) {
  const item = store.cart.find(i => i.id === id)
  if (item) {
    item.num = Math.max(1, num)
    saveScopedCart()
  }
}

// 从购物车移除某商品
export function removeFromCart(id) {
  const idx = store.cart.findIndex(i => i.id === id)
  if (idx > -1) {
    store.cart.splice(idx, 1)
    saveScopedCart()
  }
}

// 切换单个商品的勾选状态
export function toggleChecked(id) {
  const item = store.cart.find(i => i.id === id)
  if (item) {
    item.checked = !item.checked
    saveScopedCart()
  }
}

// 全选/取消全选：当前全勾则全部取消，否则全部勾选
export function toggleCheckedAll() {
  const allChecked = store.cart.every(i => i.checked)
  store.cart.forEach(i => { i.checked = !allChecked })
  saveScopedCart()
}

// 按商品 id 批量移除购物车商品（购物车结算下单成功后调用，已购商品不再留在购物车）
export function removeCartItems(ids) {
  const idSet = new Set(ids.map(String))
  store.cart = store.cart.filter(i => !idSet.has(String(i.id)))
  saveScopedCart()
}

// 清空购物车
export function clearCart() {
  store.cart.splice(0, store.cart.length)
  saveScopedCart()
}

// 购物车商品总件数
export const cartCount = computed(() => store.cart.reduce((s, i) => s + i.num, 0))

// 已勾选的商品列表
export const checkedItems = computed(() => store.cart.filter(i => i.checked))

// 已勾选商品的合计金额（单价 × 数量求和）
export const checkedTotal = computed(() =>
  checkedItems.value.reduce((s, i) => s + i.price * i.num, 0)
)

/* ==================== 订单 ==================== */

// 订单状态枚举：0待支付/1待发货/2待收货/3已完成/4已关闭
const ORDER_STATE = {
  WAIT_PAY: 0,
  WAIT_SEND: 1,
  WAIT_RECEIVE: 2,
  DONE: 3,
  CLOSED: 4
}

export const ORDER_STATE_TEXT = {
  0: '待支付',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已关闭',
  5: '售后中'
}

// 创建一笔本地待支付订单并写入缓存（下单后支付前使用）
export function createLocalOrder({ items, total, payWay, payOrderId, orderNum }) {
  const order = {
    orderNum: orderNum || 'M' + Date.now(),
    payOrderId: payOrderId || 'P' + Date.now(),
    items: items.map(i => ({ ...i })),
    total,
    payWay,
    state: ORDER_STATE.WAIT_PAY,
    createdAt: formatTime(new Date())
  }
  store.orders.unshift(order)
  saveScopedOrders()
  return order
}

/** 用服务端订单列表整体替换本地缓存（真实数据源） */
export function syncOrders(list) {
  store.orders.splice(0, store.orders.length, ...list)
  saveScopedOrders()
}

// 本地标记订单为「待发货」（支付成功后调用）
export function payOrder(orderNum) {
  const order = store.orders.find(o => o.orderNum === orderNum)
  if (order) {
    order.state = ORDER_STATE.WAIT_SEND
    order.payTime = formatTime(new Date())
    saveScopedOrders()
  }
}

// 本地标记订单为「已完成」（确认收货后调用）
export function confirmReceive(orderNum) {
  const order = store.orders.find(o => o.orderNum === orderNum)
  if (order) {
    order.state = ORDER_STATE.DONE
    order.receiveTime = formatTime(new Date())
    saveScopedOrders()
  }
}

// 格式化时间为 YYYY-MM-DD HH:mm:ss
function formatTime(d) {
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
