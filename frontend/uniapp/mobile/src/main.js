/**
 * 应用启动配置（main.js）
 * 创建并挂载 uni-app 的 SSR 应用实例，作为整个移动端的入口。
 */
import App from './App'
import { createSSRApp } from 'vue'

// 创建应用实例：用 createSSRApp 包裹根组件 App 并返回
export function createApp() {
  const app = createSSRApp(App)
  return {
    app
  }
}
