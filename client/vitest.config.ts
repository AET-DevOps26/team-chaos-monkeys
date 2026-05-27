import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@tests': path.resolve(__dirname, './tests/helpers'),
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    css: true,
    setupFiles: ['./tests/helpers/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        // Orval-generated per-service clients under src/api/<svc>/.
        // Hand-written code (src/api/mutator/) stays covered.
        'src/api/**/model/**',
        'src/api/**/zod.ts',
        'src/api/**/*-controller/**',
        'src/main.tsx',
        'src/**/*.d.ts',
      ],
    },
  },
})
