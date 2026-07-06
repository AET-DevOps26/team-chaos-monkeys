#!/usr/bin/env node
// Fetch OpenAPI specs from the running gateway and write them under client/openapi/.
// Run when a backend contract changes: `npm run codegen:fetch`.

import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const GATEWAY = process.env.GATEWAY_HOST ?? 'http://localhost:8080'
const RETRIES = 30
const RETRY_DELAY_MS = 2000

const SPECS = [
  { prefix: 'auth', file: 'auth.json' },
  { prefix: 'lost-items', file: 'lost-items.json' },
  { prefix: 'found-items', file: 'found-items.json' },
  { prefix: 'matches', file: 'matches.json' },
  { prefix: 'pickups', file: 'pickups.json' },
  { prefix: 'notifications', file: 'notifications.json' },
  { prefix: 'venues', file: 'operations.json' },
  { prefix: 'matches', file: 'matching.json' },
]

const here = dirname(fileURLToPath(import.meta.url))
const outDir = resolve(here, '..', 'openapi')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function fetchSpec(prefix) {
  const url = `${GATEWAY}/${prefix}/v3/api-docs`
  let lastErr
  for (let attempt = 1; attempt <= RETRIES; attempt++) {
    try {
      const res = await fetch(url)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return await res.json()
    } catch (err) {
      lastErr = err
      if (attempt < RETRIES) {
        process.stderr.write(
          `  attempt ${attempt}/${RETRIES} for ${prefix} failed (${err.message}); retrying in ${RETRY_DELAY_MS}ms\n`,
        )
        await sleep(RETRY_DELAY_MS)
      }
    }
  }
  throw new Error(`Failed to fetch ${url}: ${lastErr?.message}`)
}

await mkdir(outDir, { recursive: true })

const failures = []

for (const { prefix, file } of SPECS) {
  process.stdout.write(`Fetching ${prefix} from ${GATEWAY}... `)
  try {
    const spec = await fetchSpec(prefix)
    const target = resolve(outDir, file)
    await writeFile(target, JSON.stringify(spec, null, 2) + '\n')
    process.stdout.write(`wrote ${target}\n`)
  } catch (err) {
    // Keep going so one failing service doesn't block the rest; the existing
    // committed snapshot for that service is left untouched (only written on 200).
    process.stdout.write(`SKIPPED (${err.message})\n`)
    failures.push(prefix)
  }
}

if (failures.length > 0) {
  process.stderr.write(`\nFailed to fetch: ${failures.join(', ')}. Existing snapshots kept.\n`)
  process.exitCode = 1
}
