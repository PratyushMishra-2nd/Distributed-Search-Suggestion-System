import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxy API calls to the Spring Boot backend so the browser stays same-origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/suggest': 'http://localhost:8080',
      '/search': 'http://localhost:8080',
      '/trending': 'http://localhost:8080',
      '/cache': 'http://localhost:8080',
      '/metrics': 'http://localhost:8080',
    },
  },
})
