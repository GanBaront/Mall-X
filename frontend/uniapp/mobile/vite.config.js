import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // 生产构建使用相对路径，使 dist 可以直接双击打开（H5 为 hash 路由）
  base: './',
  server: {
    port: 5175,
    proxy: {
      // 指导书网关：https://localhost:10010
      '/gateway': {
        target: 'https://localhost:10010',
        changeOrigin: true,
        secure: false,
        rewrite: path => path.replace(/^\/gateway/, '')
      }
    }
  }
})
