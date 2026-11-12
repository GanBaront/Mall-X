/**
 * Mall-X 定位模块（腾讯位置服务 · 逆地理编码）
 *
 * 功能：读取用户当前位置（系统定位）→ 转为国测局 GCJ-02 坐标 →
 *       调腾讯位置服务逆地理编码 API 得到省/市/区县与街道信息。
 *
 * ★ 使用前必须配置 key（占位符，请勿提交真实 key 到公开仓库）：
 *   1. 打开腾讯位置服务开放平台 https://lbs.qq.com 并注册登录
 *   2. 控制台 → 应用管理 → 我的应用 → 创建应用 → 添加 Key
 *   3. 勾选产品「WebService API」（个人开发者免费，每日 5000 次配额）
 *   4. 把申请到的 key 替换下方 MAP_KEY 占位符，重新编译即可
 *
 * 合规说明：仅使用腾讯位置服务（合规白名单厂商）；坐标统一使用 GCJ-02；
 *           定位结果仅用于填写用户自己的收货地址，不做存储与上报。
 */

/** 腾讯位置服务 WebService API key（占位符，请替换为自行申请的 key） */
const MAP_KEY = 'YZDBZ-AHSC4-XHFUL-KJEXM-DZRS5-UGFEA'

/** key 是否已配置（占位符包含"请"字视为未配置） */
function isMapKeyReady() {
  return !!MAP_KEY && MAP_KEY.indexOf('请') !== 0
}

/** WGS84 → GCJ-02 火星坐标转换（国测局公开换算算法，全端统一使用） */
function wgs84ToGcj02(wgsLat, wgsLng) {
  const a = 6378245.0
  const ee = 0.00669342162296594323
  // 境外坐标不做偏移
  if (wgsLng < 72.004 || wgsLng > 137.8347 || wgsLat < 0.8293 || wgsLat > 55.8271) {
    return { lat: wgsLat, lng: wgsLng }
  }
  let dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
  let dLng = transformLng(wgsLng - 105.0, wgsLat - 35.0)
  const radLat = (wgsLat / 180.0) * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - ee * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((a * (1 - ee)) / (magic * sqrtMagic)) * Math.PI)
  dLng = (dLng * 180.0) / ((a / sqrtMagic) * Math.cos(radLat) * Math.PI)
  return { lat: wgsLat + dLat, lng: wgsLng + dLng }
}

/** 纬度偏移拟合函数（换算算法内部用） */
function transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin((y / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * Math.PI) + 320.0 * Math.sin((y * Math.PI) / 30.0)) * 2.0) / 3.0
  return ret
}

/** 经度偏移拟合函数（换算算法内部用） */
function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin((x / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * Math.PI) + 300.0 * Math.sin((x / 30.0) * Math.PI)) * 2.0) / 3.0
  return ret
}

/** 系统定位失败错误信息转友好提示 */
function friendlyLocateError(err) {
  const msg = (err && err.errMsg) || String(err || '')
  if (/denied|PERMISSION/i.test(msg)) return '定位权限被拒绝，请在浏览器或系统设置中开启定位权限'
  if (/timeout/i.test(msg)) return '定位超时，请到信号较好的位置重试'
  if (/no.*provider|unavailable/i.test(msg)) return '当前设备不支持定位'
  return '定位失败：' + msg
}

/** H5 端 JSONP 请求（腾讯 WebService API 支持 callback 参数，绕开浏览器跨域限制） */
function jsonp(url) {
  return new Promise((resolve, reject) => {
    const cbName = 'mallxMapCb' + Date.now()
    const script = document.createElement('script')
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error('定位服务请求超时'))
    }, 8000)
    function cleanup() {
      clearTimeout(timer)
      try { delete window[cbName] } catch (e) { window[cbName] = undefined }
      if (script.parentNode) script.parentNode.removeChild(script)
    }
    window[cbName] = (data) => {
      cleanup()
      resolve(data)
    }
    script.src = url + '&callback=' + cbName
    script.onerror = () => {
      cleanup()
      reject(new Error('定位服务请求失败，请检查网络或 key 配置'))
    }
    document.head.appendChild(script)
  })
}

/** 非编码失败转友好提示 */
function friendlyGeocodeError(data) {
  const msg = (data && data.message) || ''
  if (/key|签名|权限/i.test(msg)) return '地图 key 无效或配额不足，请检查 common/location.js 中的 key 配置'
  return '逆地理编码失败：' + (msg || '未知错误')
}

/**
 * 逆地理编码：GCJ-02 坐标 → 省/市/区县 + 街道信息（腾讯位置服务）
 * @returns {Promise<{province,city,district,street,streetNumber,address,recommend}>}
 *          recommend 为推荐填入门牌号的街道门牌文本（可能为空）
 */
