import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { paths } from '../../../../routes/paths'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { RelatorioFinanceiro } from '../../../../types/financeiro/relatorioFinanceiro'
import { formatarMoeda, hojeIso } from '../../../../utils/formatters'
import { datasDoPeriodoRapido } from './periodos'
import { Relatorios } from './Relatorios'

/** Valor como a tela mostra, com o espaço comum que o getByText compara (o Intl usa o não separável). */
function reais(valor: number): string {
  return formatarMoeda(valor).replace(/\s/g, ' ')
}

const RELATORIO: RelatorioFinanceiro = {
  de: '2026-08-01',
  ate: '2026-09-30',
  recebido: { valor: 1000, quantidade: 3 },
  estornado: { valor: 50, quantidade: 1 },
  despesas: { valor: 700, quantidade: 2 },
  despesasPagas: { valor: 400, quantidade: 1 },
  saldo: 600,
  inadimplencia: { valor: 200, cobrancas: 1, percentual: 16.7 },
  receitasEDespesas: [
    { mes: '2026-08', receitas: 400, despesas: 400, saldo: 0 },
    { mes: '2026-09', receitas: 600, despesas: 0, saldo: 600 },
  ],
  formasPagamento: [
    { forma: 'PIX', valor: 600, quantidade: 2, percentual: 60 },
    { forma: 'BOLETO', valor: 300, quantidade: 1, percentual: 30 },
    { forma: null, valor: 100, quantidade: 1, percentual: 10 },
  ],
  resumo: {
    porCurso: [
      { grupo: 'Ensino Médio', cobrado: 900, recebido: 700, emAberto: 200, inadimplencia: 22.2 },
      { grupo: 'Sem curso', cobrado: 300, recebido: 300, emAberto: 0, inadimplencia: 0 },
    ],
    porTipo: [
      { grupo: 'MENSALIDADE', cobrado: 1000, recebido: 800, emAberto: 200, inadimplencia: 20 },
      { grupo: 'MATERIAL', cobrado: 200, recebido: 200, emAberto: 0, inadimplencia: null },
    ],
    porMes: [{ grupo: '2026-09', cobrado: 1200, recebido: 1000, emAberto: 200, inadimplencia: 16.7 }],
    total: { grupo: null, cobrado: 1200, recebido: 1000, emAberto: 200, inadimplencia: 16.7 },
  },
}

const VAZIO: RelatorioFinanceiro = {
  ...RELATORIO,
  recebido: { valor: 0, quantidade: 0 },
  estornado: { valor: 0, quantidade: 0 },
  despesas: { valor: 0, quantidade: 0 },
  despesasPagas: { valor: 0, quantidade: 0 },
  saldo: 0,
  inadimplencia: { valor: 0, cobrancas: 0, percentual: null },
  receitasEDespesas: [{ mes: '2026-09', receitas: 0, despesas: 0, saldo: 0 }],
  formasPagamento: [
    { forma: 'PIX', valor: 0, quantidade: 0, percentual: null },
    { forma: 'BOLETO', valor: 0, quantidade: 0, percentual: null },
  ],
  resumo: {
    porCurso: [],
    porTipo: [],
    porMes: [],
    total: { grupo: null, cobrado: 0, recebido: 0, emAberto: 0, inadimplencia: null },
  },
}

function abrir(rota: string = paths.financeiro.relatorios) {
  const router = createMemoryRouter(
    [
      { path: paths.financeiro.relatorios, element: <Relatorios /> },
      { path: paths.master.relatorios, element: <Relatorios /> },
    ],
    { initialEntries: [rota] },
  )
  render(<RouterProvider router={router} />)
}

const PLANILHA: RespostaApi = {
  corpo: 'xlsx',
  cabecalhos: {
    'Content-Disposition': 'attachment; filename="relatorio-financeiro-colegio-sao-jose-2026-08-01-a-2026-09-30.xlsx"',
  },
}

function simular(
  relatorio: RespostaApi = { corpo: RELATORIO },
  csv: RespostaApi = { corpo: 'csv' },
  planilha: RespostaApi = PLANILHA,
) {
  return simularApi(({ caminho }) => {
    if (caminho.startsWith('/relatorios/financeiro/exportar')) return csv
    if (caminho.startsWith('/relatorios/financeiro/planilha')) return planilha
    if (caminho.startsWith('/relatorios/financeiro?')) return relatorio
    return undefined
  })
}

