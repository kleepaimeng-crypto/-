import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createReadStream, existsSync } from 'node:fs'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Plugin, ViteDevServer } from 'vite'

const configDir = dirname(fileURLToPath(import.meta.url))
const offlineMapRoot = resolve(configDir, 'map/tiles_street')

function offlineMapPlugin(): Plugin {
  return {
    name: 'offline-map-dev-server',
    configureServer(server: ViteDevServer) {
      server.middlewares.use('/offline-map', (
        req: IncomingMessage,
        res: ServerResponse,
        next: () => void,
      ) => {
        const url = req.url || ''
        const match = url.match(/^\/(\d+)\/(\d+)\/(\d+)\.png(?:\?.*)?$/)
        if (!match) {
          next()
          return
        }
        const [, z, x, y] = match
        const tilePath = resolve(offlineMapRoot, z, x, `${y}.png`)
        if (!tilePath.startsWith(offlineMapRoot) || !existsSync(tilePath)) {
          res.statusCode = 404
          res.end()
          return
        }
        res.setHeader('Content-Type', 'image/png')
        createReadStream(tilePath).pipe(res)
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')

  return {
    plugins: [vue(), offlineMapPlugin()],
    server: {
      proxy: {
        '/api': {
          target: env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
