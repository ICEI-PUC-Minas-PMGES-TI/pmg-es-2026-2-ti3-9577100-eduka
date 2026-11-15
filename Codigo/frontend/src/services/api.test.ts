import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi } from '../test/api'
import {
  ACCESS_TOKEN_KEY,
  ApiError,
  EVENTO_ESCOLA_SUSPENSA,
  MARGEM_RENOVACAO_MS,
  REFRESH_TOKEN_KEY,
  apiRequest,
  nomeDoAnexo,
} from './api'

const AGORA = new Date('2026-11-14T12:00:00Z').getTime()
const RENOVACAO = '/login/renovacao'

/** JWT só com o payload que o front lê; a assinatura não é conferida no cliente. */
function jwt(nome: string, venceEmMs: number): string {
  const payload = btoa(JSON.stringify({ sub: 'usuario-1', perfil: 'MASTER', exp: Math.floor(venceEmMs / 1000), nome }))
  return `cabecalho.${payload.replace(/=+$/, '')}.assinatura`
}

const ACCESS_VALIDO = jwt('valido', AGORA + 30 * 60_000)
const ACCESS_VENCENDO = jwt('vencendo', AGORA + MARGEM_RENOVACAO_MS - 1_000)
const ACCESS_NOVO = jwt('novo', AGORA + 60 * 60_000)

function sessao(access: string, refresh: string | null = 'refresh-antigo') {
  localStorage.setItem(ACCESS_TOKEN_KEY, access)
  if (refresh) localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
}

const RENOVOU = { corpo: { accessToken: ACCESS_NOVO, refreshToken: 'refresh-novo', perfil: 'MASTER' } }

describe('renovação da sessão', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(AGORA)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('não renova enquanto o access ainda tem folga', async () => {
    sessao(ACCESS_VALIDO)
    const chamadas = simularApi(() => ({ corpo: { ok: true } }))

    await apiRequest('/cobrancas')

    expect(chamadas.map((c) => c.caminho)).toEqual(['/cobrancas'])
    expect(chamadas[0].token).toBe(ACCESS_VALIDO)
  })

  it('renova antes de o access vencer e manda a requisição com o token novo', async () => {
    sessao(ACCESS_VENCENDO)
    const chamadas = simularApi(({ caminho }) => (caminho === RENOVACAO ? RENOVOU : { corpo: { ok: true } }))

    await apiRequest('/cobrancas')

    expect(chamadas.map((c) => c.caminho)).toEqual([RENOVACAO, '/cobrancas'])
    expect(chamadas[0]).toMatchObject({ metodo: 'POST', corpo: { refreshToken: 'refresh-antigo' }, token: null })
    expect(chamadas[1].token).toBe(ACCESS_NOVO)
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe(ACCESS_NOVO)
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-novo')
  })

  it('requisições ao mesmo tempo dividem uma única renovação', async () => {
    sessao(ACCESS_VENCENDO)
    const chamadas = simularApi(({ caminho }) => (caminho === RENOVACAO ? RENOVOU : { corpo: {} }))

    await Promise.all([apiRequest('/a'), apiRequest('/b'), apiRequest('/c')])

    expect(chamadas.filter((c) => c.caminho === RENOVACAO)).toHaveLength(1)
    expect(chamadas.filter((c) => c.caminho !== RENOVACAO).every((c) => c.token === ACCESS_NOVO)).toBe(true)
  })

  it('com 401 inesperado, renova uma vez e repete a requisição', async () => {
    sessao(ACCESS_VALIDO)
    let tentativas = 0
    const chamadas = simularApi(({ caminho }) => {
      if (caminho === RENOVACAO) return RENOVOU
      tentativas += 1
      return tentativas === 1 ? { status: 401, corpo: {} } : { corpo: { ok: true } }
    })

    await expect(apiRequest('/cobrancas')).resolves.toEqual({ ok: true })

    expect(chamadas.map((c) => c.caminho)).toEqual(['/cobrancas', RENOVACAO, '/cobrancas'])
    expect(chamadas[2].token).toBe(ACCESS_NOVO)
  })

  it('refresh recusado encerra a sessão e a requisição dá 401', async () => {
    sessao(ACCESS_VENCENDO)
    simularApi(({ caminho }) =>
      caminho === RENOVACAO ? problema(401, 'SESSAO_EXPIRADA') : { status: 401, corpo: {} },
    )

    const erro = await apiRequest('/cobrancas').catch((e: unknown) => e)

    expect(erro).toBeInstanceOf(ApiError)
    expect((erro as ApiError).status).toBe(401)
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
  })

  it('sem refresh guardado, segue com o token atual', async () => {
    sessao(ACCESS_VENCENDO, null)
    const chamadas = simularApi(() => ({ corpo: {} }))

    await apiRequest('/cobrancas')

    expect(chamadas.map((c) => c.caminho)).toEqual(['/cobrancas'])
    expect(chamadas[0].token).toBe(ACCESS_VENCENDO)
  })

  it('escola suspensa na renovação dispara o aviso de suspensão', async () => {
    sessao(ACCESS_VENCENDO)
    const ouvinte = vi.fn()
    window.addEventListener(EVENTO_ESCOLA_SUSPENSA, ouvinte)
    simularApi(({ caminho }) =>
      caminho === RENOVACAO ? problema(403, 'ESCOLA_SUSPENSA') : problema(403, 'ESCOLA_SUSPENSA'),
    )

    await apiRequest('/cobrancas').catch(() => undefined)

    expect(ouvinte).toHaveBeenCalled()
    window.removeEventListener(EVENTO_ESCOLA_SUSPENSA, ouvinte)
  })

  it('rota pública não tenta renovar', async () => {
    sessao(ACCESS_VENCENDO)
    const chamadas = simularApi(() => ({ corpo: { accessToken: null } }))

    await apiRequest('/login', { method: 'POST', body: JSON.stringify({ login: 'a', senha: 'b' }) })

    expect(chamadas.map((c) => c.caminho)).toEqual(['/login'])
    expect(chamadas[0].token).toBeNull()
  })
})

describe('nome do anexo', () => {
  it('lê o filename simples, o codificado em UTF-8 e devolve nulo sem cabeçalho', () => {
    expect(nomeDoAnexo('attachment; filename="relatorio.xlsx"')).toBe('relatorio.xlsx')
    expect(nomeDoAnexo('attachment; filename=relatorio.csv')).toBe('relatorio.csv')
    expect(nomeDoAnexo("attachment; filename=\"x.xlsx\"; filename*=UTF-8''col%C3%A9gio.xlsx")).toBe('colégio.xlsx')
    expect(nomeDoAnexo('attachment')).toBeNull()
    expect(nomeDoAnexo(null)).toBeNull()
  })
})
