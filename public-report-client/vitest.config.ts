import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@test': path.resolve(__dirname, './test/helpers'),
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    css: true,
    setupFiles: ['./test/helpers/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        // Orval-generated lost-items client under src/api/lost-items/.
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
