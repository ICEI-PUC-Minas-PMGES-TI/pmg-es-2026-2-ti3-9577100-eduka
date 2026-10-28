interface ImportMetaEnv {
  /** URL base da API. Se ausente: http://localhost:8080 em dev e /api em prod. */
  readonly VITE_API_URL?: string
  /** "true" no build de apresentação: inclui a tela /login/demo. */
  readonly VITE_DEMO?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
