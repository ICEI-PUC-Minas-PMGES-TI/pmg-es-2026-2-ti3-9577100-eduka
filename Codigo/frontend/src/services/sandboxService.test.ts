import { describe, expect, it, vi } from 'vitest'
import { problema, simularApi } from '../test/api'
import {
  aprovarContaNoSandbox,
  mensagemErroSandbox,
  pagarNoSandboxComoAluno,
  simularPagamentoNoSandbox,
} from './sandboxService'

describe('sandboxService', () => {
  it('aceita o 202 sem corpo dos atalhos', async () => {
    const chamadas: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        chamadas.push(url.replace(/^https?:\/\/[^/]+/, ''))
        return new Response(null, { status: 202 })
      }),
    )

    await expect(aprovarContaNoSandbox()).resolves.toBeUndefined()
    await expect(simularPagamentoNoSandbox('cob-1')).resolves.toBeUndefined()
    await expect(pagarNoSandboxComoAluno('cob-2')).resolves.toBeUndefined()
    expect(chamadas).toEqual([
      '/config-pagamento/conta/sandbox/aprovar',
      '/cobrancas/cob-1/sandbox/confirmar-pagamento',
      '/portal/aluno/cobrancas/cob-2/sandbox/pagar',
    ])
  })

  it('traduz os erros comuns e usa a mensagem do caso para o 400', async () => {
    simularApi(({ caminho }) => {
      if (caminho.includes('cob-400')) return problema(400, 'X')
      if (caminho.includes('cob-recusada')) return problema(400, 'PROVEDOR_RECUSOU', 'Cobrança já recebida')
      if (caminho.includes('cob-404')) return { status: 404 }
      return problema(409, 'SANDBOX_INDISPONIVEL')
    })

    const erroDe = (id: string) => simularPagamentoNoSandbox(id).catch((e: unknown) => e)

    expect(mensagemErroSandbox(await erroDe('cob-400'), 'não emitida')).toBe('não emitida')
    expect(mensagemErroSandbox(await erroDe('cob-recusada'), 'não emitida')).toBe(
      'O Asaas recusou: Cobrança já recebida',
    )
    expect(mensagemErroSandbox(await erroDe('cob-404'), '')).toMatch(/desligados/)
    expect(mensagemErroSandbox(await erroDe('cob-409'), '')).toMatch(/Sandbox/)
    expect(mensagemErroSandbox(new Error('x'), '')).toMatch(/inesperado/)
  })
})
