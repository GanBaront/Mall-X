/**
 * 统一轻提示封装。
 * uni.showToast 在 icon 为 success/error 时是固定大小的图标弹窗，文案超过约 7 个字会截断溢出；
 * 这里自动判断：文案超过 TOAST_ICON_MAX 字时改用 icon:'none' 纯文字模式（宽度自适应、可换行）。
 * 用法：import { toast } from '../common/toast' → toast('已全部标记为已读')
 */
const TOAST_ICON_MAX = 7

export function toast(title, icon = 'success') {
  const t = String(title == null ? '' : title)
  uni.showToast({
    title: t,
    icon: t.length > TOAST_ICON_MAX ? 'none' : icon
  })
}
