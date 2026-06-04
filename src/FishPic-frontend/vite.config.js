/* eslint-disable no-undef */
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
        },
        '/cos-proxy': {
          target: env.VITE_COS_BASE_URL,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/cos-proxy/, ''),
        },
      },
    },
    optimizeDeps: {
      include: ['antd/locale/zh_CN'],
    },
  }
})
