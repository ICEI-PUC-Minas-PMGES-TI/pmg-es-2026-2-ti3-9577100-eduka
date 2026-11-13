import { paths } from '../routes/paths'

// Em dev o Vite (5173) chama o backend direto na 8080, já liberada no CORS.
// Em prod o Nginx serve front e API na mesma origem, com a API em /api.
const API_URL =
  import.meta.env.VITE_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '/api')

// Chave onde o fluxo de login deve gravar o accessToken retornado por POST /login.
export const ACCESS_TOKEN_KEY = 'eduka.accessToken'

// Rotas abertas do backend (ver SecurityConfig/JwtConfig): login e primeiro acesso.
const ROTAS_PUBLICAS = new Set([
  '/health',
  '/login',
  '/login/codigo',
  '/login/codigo/reenvio',
  '/primeiro-acesso',
  '/senha/recuperacao',
  '/senha/redefinicao',
  // Acesso de demonstração (perfil demo): voltar ao /login/demo com o token
  // da sessão anterior já vencido não pode virar 401.
  '/demo/contas',
  '/demo/entrar',
])

export class ApiError extends Error {
  readonly status: number
  /** Campo `detail` do ProblemDetail do backend, quando veio (ex.: qual dado já existe no 409). */
  readonly detalhe: string | null
  /** Propriedade `codigo` do ProblemDetail, quando veio (ex.: PAGADOR_SEM_CPF no pagamento). */
  readonly codigo: string | null
  /** Propriedade `motivo` do ProblemDetail: por que a escola está suspensa (ESCOLA_SUSPENSA). */
  readonly motivo: string | null

  constructor(
    status: number,
    message: string,
    detalhe: string | null = null,
    codigo: string | null = null,
    motivo: string | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detalhe = detalhe
    this.codigo = codigo
    this.motivo = motivo
  }
}

/**
 * Disparado quando uma requisição logada volta com ESCOLA_SUSPENSA: a escola
 * foi suspensa depois do login. O PainelLayout ouve, encerra a sessão e leva
 * para o aviso (IU04). `detail` é o motivo da suspensão, ou null.
 */
export const EVENTO_ESCOLA_SUSPENSA = 'eduka:escola-suspensa'


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

type Problema = { detalhe: string | null; codigo: string | null; motivo: string | null }

async function lerProblema(resposta: Response): Promise<Problema> {
  try {
    const corpo = (await resposta.json()) as { detail?: unknown; codigo?: unknown; motivo?: unknown } | null
    return {
      detalhe: typeof corpo?.detail === 'string' ? corpo.detail : null,
      codigo: typeof corpo?.codigo === 'string' ? corpo.codigo : null,
      motivo: typeof corpo?.motivo === 'string' ? corpo.motivo : null,
    }
  } catch {
    return { detalhe: null, codigo: null, motivo: null }
  }
}

/**
 * Envia a requisição com o token (exceto rotas públicas) e transforma resposta
 * de erro em ApiError. `accept` diz o que se espera de volta no sucesso; o
 * ProblemDetail de erro é aceito sempre.
 */
async function enviar(caminho: string, init: RequestInit, accept: string): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', `${accept}, application/problem+json`)
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
    const { detalhe, codigo, motivo } = await lerProblema(resposta)
    // Com token, ESCOLA_SUSPENSA é a escola suspensa depois do login: a tela inteira sai do ar.
    if (token && codigo === 'ESCOLA_SUSPENSA') {
      window.dispatchEvent(new CustomEvent(EVENTO_ESCOLA_SUSPENSA, { detail: motivo }))
    }
    throw new ApiError(resposta.status, mensagemPadrao(resposta.status), detalhe, codigo, motivo)
  }
  return resposta
}

export async function apiRequest<T>(caminho: string, init: RequestInit = {}): Promise<T> {
  const resposta = await enviar(caminho, init, 'application/json')
  if (resposta.status === 204) return undefined as T
  // 202 sem corpo (ex.: atalhos de Sandbox): não há JSON para ler.
  const texto = await resposta.text()
  return (texto ? JSON.parse(texto) : undefined) as T
}

/** Baixa um arquivo (ex.: PDF do boleto). Erros chegam como ApiError, igual ao apiRequest. */
export async function apiBaixarArquivo(caminho: string, accept: string, signal?: AbortSignal): Promise<Blob> {
  const resposta = await enviar(caminho, { signal }, accept)
  return resposta.blob()
}

/** Login certo de escola suspensa pelo SUPER_ADMIN (RF24): a tela vai para o aviso (IU04). */
export function ehEscolaSuspensa(erro: unknown): boolean {
  return erro instanceof ApiError && erro.codigo === 'ESCOLA_SUSPENSA'
}

/** Caminho do aviso de suspensão (IU04), com o motivo quando o backend mandou. */
export function rotaAcessoSuspenso(motivo: string | null | undefined): string {
  return motivo ? `${paths.acessoSuspenso}?${new URLSearchParams({ motivo })}` : paths.acessoSuspenso
}

/** Motivo da suspensão no erro, quando é ESCOLA_SUSPENSA. */
export function motivoDaSuspensao(erro: unknown): string | null {
  return erro instanceof ApiError ? erro.motivo : null
}
