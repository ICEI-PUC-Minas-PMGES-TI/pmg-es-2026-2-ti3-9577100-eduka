import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { CobrancaDoHistorico, HistoricoFinanceiro, StatusCobrancaAluno } from '../../../../types/financeiro/pagamentoAluno'
import { Pagamentos } from './Pagamentos'

function cobranca(
  id: string,
  descricao: string,
  dataVencimento: string,
  status: StatusCobrancaAluno,
  extra: Partial<CobrancaDoHistorico> = {},
): CobrancaDoHistorico {
  return { id, descricao, valor: 500, dataVencimento, status, turmaNome: null, pagaEm: null, pagamento: null, ...extra }
}

const ONLINE = cobranca('c-online', 'Setembro', '2026-09-10', 'PAGA', {
  pagamento: {
    origem: 'ONLINE',
    valorPago: 500,
    valorEstornado: 0,
    dataPagamento: '2026-09-08',
    forma: 'PIX',
    formaManual: null,
    comprovanteUrl: '/portal/aluno/cobrancas/c-online/comprovante',
  },
})

const MANUAL = cobranca('c-manual', 'Agosto', '2026-08-10', 'PAGA', {
  pagamento: {
    origem: 'MANUAL',
    valorPago: 500,
    valorEstornado: 0,
    dataPagamento: '2026-08-09',
    forma: null,
    formaManual: null,
    comprovanteUrl: null,
  },
})

const COBRANCAS = [
  cobranca('c-dez', 'Dezembro', '2026-12-10', 'PENDENTE'),
  cobranca('c-nov', 'Novembro', '2026-11-20', 'PENDENTE'),
  cobranca('c-out', 'Outubro', '2026-10-10', 'EM_ATRASO'),
  ONLINE,
  MANUAL,
  cobranca('c-taxa', 'Taxa', '2026-07-10', 'CANCELADA'),
]

const HISTORICO: HistoricoFinanceiro = {
  alunoId: 'ana',
  alunoNome: 'Ana',
  alunoNumeroMatricula: '2026001',
  resumo: {
    valorEmAberto: 1500,
    valorEmAtraso: 500,
    valorPago: 1000,
    quantidadePendente: 2,
    quantidadeEmAtraso: 1,
    quantidadePaga: 2,
    quantidadeCancelada: 1,
  },
  cobrancas: COBRANCAS,
}

const COMPROVANTE = {
  numero: 'c-online',
  beneficiarioNome: 'Colégio Horizonte',
  beneficiarioDocumento: null,
  pagadorNome: 'Marta',
  pagadorDocumentoMascarado: '***.982.247-**',
  descricao: 'Setembro',
  vencimento: '2026-09-10',
  valorPago: 500,
  dataPagamento: '2026-09-08',
  forma: 'PIX',
  formaManual: null,
  codigoTransacao: 'pay_1',
}

function abrir(historico: HistoricoFinanceiro = HISTORICO) {
  const chamadas = simularApi(({ caminho }) => {
    if (caminho === '/portal/aluno/financeiro') return { corpo: historico }
    if (caminho === ONLINE.pagamento?.comprovanteUrl) return { corpo: COMPROVANTE }
    return undefined
  })
  render(
    <MemoryRouter>
      <Pagamentos />
    </MemoryRouter>,
  )
  return chamadas
}

function descricoesDaTabela(): string[] {
  const linhas = within(screen.getByRole('table')).getAllByRole('row').slice(1)
  return linhas.map((linha) => within(linha).getAllByRole('cell')[0].textContent ?? '')
}

function pilula(nome: RegExp) {
  return screen.getByRole('button', { name: nome })
}

