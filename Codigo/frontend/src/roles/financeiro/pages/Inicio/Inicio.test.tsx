import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { paths } from '../../../../routes/paths'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { DashboardFinanceiro } from '../../../../types/dashboard'
import { formatarMoeda, hojeIso } from '../../../../utils/formatters'
import { Cobrancas } from '../Cobrancas/Cobrancas'
import { Inicio } from './Inicio'

/** Valor como a tela mostra, com o espaço comum que o getByText compara (o Intl usa o não separável). */
function reais(valor: number): string {
  return formatarMoeda(valor).replace(/\s/g, ' ')
}

// Os 12 meses que o backend manda, de novembro de 2025 a outubro de 2026.
const DOZE_MESES = Array.from({ length: 12 }, (_, i) => {
  const mes = ((10 + i) % 12) + 1
  return `${mes > 10 ? 2025 : 2026}-${String(mes).padStart(2, '0')}`
})

const MESES_SEM_MOVIMENTO = DOZE_MESES.map((mes) => ({
  mes,
  entrou: 0,
  saiu: 0,
}))

const VAZIO: DashboardFinanceiro = {
  recebidoNoMes: { valor: 0, quantidade: 0 },
  faltaReceber: { valor: 0, quantidade: 0 },
  emAtraso: { alunos: 0, cobrancas: 0, valor: 0, inadimplencia: null },
  despesasAPagar: { valor: 0, quantidade: 0, proximoVencimento: null },
  ultimosMeses: MESES_SEM_MOVIMENTO,
  vencemNaSemana: [],
  ultimosPagamentos: [],
  ultimasAcoes: [],
}

const PAINEL: DashboardFinanceiro = {
  recebidoNoMes: { valor: 295, quantidade: 2 },
  faltaReceber: { valor: 850, quantidade: 3 },
  emAtraso: { alunos: 1, cobrancas: 1, valor: 300, inadimplencia: 46 },
  despesasAPagar: { valor: 150, quantidade: 2, proximoVencimento: '2026-10-31' },
  ultimosMeses: [...MESES_SEM_MOVIMENTO.slice(0, 11), { mes: '2026-10', entrou: 295, saiu: 40 }],
  vencemNaSemana: [
    {
      id: 'cob-atrasada',
      alunoId: 'aluno-ana',
      alunoNome: 'Ana Souza',
      descricao: 'Mensalidade de setembro',
      valor: 300,
      dataVencimento: '2026-09-10',
      status: 'EM_ATRASO',
    },
    {
      id: 'cob-hoje',
      alunoId: 'aluno-bruno',
      alunoNome: 'Bruno Lima',
      descricao: 'Material didático',
      valor: 150,
      dataVencimento: hojeIso(),
      status: 'PENDENTE',
    },
  ],
  ultimosPagamentos: [
    {
      cobrancaId: 'cob-pix',
      alunoNome: 'Carla Dias',
      descricao: 'Mensalidade de outubro',
      valor: 195,
      forma: 'PIX',
      origem: 'ONLINE',
      pagoEm: '2026-10-27T17:32:00Z',
    },
    {
      cobrancaId: 'cob-manual',
      alunoNome: 'Bruno Lima',
      descricao: 'Taxa de matrícula',
      valor: 100,
      forma: null,
      origem: 'MANUAL',
      pagoEm: '2026-10-26T12:05:00Z',
    },
  ],
  ultimasAcoes: [],
}

const PAGINA_VAZIA = { conteudo: [], pagina: 0, tamanho: 10, totalElementos: 0, totalPaginas: 0, totaisPorStatus: [] }

