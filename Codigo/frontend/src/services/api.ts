// Em dev o Vite (5173) chama o backend direto na 8080, já liberada no CORS.
// Em prod o Nginx serve front e API na mesma origem, com a API em /api.
const API_URL =
  import.meta.env.VITE_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '/api')

// Chave onde o fluxo de login deve gravar o accessToken retornado por POST /login.
export const ACCESS_TOKEN_KEY = 'eduka.accessToken'

// Rotas abertas do backend (ver SecurityConfig/JwtConfig): login e primeiro acesso.
const ROTAS_PUBLICAS = new Set(['/health', '/login', '/login/codigo', '/login/codigo/reenvio', '/primeiro-acesso'])

export class ApiError extends Error {
  readonly status: number
  /** Campo `detail` do ProblemDetail do backend, quando veio (ex.: qual dado já existe no 409). */
  readonly detalhe: string | null

  constructor(status: number, message: string, detalhe: string | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detalhe = detalhe
  }
}

function lerToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
  } catch {
    return null
  }
}

function mensagemPadrao(status: number): string {
  if (status === 401) return 'Sua sessão expirou ou você não está autenticado.'
  if (status === 403) return 'Você não tem permissão para acessar este recurso.'
  if (status === 404) return 'Recurso não encontrado.'
  return 'Não foi possível completar a requisição.'
}

async function lerDetalhe(resposta: Response): Promise<string | null> {
  try {
    const corpo: unknown = await resposta.json()
    const detalhe = (corpo as { detail?: unknown } | null)?.detail
    return typeof detalhe === 'string' ? detalhe : null
  } catch {
    return null
  }
}

export async function apiRequest<T>(caminho: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  // Rota pública vai sem token: um token vencido ou de outra chave faria o
  // backend responder 401 antes mesmo de conferir a senha.
  const token = ROTAS_PUBLICAS.has(caminho.split('?')[0]) ? null : lerToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let resposta: Response
  try {
    resposta = await fetch(`${API_URL}${caminho}`, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Não foi possível conectar ao servidor.')
  }

  if (!resposta.ok) {
    throw new ApiError(resposta.status, mensagemPadrao(resposta.status), await lerDetalhe(resposta))
  }

  if (resposta.status === 204) return undefined as T
  return (await resposta.json()) as T
}
