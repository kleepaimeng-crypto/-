/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_DEV_PROXY_TARGET?: string
  readonly VITE_OFFLINE_MAP_TILE_URL?: string
  readonly VITE_OFFLINE_MAP_MIN_ZOOM?: string
  readonly VITE_OFFLINE_MAP_MAX_ZOOM?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