describe('Pagamentos', () => {
  it('destaca a próxima mensalidade: a em aberto que venceu primeiro', async () => {
    abrir()

    const rotulo = await screen.findByText('Cobrança em atraso')
    const destaque = rotulo.closest('section') as HTMLElement
    expect(within(destaque).getByText('Outubro')).toBeInTheDocument()
    expect(within(destaque).getByRole('button', { name: /Pagar Outubro/ })).toBeInTheDocument()
  })

  it('mostra os cards do resumo', async () => {
    abrir()

    const cards = await screen.findByRole('region', { name: 'Resumo financeiro' })
    expect(within(cards).getByText('Total em aberto')).toBeInTheDocument()
    expect(within(cards).getByText('Em atraso')).toBeInTheDocument()
    expect(within(cards).getByText('Total pago')).toBeInTheDocument()
  })

  it('filtra o histórico por situação, com a contagem de cada uma', async () => {
    abrir()
    await screen.findByRole('table')

    expect(descricoesDaTabela()).toEqual(['Dezembro', 'Novembro', 'Outubro', 'Setembro', 'Agosto', 'Taxa'])
    expect(pilula(/^Em aberto/)).toHaveTextContent('3')
    expect(pilula(/^Pagas/)).toHaveTextContent('2')
    expect(pilula(/^Canceladas/)).toHaveTextContent('1')

    await userEvent.click(pilula(/^Em aberto/))
    expect(pilula(/^Em aberto/)).toHaveAttribute('aria-pressed', 'true')
    expect(descricoesDaTabela()).toEqual(['Dezembro', 'Novembro', 'Outubro'])

    await userEvent.click(pilula(/^Pagas/))
    expect(descricoesDaTabela()).toEqual(['Setembro', 'Agosto'])

    await userEvent.click(pilula(/^Canceladas/))
    expect(descricoesDaTabela()).toEqual(['Taxa'])
  })

  it('filtra por período de vencimento, inclusive no futuro, e limpa o período', async () => {
    // O calendário abre no mês de hoje, não no fim do ano que vem.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-15T15:00:00Z'))
    abrir()
    await screen.findByRole('table')

    await userEvent.click(pilula(/Período de vencimento/))
    const calendario = screen.getByRole('dialog', { name: 'Escolher período' })
    await userEvent.click(within(calendario).getByRole('button', { name: /(?<!\d)1 de novembro de 2026/i }))
    await userEvent.click(within(calendario).getByRole('button', { name: /30 de novembro de 2026/i }))
    await userEvent.click(within(calendario).getByRole('button', { name: 'Aplicar' }))

    expect(descricoesDaTabela()).toEqual(['Novembro'])
    // A contagem acompanha o período.
    expect(pilula(/^Todas/)).toHaveTextContent('1')
    expect(pilula(/^Pagas/)).toHaveTextContent('0')

    await userEvent.click(pilula(/^Pagas/))
    expect(screen.getByText('Nenhuma cobrança encontrada para estes filtros.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Limpar período' }))
    expect(descricoesDaTabela()).toEqual(['Setembro', 'Agosto'])
  }, 30000)

  it('Ver comprovante abre o comprovante; a baixa da escola não tem', async () => {
    const chamadas = abrir()
    await screen.findByRole('table')
    await userEvent.click(pilula(/^Pagas/))

    const manual = screen.getByText('Agosto').closest('tr') as HTMLElement
    expect(within(manual).getByText('Sem comprovante')).toBeInTheDocument()
    expect(within(manual).getByText(/Baixa pela escola em 09\/08\/2026/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ver o comprovante de Setembro' }))

    expect(await screen.findByText('Colégio Horizonte')).toBeInTheDocument()
    expect(chamadas.map((c) => c.caminho)).toContain('/portal/aluno/cobrancas/c-online/comprovante')
  })

  it('só a cobrança em aberto tem Pagar, e cancelada não tem ação', async () => {
    abrir()
    await screen.findByRole('table')

    const aberta = within(screen.getByRole('table')).getByText('Novembro').closest('tr') as HTMLElement
    expect(within(aberta).getByRole('button', { name: /Pagar Novembro/ })).toBeInTheDocument()
    const cancelada = screen.getByText('Taxa').closest('tr') as HTMLElement
    expect(within(cancelada).queryByRole('button')).not.toBeInTheDocument()
    expect(within(cancelada).queryByText('Sem comprovante')).not.toBeInTheDocument()
  })

  it('sem cobrança nenhuma, não mostra histórico nem filtros', async () => {
    abrir({ ...HISTORICO, cobrancas: [] })

    expect(await screen.findByText('Nenhuma cobrança por aqui.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Histórico' })).not.toBeInTheDocument()
  })
})