function abrir(painel: RespostaApi) {
  const chamadas = simularApi(({ caminho }) => {
    if (caminho === '/dashboard/financeiro') return painel
    if (caminho.startsWith('/cobrancas/pagina?')) return { corpo: PAGINA_VAZIA }
    // O período do calendário: de 5 a 20 de outubro de 2026.
    if (caminho === '/dashboard/financeiro/movimento?de=2026-10-05&ate=2026-10-20') {
      return { corpo: [{ mes: '2026-10', entrou: 70, saiu: 10 }] }
    }
    return undefined
  })
  const router = createMemoryRouter(
    [
      { path: paths.financeiro.inicio, element: <Inicio /> },
      { path: paths.financeiro.cobrancas, element: <Cobrancas /> },
      { path: paths.financeiro.novaCobranca, element: <p>Tela de nova cobrança</p> },
      { path: paths.financeiro.despesas, element: <p>Tela de despesas</p> },
    ],
    { initialEntries: [paths.financeiro.inicio] },
  )
  render(<RouterProvider router={router} />)
  return chamadas
}

describe('Início do financeiro', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('mostra os totais do mês, o que vence na semana e os últimos pagamentos', async () => {
    abrir({ corpo: PAINEL })

    const resumo = await screen.findByRole('region', { name: 'Resumo financeiro' })
    expect(within(resumo).getByText(reais(295))).toBeInTheDocument()
    expect(within(resumo).getByText('2 cobranças')).toBeInTheDocument()
    expect(within(resumo).getByText('3 cobranças em aberto')).toBeInTheDocument()
    expect(within(resumo).getByText(`${reais(300)} · 46% de inadimplência`)).toBeInTheDocument()
    expect(within(resumo).getByText('2 despesas este mês · próxima em 31/10')).toBeInTheDocument()

    const semana = screen.getByRole('region', { name: 'Vencem esta semana' })
    expect(within(semana).getByText('Ana Souza')).toBeInTheDocument()
    expect(within(semana).getByText('Em atraso')).toBeInTheDocument()
    expect(within(semana).getByText('Vence hoje')).toBeInTheDocument()

    // Os horários aparecem no fuso da escola (UTC-3).
    const pagamentos = screen.getByRole('region', { name: 'Últimos pagamentos' })
    expect(within(pagamentos).getByText('Mensalidade de outubro · PIX · 27/10 às 14:32')).toBeInTheDocument()
    expect(within(pagamentos).getByText('Taxa de matrícula · Baixa manual · 26/10 às 09:05')).toBeInTheDocument()

    expect(screen.getByRole('img', { name: `Entrou em outubro de 2026: ${formatarMoeda(295)}` })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: `Saiu em outubro de 2026: ${formatarMoeda(40)}` })).toBeInTheDocument()
  })

  it('escola sem movimento vê o painel zerado e os avisos de vazio', async () => {
    abrir({ corpo: VAZIO })

    expect(await screen.findByText('Nada venceu ainda')).toBeInTheDocument()
    expect(screen.getByText('Nada a pagar este mês')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma cobrança vence esta semana e nada está em atraso.')).toBeInTheDocument()
    expect(screen.getByText('Nenhum pagamento recebido ainda.')).toBeInTheDocument()
    expect(screen.getByText('Nenhum dinheiro entrou ou saiu nos últimos 6 meses.')).toBeInTheDocument()
  })

  it('o filtro troca quantos meses o gráfico mostra, com os totais e o saldo do período', async () => {
    // Todo mês entrou 100 e saiu 30; em dezembro de 2025 saiu uma despesa grande.
    const ano = DOZE_MESES.map((mes) => ({ mes, entrou: 100, saiu: mes === '2025-12' ? 2000 : 30 }))
    abrir({ corpo: { ...PAINEL, ultimosMeses: ano } })

    const grafico = await screen.findByRole('list', { name: 'Dinheiro que entrou e saiu por mês' })
    const meses = () => within(grafico).getAllByRole('listitem')
    expect(meses()).toHaveLength(6)
    expect(screen.getByRole('button', { name: '6 meses' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText(reais(600))).toBeInTheDocument()
    expect(screen.getByText(`Saldo + ${reais(420)}`)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '12 meses' }))
    expect(meses()).toHaveLength(12)
    expect(screen.getByText('Cobranças recebidas e despesas pagas nos últimos 12 meses.')).toBeInTheDocument()
    // 1.200 entraram e 11 × 30 + 2.000 = 2.330 saíram.
    expect(screen.getByText(`Saldo − ${reais(1130)}`)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '3 meses' }))
    expect(meses()).toHaveLength(3)
    expect(screen.getByRole('img', { name: `Entrou em outubro de 2026: ${formatarMoeda(100)}` })).toBeInTheDocument()
  })

  it('o calendário busca o período escolhido e mostra só os meses dele', async () => {
    // O calendário abre no mês de hoje: outubro de 2026.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-30T15:00:00Z'))
    const chamadas = abrir({ corpo: PAINEL })

    await userEvent.click(await screen.findByRole('button', { name: /personalizado/i }))
    const calendario = screen.getByRole('dialog', { name: 'Escolher período' })
    expect(within(calendario).getByRole('button', { name: 'Aplicar' })).toBeDisabled()
    await userEvent.click(within(calendario).getByRole('button', { name: /(?<!\d)5 de outubro de 2026/i }))
    await userEvent.click(within(calendario).getByRole('button', { name: /20 de outubro de 2026/i }))
    expect(within(calendario).getByText('05/10/2026 – 20/10/2026')).toBeInTheDocument()
    await userEvent.click(within(calendario).getByRole('button', { name: 'Aplicar' }))

    expect(await screen.findByText(`Saldo + ${reais(60)}`)).toBeInTheDocument()
    expect(chamadas.map((c) => c.caminho)).toContain('/dashboard/financeiro/movimento?de=2026-10-05&ate=2026-10-20')
    expect(screen.getByText('Cobranças recebidas e despesas pagas de 05/10/2026 a 20/10/2026.')).toBeInTheDocument()
    const grafico = screen.getByRole('list', { name: 'Dinheiro que entrou e saiu por mês' })
    expect(within(grafico).getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByRole('button', { name: /05\/10\/2026 – 20\/10\/2026/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '6 meses' })).toHaveAttribute('aria-pressed', 'false')

    // Voltar a um atalho usa os meses que o painel já trouxe.
    await userEvent.click(screen.getByRole('button', { name: '3 meses' }))
    expect(within(screen.getByRole('list', { name: 'Dinheiro que entrou e saiu por mês' })).getAllByRole('listitem'))
      .toHaveLength(3)
  })

  it('o atalho de atraso abre as cobranças já filtradas em Atrasadas', async () => {
    const chamadas = abrir({ corpo: PAINEL })

    await userEvent.click(await screen.findByRole('button', { name: 'Ver cobranças em atraso' }))

    expect(await screen.findByRole('button', { name: /Atrasadas/ })).toHaveAttribute('aria-pressed', 'true')
    expect(chamadas.map((c) => c.caminho)).toContain('/cobrancas/pagina?pagina=0&tamanho=10&status=EM_ATRASO')
  })

  it.each([
    ['Nova cobrança', 'Tela de nova cobrança'],
    ['Lançar uma despesa', 'Tela de despesas'],
  ])('o atalho %s abre a tela dele', async (atalho, tela) => {
    abrir({ corpo: PAINEL })

    await userEvent.click(await screen.findByRole('button', { name: atalho }))

    expect(await screen.findByText(tela)).toBeInTheDocument()
  })

  it('mostra o erro e busca de novo no Tentar novamente', async () => {
    let falhar = true
    const chamadas = simularApi(({ caminho }) => {
      if (caminho !== '/dashboard/financeiro') return undefined
      return falhar ? problema(500, 'ERRO_INTERNO') : { corpo: PAINEL }
    })
    render(
      <RouterProvider router={createMemoryRouter([{ path: '/', element: <Inicio /> }], { initialEntries: ['/'] })} />,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível completar a requisição.')

    falhar = false
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByRole('region', { name: 'Resumo financeiro' })).toBeInTheDocument()
    expect(chamadas).toHaveLength(2)
  })
})
