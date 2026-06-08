import { defineConfig } from 'orval'
import type { GeneratorVerbOptions } from '@orval/core'

const mutator = {
  path: './src/api/mutator/custom-instance.ts',
  name: 'customInstance',
}

const multipartJsonPartTransformer = (verb: GeneratorVerbOptions): GeneratorVerbOptions => {
  const multipart = verb.body.originalSchema && 'content' in verb.body.originalSchema
    ? verb.body.originalSchema.content?.['multipart/form-data']
    : undefined
  const encodings = multipart?.encoding ?? {}
  const jsonPartNames = Object.entries(encodings)
    .filter(([, encoding]) => encoding.contentType === 'application/json')
    .map(([name]) => name)

  if (!verb.body.formData || !multipart) {
    return verb
  }

  let formData = verb.body.formData
  for (const name of jsonPartNames) {
    formData = formData.replace(
      new RegExp(`formData\\.append\\(\\\`${name}\\\`, JSON\\.stringify\\(([^)]+)\\)\\);`, 'g'),
      `formData.append(\`${name}\`, new Blob([JSON.stringify($1)], { type: 'application/json' }));`,
    )
  }

  return {
    ...verb,
    body: {
      ...verb.body,
      contentType: '',
      formData,
    },
  }
}

const apiOverride = {
  mutator,
  transformer: multipartJsonPartTransformer,
}

export default defineConfig({
  auth: {
    input: './openapi/auth.json',
    output: {
      target: './src/api/auth/index.ts',
      client: 'react-query',
      mode: 'tags-split',
      schemas: './src/api/auth/model',
      override: apiOverride,
    },
  },
  'lost-items': {
    input: './openapi/lost-items.json',
    output: {
      target: './src/api/lost-items/index.ts',
      client: 'react-query',
      mode: 'tags-split',
      schemas: './src/api/lost-items/model',
      override: apiOverride,
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
      override: apiOverride,
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
  matching: {
    input: './openapi/matching.json',
    output: {
      target: './src/api/matching/index.ts',
      client: 'react-query',
      mode: 'tags-split',
      schemas: './src/api/matching/model',
      override: apiOverride,
    },
  },
  'matching-zod': {
    input: './openapi/matching.json',
    output: {
      target: './src/api/matching/zod.ts',
      client: 'zod',
      mode: 'single',
    },
  },
  operations: {
    input: './openapi/operations.json',
    output: {
      target: './src/api/operations/index.ts',
      client: 'react-query',
      mode: 'tags-split',
      schemas: './src/api/operations/model',
      override: apiOverride,
    },
  },
  'operations-zod': {
    input: './openapi/operations.json',
    output: {
      target: './src/api/operations/zod.ts',
      client: 'zod',
      mode: 'single',
    },
  },
})
