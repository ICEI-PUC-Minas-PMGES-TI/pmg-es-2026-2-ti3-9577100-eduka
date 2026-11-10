import { render, screen } from '@testing-library/react'
import { useEffect } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AcessoSuspenso } from '../../pages/AcessoSuspenso/AcessoSuspenso'
import { ACCESS_TOKEN_KEY, apiRequest } from '../../services/api'
import { simularApi, type RespostaApi } from '../../test/api'
import { logarComo } from '../../test/sessao'
import type { SuspensaoPrevista } from '../../types/faturaPlataforma'
import { PainelLayout } from '../PainelLayout/PainelLayout'

const SUSPENSAO = '/config-pagamento/assinatura/suspensao'

const PREVISTA: SuspensaoPrevista = {
  suspensaoEm: '2026-11-16',
  diasRestantes: 10,
  faturaId: 'f1',
  faturaDescricao: 'Plano Avançado',
  faturaValor: 690,
  faturaVencimento: '2026-11-01',
}

/** Uma tela logada que, ao abrir, faz uma requisição qualquer. */
function TelaQueBusca() {
  useEffect(() => {
    apiRequest('/cobrancas/pagina').catch(() => undefined)
  }, [])
  return <p>Conteúdo da tela</p>
}

function abrir(conteudo = <p>Conteúdo da tela</p>) {
  const router = createMemoryRouter(
    [
      { path: '/master/inicio', element: <PainelLayout contexto="Gestão" titulo="Início">{conteudo}</PainelLayout> },
      { path: '/master/plano-e-faturas', element: <p>Tela de faturas</p> },
      { path: '/acesso-suspenso', element: <AcessoSuspenso /> },
    ],
    { initialEntries: ['/master/inicio'] },
  )
  render(<RouterProvider router={router} />)
  return router
}

function api(suspensao: RespostaApi, outras: RespostaApi = { corpo: {} }) {
  return simularApi(({ caminho }) => (caminho === SUSPENSAO ? suspensao : outras))
}

describe('Aviso de suspensão', () => {
  afterEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('mostra ao MASTER em quantos dias a escola será suspensa e leva para o pagamento', async () => {
    logarComo('MASTER')
    api({ corpo: PREVISTA })
    abrir()

    const aviso = await screen.findByRole('alert')
    expect(aviso).toHaveTextContent('O acesso da escola será suspenso em 10 dias (16/11/2026).')
    // O Intl separa "R$" do número com espaço não separável.
    expect(aviso).toHaveTextContent(/"Plano Avançado" de R\$\s690,00/)
    expect(aviso).toHaveTextContent('vencida em 01/11/2026')
    expect(screen.getByRole('link', { name: 'Pagar a fatura' })).toHaveAttribute('href', '/master/plano-e-faturas')
  })

  it('no último dia avisa que é hoje', async () => {
    logarComo('MASTER')
    api({ corpo: { ...PREVISTA, diasRestantes: 0, suspensaoEm: '2026-11-06' } })
    abrir()

    expect(await screen.findByRole('alert')).toHaveTextContent('será suspenso hoje (06/11/2026)')
  })

  it('sem suspensão prevista, não mostra nada', async () => {
    logarComo('MASTER')
    const chamadas = api({ status: 204 })
    abrir()

    expect(await screen.findByText('Conteúdo da tela')).toBeInTheDocument()
    await vi.waitFor(() => expect(chamadas.map((c) => c.caminho)).toContain(SUSPENSAO))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('outros perfis não buscam o aviso', async () => {
    logarComo('FINANCEIRO')
    const chamadas = api({ corpo: PREVISTA })
    abrir()

    expect(await screen.findByText('Conteúdo da tela')).toBeInTheDocument()
    expect(chamadas.map((c) => c.caminho)).not.toContain(SUSPENSAO)
  })

  it('escola suspensa com a tela aberta encerra a sessão e leva ao aviso de suspensão', async () => {
    logarComo('FINANCEIRO')
    api({ status: 204 }, { status: 403, corpo: { status: 403, codigo: 'ESCOLA_SUSPENSA', motivo: 'FATURA_EM_ATRASO' } })
    const router = abrir(<TelaQueBusca />)

    expect(await screen.findByRole('heading', { name: /acesso temporariamente indisponível/i })).toBeInTheDocument()
    expect(screen.getByText(/porque uma fatura do Edúka está em atraso/)).toBeInTheDocument()
    expect(router.state.location.search).toBe('?motivo=FATURA_EM_ATRASO')
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
  })
})
