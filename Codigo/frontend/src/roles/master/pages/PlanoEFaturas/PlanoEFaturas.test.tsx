import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { Assinatura, FaturaPlataforma } from '../../../../types/faturaPlataforma'
import { PlanoEFaturas } from './PlanoEFaturas'
import { faturasAnteriores, mesPorExtenso, proximaFatura, referenciaDaFatura } from './proximaFatura'

function fatura(id: string, competencia: string, status: FaturaPlataforma['status'], vencimento: string): FaturaPlataforma {
  const paga = status === 'PAGA'
  return {
    id,
    escolaId: 'e1',
    escolaNome: 'Colégio Santo Agostinho',
    competencia,
    descricao: 'Plano Avançado',
    valor: 690,
    dataVencimento: vencimento,
    status,
    situacaoPagamento: paga ? 'PAGA' : status === 'CANCELADA' ? 'CANCELADA' : 'AGUARDANDO_PAGAMENTO',
    formaPagamento: paga ? 'PIX' : null,
    pagamento: paga ? { valorPago: 690, valorEstornado: 0, dataPagamento: vencimento, forma: 'PIX' } : null,
    acoes: paga ? ['VER_COMPROVANTE'] : status === 'CANCELADA' ? [] : ['PAGAR'],
  }
}

const FATURAS = [
  fatura('f-ago', '2026-08', 'PAGA', '2026-08-05'),
  fatura('f-nov', '2026-11', 'PENDENTE', '2026-11-05'),
  fatura('f-out', '2026-10', 'PAGA', '2026-10-05'),
  fatura('f-set', '2026-09', 'CANCELADA', '2026-09-05'),
]

const ASSINATURA: Assinatura = {
  escolaNome: 'Colégio Santo Agostinho',
  plano: 'AVANCADO',
  ativa: true,
  inicioVigencia: '2024-01-15',
  fimVigencia: '2027-01-15',
  limiteUsuarios: 1500,
}

function abrir(faturas: FaturaPlataforma[]) {
  simularApi(({ caminho }) => {
    if (caminho === '/config-pagamento/faturas') return { corpo: faturas }
    if (caminho === '/config-pagamento/assinatura') return { corpo: ASSINATURA }
    return undefined
  })
  render(
    <MemoryRouter>
      <PlanoEFaturas />
    </MemoryRouter>,
  )
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

  it('as anteriores são só as pagas ou canceladas, das mais recentes para as mais antigas', () => {
    expect(faturasAnteriores(FATURAS).map((f) => f.id)).toEqual(['f-out', 'f-set', 'f-ago'])
  })

  it('a referência traz o mês por extenso quando a descrição não tem', () => {
    expect(mesPorExtenso('2026-11')).toBe('novembro/2026')
    expect(referenciaDaFatura(FATURAS[1])).toBe('Plano Avançado · novembro/2026')
    expect(referenciaDaFatura({ ...FATURAS[1], descricao: 'Plano Avançado · 11/2026' })).toBe('Plano Avançado · 11/2026')
  })

  it('mostra a próxima fatura, a assinatura e as anteriores com o comprovante', async () => {
    abrir(FATURAS)

    const destaque = await screen.findByRole('region', { name: 'Próxima fatura' })
    expect(within(destaque).getByText('R$ 690,00')).toBeInTheDocument()
    expect(within(destaque).getByText(/plano avançado · novembro\/2026 · vence em/i)).toBeInTheDocument()
    expect(within(destaque).getByText('05/11/2026')).toBeInTheDocument()
    expect(within(destaque).getByText('A pagar')).toBeInTheDocument()
    expect(within(destaque).getByRole('button', { name: /pagar fatura/i })).toBeInTheDocument()

    const assinatura = screen.getByRole('region', { name: 'Sua assinatura' })
    expect(await within(assinatura).findByText('Avançado')).toBeInTheDocument()
    expect(within(assinatura).getByText('Ativa')).toBeInTheDocument()
    expect(within(assinatura).getByText('15/01/2024 a 15/01/2027')).toBeInTheDocument()
    expect(within(assinatura).getByText('1.500')).toBeInTheDocument()

    const anteriores = screen.getByRole('region', { name: 'Faturas anteriores' })
    const linhas = within(anteriores).getAllByRole('row').slice(1)
    expect(linhas.map((l) => within(l).getAllByRole('cell')[0].textContent)).toEqual([
      'Plano Avançado · outubro/2026',
      'Plano Avançado · setembro/2026',
      'Plano Avançado · agosto/2026',
    ])
    expect(within(anteriores).queryByText(/novembro/i)).not.toBeInTheDocument()
    expect(within(anteriores).getAllByRole('button', { name: /ver comprovante/i })).toHaveLength(2)
    expect(within(linhas[1]).getByText('Cancelada')).toBeInTheDocument()
  })

  it('Pagar fatura abre a mesma escolha de PIX ou boleto do aluno', async () => {
    const usuario = userEvent.setup()
    abrir(FATURAS)

    await usuario.click(await screen.findByRole('button', { name: /pagar fatura/i }))

    const modal = screen.getByRole('dialog')
    expect(within(modal).getByRole('heading', { name: 'Pagar fatura' })).toBeInTheDocument()
    expect(within(modal).getByRole('radio', { name: /pix/i })).toBeChecked()
    expect(within(modal).getByRole('radio', { name: /boleto/i })).toBeInTheDocument()
    expect(within(modal).getByRole('button', { name: 'Pagar com PIX' })).toBeInTheDocument()
  })

  it('fatura em atraso aparece em vermelho e continua podendo ser paga', async () => {
    abrir([fatura('f-nov', '2026-11', 'EM_ATRASO', '2026-11-05')])

    const destaque = await screen.findByRole('region', { name: 'Próxima fatura' })
    expect(within(destaque).getByText('Em atraso')).toBeInTheDocument()
    expect(within(destaque).getByRole('button', { name: /pagar fatura/i })).toBeInTheDocument()
  })

  it('sem fatura em aberto, avisa que está tudo em dia', async () => {
    abrir([FATURAS[2]])

    expect(await screen.findByText(/nenhuma fatura em aberto/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pagar fatura/i })).not.toBeInTheDocument()
  })
})
