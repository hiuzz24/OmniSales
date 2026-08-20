import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5174,
    host: '127.0.0.1',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  },
  optimizeDeps: {
    include: [
      'react',
      'react-dom/client',
      'react-router-dom',
      'axios',
      'react-toastify',
      'react-hook-form',
      'zod',
      '@hookform/resolvers/zod',
      'lucide-react',
      'recharts',
    ],
  },
  build: {
    target: 'es2022',
  },
})