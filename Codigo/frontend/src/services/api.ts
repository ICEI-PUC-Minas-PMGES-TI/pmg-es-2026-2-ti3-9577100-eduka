import { paths } from '../routes/paths'

// Em dev o Vite (5173) chama o backend direto na 8080, já liberada no CORS.
// Em prod o Nginx serve front e API na mesma origem, com a API em /api.
const API_URL =
  import.meta.env.VITE_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '/api')

// Chave onde o fluxo de login deve gravar o accessToken retornado por POST /login.
export const ACCESS_TOKEN_KEY = 'eduka.accessToken'

// Refresh token do login: troca o access por um novo antes de ele vencer
// (POST /login/renovacao), sem pedir a senha de novo.
export const REFRESH_TOKEN_KEY = 'eduka.refreshToken'

const ROTA_RENOVACAO = '/login/renovacao'

/** Faltando menos que isto para o access vencer, a requisição renova antes de sair. */
export const MARGEM_RENOVACAO_MS = 60_000

// Rotas abertas do backend (ver SecurityConfig/JwtConfig): login e primeiro acesso.
const ROTAS_PUBLICAS = new Set([
  '/health',
  '/login',
  '/login/codigo',
  '/login/codigo/reenvio',
  '/primeiro-acesso',
  ROTA_RENOVACAO,
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


function lerStorage(chave: string): string | null {
  try {
    return localStorage.getItem(chave)
  } catch {
    return null
  }
}

function lerToken(): string | null {
  return lerStorage(ACCESS_TOKEN_KEY)
}

/** Instante (ms) em que o JWT vence, pela claim `exp`. Null se não der para ler. */
function expiracaoDoToken(token: string): number | null {
  const payload = token.split('.')[1]
  if (!payload) return null
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const { exp } = JSON.parse(atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='))) as {
      exp?: unknown
    }
    return typeof exp === 'number' ? exp * 1000 : null
  } catch {
    return null
  }
}

let renovacaoEmAndamento: Promise<boolean> | null = null

/**
 * Troca o refresh token guardado por um novo par de tokens. Várias requisições
 * ao mesmo tempo dividem uma única chamada. Devolve se a sessão foi renovada.
 *
 * 401 quer dizer que a sessão acabou de vez (refresh vencido, senha trocada,
 * usuário inativado): os tokens são descartados e a requisição original
 * segue para dar 401, que as telas já tratam voltando ao login.
 */
export function renovarSessao(): Promise<boolean> {
  renovacaoEmAndamento ??= executarRenovacao().finally(() => {
    renovacaoEmAndamento = null
  })
  return renovacaoEmAndamento
}

async function executarRenovacao(): Promise<boolean> {
  const refreshToken = lerStorage(REFRESH_TOKEN_KEY)
  if (!refreshToken) return false

  let resposta: Response
  try {
    resposta = await fetch(`${API_URL}${ROTA_RENOVACAO}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' },
      body: JSON.stringify({ refreshToken }),
    })
  } catch {
    // Sem rede: mantém a sessão; a requisição original sai com o token atual.
    return false
  }

  if (!resposta.ok) {
    const { codigo, motivo } = await lerProblema(resposta)
    if (codigo === 'ESCOLA_SUSPENSA') {
      window.dispatchEvent(new CustomEvent(EVENTO_ESCOLA_SUSPENSA, { detail: motivo }))
    } else if (resposta.status === 401 && lerStorage(REFRESH_TOKEN_KEY) === refreshToken) {
      // Só descarta se ninguém (outra aba, um login novo) gravou outra sessão nesse meio-tempo.
      try {
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(REFRESH_TOKEN_KEY)
      } catch {
        // Ignora erros de storage
      }
    }
    return false
  }

  try {
    const dados = (await resposta.json()) as { accessToken?: unknown; refreshToken?: unknown }
    if (typeof dados.accessToken !== 'string' || typeof dados.refreshToken !== 'string') return false
    localStorage.setItem(ACCESS_TOKEN_KEY, dados.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, dados.refreshToken)
    return true
  } catch {
    return false
  }
}

/** Renova antes de enviar quando o access está vencido ou perto de vencer. */
async function renovarSeVencendo(): Promise<void> {
  const token = lerToken()
  if (!token) return
  const expiraEm = expiracaoDoToken(token)
  if (expiraEm !== null && expiraEm - Date.now() < MARGEM_RENOVACAO_MS) {
    await renovarSessao()
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
  const publica = ROTAS_PUBLICAS.has(caminho.split('?')[0])
  if (!publica) await renovarSeVencendo()

  async function disparar(token: string | null): Promise<Response> {
    if (token) headers.set('Authorization', `Bearer ${token}`)
    try {
      return await fetch(`${API_URL}${caminho}`, { ...init, headers })
    } catch {
      throw new ApiError(0, 'Não foi possível conectar ao servidor.')
    }
  }

  let token = publica ? null : lerToken()
  let resposta = await disparar(token)

  // O access venceu mesmo assim (relógio do aparelho adiantado/atrasado, aba
  // parada): renova uma vez e repete a requisição com o token novo.
  if (resposta.status === 401 && token && (await renovarSessao())) {
    token = lerToken()
    resposta = await disparar(token)
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

/**
 * Como o apiBaixarArquivo, com o nome que o backend deu ao anexo (o
 * Content-Disposition, exposto pelo CORS); nulo se não veio.
 */
export async function apiBaixarArquivoComNome(
  caminho: string,
  accept: string,
): Promise<{ conteudo: Blob; nome: string | null }> {
  const resposta = await enviar(caminho, {}, accept)
  return { conteudo: await resposta.blob(), nome: nomeDoAnexo(resposta.headers.get('Content-Disposition')) }
}

/** `attachment; filename="a.xlsx"` (ou filename*=UTF-8''a.xlsx) → "a.xlsx". */
export function nomeDoAnexo(cabecalho: string | null): string | null {
  if (!cabecalho) return null
  const codificado = /filename\*=UTF-8''([^;]+)/i.exec(cabecalho)
  if (codificado) {
    try {
      return decodeURIComponent(codificado[1].trim())
    } catch {
      // Codificação quebrada: tenta o filename simples.
    }
  }
  const simples = /filename="?([^";]+)"?/i.exec(cabecalho)
  return simples ? simples[1].trim() : null
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
