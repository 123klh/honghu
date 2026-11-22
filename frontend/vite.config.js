import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: '波波公考',
        short_name: '波波公考',
        description: '公务员考试在线刷题、模拟考试、申论评分与学习社区',
        theme_color: '#1f5fbf',
        background_color: '#f5f7fa',
        display: 'standalone',
        start_url: '/',
        lang: 'zh-CN',
        icons: [
          { src: 'pwa-192.svg', sizes: '192x192', type: 'image/svg+xml', purpose: 'any' },
          { src: 'pwa-512.svg', sizes: '512x512', type: 'image/svg+xml', purpose: 'any maskable' }
        ]
      },
      workbox: {
        // 题库接口不缓存：答案判定必须实时；只缓存静态资源
        navigateFallback: '/index.html',
        globPatterns: ['**/*.{js,css,html,svg}'],
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api/'),
            handler: 'NetworkOnly'
          }
        ]
      }
    })
  ],
  server: {
    port: 7001,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
