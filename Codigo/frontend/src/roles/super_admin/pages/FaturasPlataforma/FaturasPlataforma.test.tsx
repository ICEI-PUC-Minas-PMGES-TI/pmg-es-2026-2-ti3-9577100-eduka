import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { Escola } from '../../../../types/escola'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import { hojeIso } from '../../../../utils/formatters'
import { FaturasPlataforma } from './FaturasPlataforma'

function escola(id: string, nome: string, ativo = true): Escola {
  return {
    id,
    nome,
    cnpjCpf: null,
    endereco: null,
    email: null,
    telefone: null,
    ativo,
    criadoEm: '2026-01-01',
    plano: null,
    inicioVigencia: null,
    fimVigencia: null,
    limiteUsuarios: null,
    modulos: [],
  }
}

function fatura(id: string, escolaId: string, escolaNome: string, status: FaturaPlataforma['status']): FaturaPlataforma {
  const paga = status === 'PAGA'
  return {
    id,
    escolaId,
    escolaNome,
    competencia: '2026-11',
    descricao: 'Plano Avançado',
    valor: 690,
    dataVencimento: '2026-11-05',
    status,
    situacaoPagamento: paga ? 'PAGA' : 'AGUARDANDO_PAGAMENTO',
    formaPagamento: paga ? 'PIX' : null,
    pagamento: paga ? { valorPago: 690, valorEstornado: 0, dataPagamento: hojeIso(), forma: 'PIX' } : null,
    acoes: paga ? ['VER_COMPROVANTE'] : ['CANCELAR'],
  }
}

const FATURAS = [
  fatura('f1', 'e1', 'Colégio Bandeirantes', 'PENDENTE'),
  fatura('f2', 'e2', 'Escola Anglo', 'EM_ATRASO'),
  fatura('f3', 'e3', 'Colégio Dom Bosco', 'PAGA'),
]

const ESCOLAS = [escola('e1', 'Colégio Bandeirantes'), escola('e2', 'Escola Anglo'), escola('e9', 'Suspensa', false)]

function abrir() {
  const chamadas = simularApi(({ metodo, caminho }) => {
    if (caminho === '/escolas/faturas') return { corpo: FATURAS }
    if (caminho === '/escolas') return { corpo: ESCOLAS }
    if (metodo === 'POST' && caminho === '/escolas/e2/faturas') {
      return { status: 201, corpo: fatura('f9', 'e2', 'Escola Anglo', 'PENDENTE') }
    }
    return undefined
  })
  render(
    <MemoryRouter>
      <FaturasPlataforma />
    </MemoryRouter>,
  )
  return chamadas
}

describe('FaturasPlataforma', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('lista as faturas de todas as escolas com os indicadores e filtra pela situação', async () => {
    abrir()

    expect(await screen.findByText('Colégio Bandeirantes')).toBeInTheDocument()
    expect(screen.getByText('Escola Anglo')).toBeInTheDocument()
    const resumo = screen.getByRole('region', { name: /resumo das faturas/i })
    expect(resumo).toHaveTextContent('1 fatura paga')
    expect(resumo).toHaveTextContent('1 escola')

    await userEvent.click(screen.getByRole('button', { name: /em atraso \(1\)/i }))

    expect(screen.getByText('Escola Anglo')).toBeInTheDocument()
    expect(screen.queryByText('Colégio Bandeirantes')).not.toBeInTheDocument()
  })

  it('lança a fatura para a escola escolhida entre as ativas', async () => {
    const chamadas = abrir()
    await screen.findByText('Colégio Bandeirantes')

    await userEvent.click(screen.getByRole('button', { name: /lançar fatura/i }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('combobox', { name: /escola/i }))
    expect(screen.queryByRole('option', { name: 'Suspensa' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('option', { name: 'Escola Anglo' }))
    await userEvent.type(within(dialogo).getByLabelText(/competência/i), '2026-12')
    await userEvent.type(within(dialogo).getByLabelText(/descrição/i), 'Plano Avançado dezembro')
    await userEvent.type(within(dialogo).getByLabelText(/valor/i), '690,00')

    // Sem vencimento: a validação segura e nada vai ao backend.
    await userEvent.click(within(dialogo).getByRole('button', { name: /lançar fatura/i }))
    expect(within(dialogo).getByText(/informe o vencimento/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
  })
})
