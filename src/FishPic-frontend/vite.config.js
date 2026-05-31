import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/cos-proxy': {
        target: 'https://fish-picture-1333236187.cos.ap-guangzhou.myqcloud.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/cos-proxy/, ''),
      },
    },
  },
  optimizeDeps: {
    include: ['antd/locale/zh_CN'],
  },
})
