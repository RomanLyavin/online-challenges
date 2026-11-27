import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(here, '..')
const staticDirs = ['data', 'avatars', 'catalog', 'confirmations']

function copyRecursive(source, destination) {
  if (!fs.existsSync(source)) return
  fs.mkdirSync(destination, { recursive: true })
  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const from = path.join(source, entry.name)
    const to = path.join(destination, entry.name)
    if (entry.isDirectory()) copyRecursive(from, to)
    else if (entry.isFile()) fs.copyFileSync(from, to)
  }
}

function serveRepoData() {
  return {
    name: 'serve-repo-data',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const pathname = decodeURIComponent((req.url || '').split('?')[0])
        const match = pathname.match(/^\/(data|avatars|catalog|confirmations)\/(.+)$/)
        if (!match) return next()
        const file = path.join(repoRoot, match[1], match[2])
        const resolved = path.resolve(file)
        if (!resolved.startsWith(path.join(repoRoot, match[1]) + path.sep) || !fs.existsSync(resolved)) {
          return next()
        }
        res.setHeader('Content-Type', 'application/octet-stream')
        fs.createReadStream(resolved).pipe(res)
      })
    },
    closeBundle() {
      const outDir = path.join(here, 'dist')
      for (const dir of staticDirs) {
        copyRecursive(path.join(repoRoot, dir), path.join(outDir, dir))
      }
      const nojekyll = path.join(repoRoot, '.nojekyll')
      if (fs.existsSync(nojekyll)) fs.copyFileSync(nojekyll, path.join(outDir, '.nojekyll'))
    }
  }
}

export default defineConfig({
  base: './',
  plugins: [react(), serveRepoData()],
  build: {
    outDir: 'dist',
    emptyOutDir: true
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.js']
  }
})
