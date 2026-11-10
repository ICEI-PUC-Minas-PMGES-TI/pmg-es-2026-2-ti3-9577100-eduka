import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { Escola } from '../../../../types/escola'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import { hojeIso } from '../../../../utils/formatters'
import { competenciaDaReferencia } from '../../components/FormLancarFatura/regrasFatura'
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
    if (metodo === 'PATCH' && caminho === '/escolas/e1/faturas/f1/cancelar') {
      return { corpo: fatura('f1', 'e1', 'Colégio Bandeirantes', 'CANCELADA') }
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

  it('trocar o filtro volta para a primeira página', async () => {
    const muitas = Array.from({ length: 12 }, (_, i) => fatura(`p${i}`, 'e1', `Escola ${String(i + 1).padStart(2, '0')}`, 'PENDENTE'))
    simularApi(({ caminho }) => {
      if (caminho === '/escolas/faturas') return { corpo: [...muitas, ...FATURAS] }
      if (caminho === '/escolas') return { corpo: ESCOLAS }
      return undefined
    })
    render(
      <MemoryRouter>
        <FaturasPlataforma />
      </MemoryRouter>,
    )
    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /a pagar \(13\)/i }))

    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
    expect(screen.getByText('Escola 01')).toBeInTheDocument()
  })

  it('sugere referência e valor ao escolher a escola, só entre as ativas, e valida antes de enviar', async () => {
    const chamadas = abrir()
    await screen.findByText('Colégio Bandeirantes')
    const form = screen.getByRole('form', { name: /lançar fatura/i })

    await userEvent.click(within(form).getByRole('combobox', { name: /escola/i }))
    expect(screen.queryByRole('option', { name: 'Suspensa' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('option', { name: 'Escola Anglo' }))
    expect(within(form).getByLabelText(/valor/i)).toHaveValue('690,00')
    expect(within(form).getByLabelText(/referência/i)).toHaveDisplayValue(/\/\d{4}$/)

    // Sem vencimento: a validação segura e nada vai ao backend.
    await userEvent.click(within(form).getByRole('button', { name: /lançar fatura/i }))
    expect(within(form).getByText(/informe o vencimento/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
  })

  it('recusa referência sem mês e ano', async () => {
    abrir()
    await screen.findByText('Colégio Bandeirantes')
    const form = screen.getByRole('form', { name: /lançar fatura/i })

    await userEvent.type(within(form).getByLabelText(/referência/i), 'Plano Avançado')
    await userEvent.click(within(form).getByRole('button', { name: /lançar fatura/i }))

    expect(within(form).getByText(/inclua o mês e o ano/i)).toBeInTheDocument()
  })

  it('cancela a fatura selecionada depois de confirmar; a paga não pode ser selecionada', async () => {
    const chamadas = abrir()
    await screen.findByText('Colégio Bandeirantes')
    const cancelar = screen.getByRole('button', { name: /cancelar fatura selecionada/i })
    expect(cancelar).toBeDisabled()
    expect(screen.getByRole('radio', { name: /colégio dom bosco/i })).toBeDisabled()

    await userEvent.click(screen.getByRole('radio', { name: /colégio bandeirantes/i }))
    await userEvent.click(cancelar)
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /^cancelar fatura$/i }))

    expect(chamadas.some((c) => c.metodo === 'PATCH' && c.caminho === '/escolas/e1/faturas/f1/cancelar')).toBe(true)
  })
})

describe('competenciaDaReferencia', () => {
  it.each([
    ['Plano Avançado · novembro/2026', '2026-11'],
    ['Plano Básico · Março/2027', '2027-03'],
    ['Mensalidade 01/2026', '2026-01'],
    ['Plano Avançado', null],
    ['Plano · 13/2026', null],
    ['Plano · marco/2026', null],
  ])('%s -> %s', (referencia, esperado) => {
    expect(competenciaDaReferencia(referencia)).toBe(esperado)
  })
})
