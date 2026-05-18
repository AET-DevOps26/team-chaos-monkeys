import { defineConfig } from 'orval'

const mutator = {
  path: './src/api/mutator/custom-instance.ts',
  name: 'customInstance',
}

export default defineConfig({
  auth: {
    input: './openapi/auth.json',
    output: {
      target: './src/api/auth/index.ts',
      client: 'react-query',
      mode: 'tags-split',
      schemas: './src/api/auth/model',
      override: { mutator },
    },
  },
  'lost-items': {
    input: './openapi/lost-items.json',
    output: {
      target: './src/api/lost-items/index.ts',
      client: 'react-query',
      mode: 'tags-split',
      schemas: './src/api/lost-items/model',
      override: { mutator },
    },
  },
  'lost-items-zod': {
    input: './openapi/lost-items.json',
    output: {
      target: './src/api/lost-items/zod.ts',
      client: 'zod',
      mode: 'single',
    },
  },
  'found-items': {
    input: './openapi/found-items.json',
    output: {
      target: './src/api/found-items/index.ts',
      client: 'react-query',
      mode: 'tags-split',
      schemas: './src/api/found-items/model',
      override: { mutator },
    },
  },
  'found-items-zod': {
    input: './openapi/found-items.json',
    output: {
      target: './src/api/found-items/zod.ts',
      client: 'zod',
      mode: 'single',
    },
  },
  'auth-zod': {
    input: './openapi/auth.json',
    output: {
      target: './src/api/auth/zod.ts',
      client: 'zod',
      mode: 'single',
    },
  },
})