/** Parâmetros da última consulta do relatório. */
function parametros(chamadas: { caminho: string }[], prefixo = '/relatorios/financeiro?'): URLSearchParams {
  const ultima = chamadas.filter((c) => c.caminho.startsWith(prefixo)).at(-1)
  return new URLSearchParams(ultima?.caminho.split('?')[1])
}

beforeEach(() => {
  // O jsdom não baixa arquivo nem cria URL de Blob.
  URL.createObjectURL = vi.fn(() => 'blob:relatorio')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('Relatórios financeiros', () => {
  it('abre com este bimestre e mostra os números do período', async () => {
    const chamadas = simular()
    abrir()

    const cards = await screen.findByRole('region', { name: 'Números do período' })
    const bimestre = datasDoPeriodoRapido('BIMESTRE', hojeIso())
    expect(parametros(chamadas).get('de')).toBe(bimestre.de)
    expect(parametros(chamadas).get('ate')).toBe(bimestre.ate)
    expect(screen.getByRole('button', { name: 'Este bimestre' })).toHaveAttribute('aria-pressed', 'true')

    expect(within(cards).getByText(reais(1000))).toBeInTheDocument()
    expect(within(cards).getByText(`3 pagamentos confirmados · ${reais(50)} estornados`)).toBeInTheDocument()
    expect(within(cards).getByText('2 lançamentos no período')).toBeInTheDocument()
    expect(within(cards).getByText(reais(600))).toBeInTheDocument()
    // O saldo desconta as despesas pagas, não as lançadas do card ao lado.
    expect(within(cards).getByText(`Recebido menos ${reais(400)} de despesas pagas`)).toBeInTheDocument()
    expect(within(cards).getByText('16,7%')).toBeInTheDocument()
    expect(within(cards).getByText(`${reais(200)} em 1 cobrança`)).toBeInTheDocument()
  })

  it('mostra o saldo de cada mês no gráfico', async () => {
    simular()
    abrir()

    const painel = await screen.findByRole('region', { name: 'Receitas e despesas no período' })
    expect(within(painel).getByRole('img', { name: `Saldo em setembro de 2026: ${formatarMoeda(600)}` })).toHaveTextContent(
      '+600',
    )
  })

  it('mostra as formas de pagamento com a baixa manual e sem "Comprovante enviado"', async () => {
    simular()
    abrir()

    const painel = await screen.findByRole('region', { name: 'Formas de pagamento' })
    expect(within(painel).getByText('PIX')).toBeInTheDocument()
    expect(within(painel).getByText('Boleto')).toBeInTheDocument()
    expect(within(painel).getByText('Baixa manual')).toBeInTheDocument()
    expect(within(painel).getByText(`${reais(100)} · 10%`)).toBeInTheDocument()
    expect(screen.queryByText(/Comprovante enviado/)).not.toBeInTheDocument()
  })

  it('troca o agrupamento do resumo sem consultar de novo', async () => {
    const chamadas = simular()
    const usuario = userEvent.setup()
    abrir()

    const tabela = await screen.findByRole('table', { name: 'Resumo por curso' })
    expect(within(tabela).getByRole('rowheader', { name: 'Ensino Médio' })).toBeInTheDocument()
    expect(within(tabela).getByRole('rowheader', { name: 'Total do período' })).toBeInTheDocument()
    const consultas = chamadas.length

    await usuario.click(screen.getByRole('button', { name: 'Por tipo de cobrança' }))
    const porTipo = screen.getByRole('table', { name: 'Resumo por tipo de cobrança' })
    expect(within(porTipo).getByRole('rowheader', { name: 'Mensalidade' })).toBeInTheDocument()
    expect(within(porTipo).getByRole('rowheader', { name: 'Material' })).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Por mês' }))
    const porMes = screen.getByRole('table', { name: 'Resumo por mês' })
    expect(within(porMes).getByRole('rowheader', { name: 'Setembro de 2026' })).toBeInTheDocument()

    expect(chamadas).toHaveLength(consultas)
  })

  it('o período rápido consulta na hora', async () => {
    const chamadas = simular()
    const usuario = userEvent.setup()
    abrir()
    await screen.findByRole('region', { name: 'Números do período' })

    await usuario.click(screen.getByRole('button', { name: 'Este ano' }))

    await screen.findByRole('region', { name: 'Números do período' })
    const ano = datasDoPeriodoRapido('ANO', hojeIso())
    expect(parametros(chamadas).get('de')).toBe(ano.de)
    expect(parametros(chamadas).get('ate')).toBe(ano.ate)
    expect(screen.getByRole('button', { name: 'Este ano' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('mostra os vazios de um período sem movimento', async () => {
    simular({ corpo: VAZIO })
    abrir()

    expect(await screen.findByText('Nenhum dinheiro entrou ou saiu neste período.')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma cobrança vence neste período.')).toBeInTheDocument()
    expect(screen.getByText('Nada venceu no período')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('mostra um recebido negativo sem quebrar as barras', async () => {
    simular({
      corpo: {
        ...RELATORIO,
        recebido: { valor: -50, quantidade: 0 },
        saldo: -50,
        receitasEDespesas: [{ mes: '2026-09', receitas: -50, despesas: 0, saldo: -50 }],
        formasPagamento: [{ forma: 'PIX', valor: -50, quantidade: 0, percentual: null }],
      },
    })
    abrir()

    const painel = await screen.findByRole('region', { name: 'Receitas e despesas no período' })
    const barra = within(painel).getByRole('img', { name: /^Receitas em setembro/ })
    expect(barra).toHaveStyle({ height: '0%' })
    const formas = screen.getByRole('region', { name: 'Formas de pagamento' })
    expect(within(formas).getByRole('meter')).toHaveAttribute('aria-valuetext', '—')
  })

  it('sem o relatório, avisa o erro e não deixa exportar', async () => {
    simular(problema(500, 'ERRO'))
    const usuario = userEvent.setup()
    abrir()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Exportar' })).toBeDisabled()

    simular()
    await usuario.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    await screen.findByRole('region', { name: 'Números do período' })
    expect(screen.getByRole('button', { name: 'Exportar' })).toBeEnabled()
  })
})

describe('Exportar em CSV', () => {
  async function abrirModal() {
    const usuario = userEvent.setup()
    abrir()
    await screen.findByRole('region', { name: 'Números do período' })
    await usuario.click(screen.getByRole('button', { name: 'Exportar' }))
    await usuario.click(screen.getByRole('menuitem', { name: /^CSV/ }))
    return usuario
  }

  it('baixa com o período da tela e mostra o nome do arquivo', async () => {
    const chamadas = simular()
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const usuario = await abrirModal()

    const bimestre = datasDoPeriodoRapido('BIMESTRE', hojeIso())
    await usuario.click(screen.getByRole('button', { name: 'Baixar arquivo', hidden: true }))

    const nome = `relatorio-financeiro-${bimestre.de}-a-${bimestre.ate}.csv`
    expect(await screen.findByText(`Arquivo gerado: ${nome}`)).toBeInTheDocument()
    const enviados = parametros(chamadas, '/relatorios/financeiro/exportar?')
    expect(enviados.get('de')).toBe(bimestre.de)
    expect(enviados.get('ate')).toBe(bimestre.ate)
    expect(enviados.get('conjuntos')).toBe('COBRANCAS,PAGAMENTOS')
    expect(enviados.get('separador')).toBe('PONTO_E_VIRGULA')
    expect(clique).toHaveBeenCalledOnce()
  })

  it('envia os conjuntos na ordem do arquivo e o separador escolhido', async () => {
    const chamadas = simular()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const usuario = await abrirModal()

    await usuario.click(screen.getByRole('checkbox', { name: /^Cobranças/, hidden: true }))
    await usuario.click(screen.getByRole('checkbox', { name: /^Despesas/, hidden: true }))
    await usuario.click(screen.getByRole('checkbox', { name: /^Cobranças/, hidden: true }))
    await usuario.click(screen.getByRole('button', { name: 'Vírgula (,)', hidden: true }))
    await usuario.click(screen.getByRole('button', { name: 'Baixar arquivo', hidden: true }))

    await screen.findByText(/^Arquivo gerado/)
    const enviados = parametros(chamadas, '/relatorios/financeiro/exportar?')
    expect(enviados.get('conjuntos')).toBe('COBRANCAS,PAGAMENTOS,DESPESAS')
    expect(enviados.get('separador')).toBe('VIRGULA')
  })

  it('exige ao menos um conjunto', async () => {
    simular()
    const usuario = await abrirModal()

    await usuario.click(screen.getByRole('checkbox', { name: /^Cobranças/, hidden: true }))
    await usuario.click(screen.getByRole('checkbox', { name: /^Pagamentos/, hidden: true }))

    expect(screen.getByText('Escolha ao menos um conjunto de dados.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Baixar arquivo', hidden: true })).toBeDisabled()
  })

  it('mostra o motivo se o arquivo não sair, e mudar a escolha limpa a confirmação', async () => {
    simular({ corpo: RELATORIO }, problema(400, 'INVALIDO', 'Período inválido'))
    const usuario = await abrirModal()

    await usuario.click(screen.getByRole('button', { name: 'Baixar arquivo', hidden: true }))
    expect(await screen.findByText('Período inválido')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Vírgula (,)', hidden: true }))
    expect(screen.queryByText('Período inválido')).not.toBeInTheDocument()
  })
})

describe('Exportar em planilha', () => {
  it('o botão Exportar abre o menu com CSV e planilha', async () => {
    simular()
    const usuario = userEvent.setup()
    abrir()
    await screen.findByRole('region', { name: 'Números do período' })

    const botao = screen.getByRole('button', { name: 'Exportar' })
    expect(botao).toHaveAttribute('aria-haspopup', 'menu')
    await usuario.click(botao)

    const menu = screen.getByRole('menu', { name: 'Exportar' })
    expect(within(menu).getAllByRole('menuitem').map((item) => item.querySelector('strong')?.textContent)).toEqual([
      'CSV',
      'Planilha',
    ])
    expect(botao).toHaveAttribute('aria-expanded', 'true')
  })

  it('baixa direto com o período da tela e o nome que o backend deu, com a escola', async () => {
    const chamadas = simular()
    let baixado = ''
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      baixado = this.download
    })
    const usuario = userEvent.setup()
    abrir()
    await screen.findByRole('region', { name: 'Números do período' })

    await usuario.click(screen.getByRole('button', { name: 'Exportar' }))
    await usuario.click(screen.getByRole('menuitem', { name: /^Planilha/ }))

    await vi.waitFor(() => expect(baixado).toBe('relatorio-financeiro-colegio-sao-jose-2026-08-01-a-2026-09-30.xlsx'))
    const bimestre = datasDoPeriodoRapido('BIMESTRE', hojeIso())
    const enviados = parametros(chamadas, '/relatorios/financeiro/planilha?')
    expect(enviados.get('de')).toBe(bimestre.de)
    expect(enviados.get('ate')).toBe(bimestre.ate)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Exportar' })).toBeEnabled()
  })

  it('pelo teclado: seta para baixo abre, Enter escolhe', async () => {
    const chamadas = simular()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const usuario = userEvent.setup()
    abrir()
    await screen.findByRole('region', { name: 'Números do período' })

    screen.getByRole('button', { name: 'Exportar' }).focus()
    await usuario.keyboard('{ArrowDown}{ArrowDown}{Enter}')

    await vi.waitFor(() =>
      expect(chamadas.some((c) => c.caminho.startsWith('/relatorios/financeiro/planilha?'))).toBe(true),
    )
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Exportar' })).toHaveFocus()
  })

  it('mostra o motivo se a planilha não sair', async () => {
    simular({ corpo: RELATORIO }, { corpo: 'csv' }, problema(400, 'INVALIDO', 'Período inválido'))
    const usuario = userEvent.setup()
    abrir()
    await screen.findByRole('region', { name: 'Números do período' })

    await usuario.click(screen.getByRole('button', { name: 'Exportar' }))
    await usuario.click(screen.getByRole('menuitem', { name: /^Planilha/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Período inválido')
  })
})
