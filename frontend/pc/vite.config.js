import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // 生产构建使用相对路径，使 dist 可以直接双击打开或任意静态服务器托管
  base: './',
  server: {
    port: 5173,
    watch: {
      usePolling: true
    },
    proxy: {
      // 平台管理端网关（实验指导书：https://localhost:10010）
      '/gateway': {
        target: 'https://localhost:10010',
        changeOrigin: true,
        secure: false,
        rewrite: path => path.replace(/^\/gateway/, '')
      }
    }
  }
})

