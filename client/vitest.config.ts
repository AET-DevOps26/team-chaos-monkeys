import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    css: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/api/auth/**',
        'src/api/lost-items/**',
        'src/api/found-items/**',
        'src/main.tsx',
        'src/**/*.d.ts',
        'src/test/**',
        '**/*.test.{ts,tsx}',
      ],
    },
  },
})
