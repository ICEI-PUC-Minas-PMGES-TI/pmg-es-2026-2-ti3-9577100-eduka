interface ImportMetaEnv {
  /** URL base da API. Se ausente: http://localhost:8080 em dev e /api em prod. */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