async function reverseGeocode(lat, lng) {
  if (!isMapKeyReady()) {
    throw new Error('请先在 common/location.js 中配置腾讯地图 key（lbs.qq.com 申请）')
  }
  const url = 'https://apis.map.qq.com/ws/geocoder/v1/?location=' + lat + ',' + lng
    + '&get_poi=1&key=' + MAP_KEY
  let data
  // #ifdef H5
  data = await jsonp(url)
  // #endif
  // #ifndef H5
  data = await new Promise((resolve, reject) => {
    uni.request({
      url,
      method: 'GET',
      timeout: 8000,
      success: (res) => resolve(res.data),
      fail: () => reject(new Error('定位服务网络请求失败，请检查网络'))
    })
  })
  // #endif
  if (!data || data.status !== 0) throw new Error(friendlyGeocodeError(data))
  const result = data.result || {}
  const ad = result.ad_info || {}
  const det = result.address_detail || {}
  const recommend = det.street ? det.street + (det.street_number || '') : ''
  return {
    province: ad.province || '',
    city: ad.city || '',
    district: ad.district || '',
    street: det.street || '',
    streetNumber: det.street_number || '',
    address: result.address || '',
    recommend
  }
}

/**
 * 定位入口：获取当前位置并解析为省市区地址（各页面共用）。
 * App/模拟器：请求 wgs84 坐标（App 端请求 gcj02 需要配置地图厂商 SDK，标准基座会报
 * "map provider not configured"），拿到后由本地算法统一转为 GCJ-02。
 * H5：优先浏览器 geolocation（仅安全上下文 localhost/HTTPS 且需用户授权可用），
 *     失败时回退「IP 定位」——按出口 IP 解析大致坐标（不依赖定位权限，
 *     局域网 IP / 嵌入式预览等非安全上下文也能定位），再统一走逆地理编码。
 * @returns {Promise<{lat,lng,province,city,district,street,streetNumber,address,recommend}>}
 */
export function locate() {
  // #ifdef H5
  return h5Locate()
  // #endif
  // #ifndef H5
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'wgs84',
      success: async (res) => {
        try {
          // 全端统一：WGS84 → GCJ-02 国测局火星坐标
          const gcj = wgs84ToGcj02(res.latitude, res.longitude)
          const addr = await reverseGeocode(gcj.lat, gcj.lng)
          resolve({ lat: gcj.lat, lng: gcj.lng, ...addr })
        } catch (e) {
          reject(e)
        }
      },
      fail: (err) => reject(new Error(friendlyLocateError(err)))
    })
  })
  // #endif
}

/* ==================== H5 端定位（浏览器 geolocation + IP 定位兜底） ==================== */

/** H5 浏览器定位：uni.getLocation 包装为 Promise（返回 WGS84 原始坐标） */
function browserLocate() {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'wgs84',
      success: (res) => resolve(res),
      fail: (err) => reject(new Error(friendlyLocateError(err)))
    })
  })
}

/**
 * IP 定位（腾讯位置服务 /ws/location/v1/ip）：按出口 IP 解析大致位置，
 * 返回坐标已是 GCJ-02（无需再偏移转换），精度到城市级。
 * 请求方式：JSONP 优先，失败时降级 uni.request（部分环境 JSONP 被拦截）。
 */
async function ipLocate() {
  if (!isMapKeyReady()) {
    throw new Error('请先在 common/location.js 中配置腾讯地图 key（lbs.qq.com 申请）')
  }
  const url = 'https://apis.map.qq.com/ws/location/v1/ip?key=' + MAP_KEY
  let data
  try {
    data = await jsonp(url)
  } catch (e) {
    data = await new Promise((resolve, reject) => {
      uni.request({
        url,
        method: 'GET',
        timeout: 8000,
        success: (res) => resolve(res.data),
        fail: () => reject(new Error('IP 定位服务请求失败，请检查网络'))
      })
    })
  }
  if (!data || data.status !== 0) throw new Error(friendlyGeocodeError(data))
  const result = data.result || {}
  const loc = result.location || {}
  if (!loc.latitude || !loc.longitude) throw new Error('IP 定位失败：未解析到坐标')
  return { lat: loc.latitude, lng: loc.longitude }
}

/** H5 定位主流程：浏览器定位失败/不可用时自动回退 IP 定位，结果统一逆地理编码为省市区 */
async function h5Locate() {
  let gcj
  try {
    const wgs = await browserLocate()
    gcj = wgs84ToGcj02(wgs.latitude, wgs.longitude)
  } catch (e) {
    // 浏览器定位不可用（非安全上下文 / 权限被拒 / 超时）→ IP 定位兜底（坐标已是 GCJ-02）
    gcj = await ipLocate()
  }
  const addr = await reverseGeocode(gcj.lat, gcj.lng)
  return { lat: gcj.lat, lng: gcj.lng, ...addr }
}
