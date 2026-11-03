import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { Assinatura, FaturaPlataforma } from '../../../../types/faturaPlataforma'
import { PlanoEFaturas } from './PlanoEFaturas'
import { proximaFatura } from './proximaFatura'

function fatura(id: string, competencia: string, status: FaturaPlataforma['status'], vencimento: string): FaturaPlataforma {
  const paga = status === 'PAGA'
  return {
    id,
    escolaId: 'e1',
    escolaNome: 'Colégio Santo Agostinho',
    competencia,
    descricao: `Plano Avançado · ${competencia}`,
    valor: 690,
    dataVencimento: vencimento,
    status,
    situacaoPagamento: paga ? 'PAGA' : 'AGUARDANDO_PAGAMENTO',
    formaPagamento: paga ? 'PIX' : null,
    pagamento: paga ? { valorPago: 690, dataPagamento: vencimento, forma: 'PIX' } : null,
    acoes: paga ? ['VER_COMPROVANTE'] : ['PAGAR'],
  }
}

const FATURAS = [
  fatura('f-nov', '2026-11', 'PENDENTE', '2026-11-05'),
  fatura('f-out', '2026-10', 'PAGA', '2026-10-05'),
]

const ASSINATURA: Assinatura = {
  escolaNome: 'Colégio Santo Agostinho',
  plano: 'AVANCADO',
  ativa: true,
  inicioVigencia: '2024-01-15',
  fimVigencia: '2027-01-15',
  limiteUsuarios: 1500,
}

describe('PlanoEFaturas', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('a próxima é a em aberto que vence primeiro', () => {
    expect(
      proximaFatura([
        fatura('a', '2026-12', 'PENDENTE', '2026-12-05'),
        fatura('b', '2026-11', 'EM_ATRASO', '2026-11-05'),
        fatura('c', '2026-10', 'PAGA', '2026-10-05'),
      ])?.id,
    ).toBe('b')
    expect(proximaFatura([fatura('c', '2026-10', 'PAGA', '2026-10-05')])).toBeNull()
  })

  it('mostra a próxima fatura, a assinatura e as anteriores com o comprovante', async () => {
    simularApi(({ caminho }) => {
      if (caminho === '/config-pagamento/faturas') return { corpo: FATURAS }
      if (caminho === '/config-pagamento/assinatura') return { corpo: ASSINATURA }
      return undefined
    })
    render(
      <MemoryRouter>
        <PlanoEFaturas />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('button', { name: /pagar fatura com pix/i })).toBeInTheDocument()
    expect(screen.getByText(/plano avançado · 2026-11 · vence em/i)).toBeInTheDocument()

    expect(await screen.findByText('Avançado')).toBeInTheDocument()
    expect(screen.getByText('Ativa')).toBeInTheDocument()
    expect(screen.getByText('15/01/2024 a 15/01/2027')).toBeInTheDocument()
    expect(screen.getByText('1500')).toBeInTheDocument()

    const tabela = screen.getByRole('region', { name: /faturas da plataforma/i })
    expect(within(tabela).getByRole('button', { name: /ver comprovante/i })).toBeInTheDocument()
    expect(within(tabela).queryByText(/novembro/i)).not.toBeInTheDocument()
  })

  it('sem fatura em aberto, avisa que está tudo em dia', async () => {
    simularApi(({ caminho }) => {
      if (caminho === '/config-pagamento/faturas') return { corpo: [FATURAS[1]] }
      if (caminho === '/config-pagamento/assinatura') return { corpo: ASSINATURA }
      return undefined
    })
    render(
      <MemoryRouter>
        <PlanoEFaturas />
      </MemoryRouter>,
    )

    expect(await screen.findByText(/nenhuma fatura em aberto/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pagar fatura/i })).not.toBeInTheDocument()
  })
})
