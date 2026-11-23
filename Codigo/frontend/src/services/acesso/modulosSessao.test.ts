import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi } from '../../test/api'
import {
  ACCESS_TOKEN_KEY,
  ApiError,
  EVENTO_MODULO_NAO_CONTRATADO,
  MARGEM_RENOVACAO_MS,
  REFRESH_TOKEN_KEY,
  apiRequest,
  ehLimiteDeUsuarios,
  ehModuloNaoContratado,
} from '../api'
import { salvarSessao } from './authService'
import { EVENTO_MODULOS_ALTERADOS, USUARIO_KEY, gravarSessao, modulosDaSessao, temModulo, tirarModulo } from './modulosSessao'

const AGORA = new Date('2026-11-14T12:00:00Z').getTime()

function jwt(venceEmMs: number): string {
  const payload = btoa(JSON.stringify({ sub: 'usuario-1', perfil: 'MASTER', exp: Math.floor(venceEmMs / 1000) }))
  return `cabecalho.${payload.replace(/=+$/, '')}.assinatura`
}

afterEach(() => localStorage.clear())

describe('módulos da sessão', () => {
  it('sem a informação (sessão antiga, Super Admin), a tela mostra tudo', () => {
    localStorage.setItem(USUARIO_KEY, JSON.stringify({ login: 'ana@escola.com' }))

    expect(modulosDaSessao()).toBeNull()
    expect(temModulo('GESTAO_FINANCEIRA')).toBe(true)
  })

  it('o login guarda os módulos da escola junto com o login', () => {
    salvarSessao(
      {
        accessToken: 'a',
        refreshToken: 'r',
        usuarioId: 'u',
        perfil: 'MASTER',
        trocaSenhaObrigatoria: false,
        verificacao: null,
        modulos: ['PORTAL_ALUNO', 'CONTROLE_FREQUENCIA'],
      },
      'gestor@escola.com',
    )

    expect(JSON.parse(localStorage.getItem(USUARIO_KEY) ?? '{}').login).toBe('gestor@escola.com')
    expect(temModulo('PORTAL_ALUNO')).toBe(true)
    expect(temModulo('GESTAO_FINANCEIRA')).toBe(false)
  })

  it('tirar um módulo mantém os outros e avisa a tela', () => {
    gravarSessao('ana@escola.com', ['PORTAL_ALUNO', 'GESTAO_FINANCEIRA'])
    const ouvinte = vi.fn()
    window.addEventListener(EVENTO_MODULOS_ALTERADOS, ouvinte)

    tirarModulo('GESTAO_FINANCEIRA')

    expect(modulosDaSessao()).toEqual(['PORTAL_ALUNO'])
    expect(ouvinte).toHaveBeenCalledTimes(1)
    window.removeEventListener(EVENTO_MODULOS_ALTERADOS, ouvinte)
  })
})

describe('erros de módulo e de limite na API', () => {
  beforeEach(() => {
    localStorage.setItem(ACCESS_TOKEN_KEY, jwt(Date.now() + 30 * 60_000))
    gravarSessao('gestor@escola.com', ['PORTAL_ALUNO', 'GESTAO_FINANCEIRA'])
  })

  it('módulo tirado depois do login sai da sessão e vira uma mensagem em português', async () => {
    const ouvinte = vi.fn()
    window.addEventListener(EVENTO_MODULO_NAO_CONTRATADO, ouvinte)
    simularApi(() => ({
      status: 403,
      corpo: { status: 403, codigo: 'MODULO_NAO_CONTRATADO', modulo: 'GESTAO_FINANCEIRA', detail: 'O modulo nao esta' },
    }))

    const erro = await apiRequest('/cobrancas').catch((e: unknown) => e)

    expect(ehModuloNaoContratado(erro)).toBe(true)
    expect((erro as ApiError).message).toBe(
      'Sua escola não contratou o módulo Gestão Financeira. Fale com a administração da escola.',
    )
    // Telas que mostram o detalhe também recebem a mensagem pronta, não o texto cru do backend.
    expect((erro as ApiError).detalhe).toBe((erro as ApiError).message)
    expect(modulosDaSessao()).toEqual(['PORTAL_ALUNO'])
    expect((ouvinte.mock.calls[0][0] as CustomEvent).detail).toBe('GESTAO_FINANCEIRA')
    window.removeEventListener(EVENTO_MODULO_NAO_CONTRATADO, ouvinte)
  })

  it('limite de usuários cita o número do plano', async () => {
    simularApi(() => ({ status: 409, corpo: { status: 409, codigo: 'LIMITE_USUARIOS_ATINGIDO', limite: 1500 } }))

    const erro = await apiRequest('/alunos', { method: 'POST', body: '{}' }).catch((e: unknown) => e)

    expect(ehLimiteDeUsuarios(erro)).toBe(true)
    expect((erro as ApiError).message).toMatch(/limite de 1\.500 usuários ativos do plano/)
  })

  it('outro 409 continua com o detalhe do backend', async () => {
    simularApi(() => problema(409, 'COBRANCA_JA_PAGA', 'Cobranca ja paga'))

    const erro = (await apiRequest('/cobrancas/1').catch((e: unknown) => e)) as ApiError

    expect(ehLimiteDeUsuarios(erro)).toBe(false)
    expect(erro.detalhe).toBe('Cobranca ja paga')
  })
})

describe('renovação traz os módulos atuais', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(AGORA)
    localStorage.setItem(ACCESS_TOKEN_KEY, jwt(AGORA + MARGEM_RENOVACAO_MS - 1_000))
    localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-antigo')
    gravarSessao('gestor@escola.com', ['PORTAL_ALUNO'])
  })

  it('o que o Super Admin mudou aparece sem novo login', async () => {
    simularApi(({ caminho }) =>
      caminho === '/login/renovacao'
        ? {
            corpo: {
              accessToken: jwt(AGORA + 60 * 60_000),
              refreshToken: 'refresh-novo',
              modulos: ['PORTAL_ALUNO', 'GESTAO_FINANCEIRA'],
            },
          }
        : { corpo: { ok: true } },
    )

    await apiRequest('/alunos')

    expect(modulosDaSessao()).toEqual(['PORTAL_ALUNO', 'GESTAO_FINANCEIRA'])
    expect(JSON.parse(localStorage.getItem(USUARIO_KEY) ?? '{}').login).toBe('gestor@escola.com')
  })
})
