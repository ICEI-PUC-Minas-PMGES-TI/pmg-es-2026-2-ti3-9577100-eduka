import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import { rotuloDoDia } from '../../../../test/calendario'
import type { PeriodoLetivo } from '../../../../types/academico/academico'
import type { Cobranca, PaginaCobrancas, StatusCobranca } from '../../../../types/financeiro/cobranca'
import type { RiscoAtraso } from '../../../../types/financeiro/riscoAtraso'
import { hojeIso } from '../../../../utils/formatters'
import { Cobrancas } from './Cobrancas'

function cobranca(n: number, status: StatusCobranca = 'PENDENTE'): Cobranca {
  return {
    id: `cob-${n}`,
    alunoId: `aluno-${n}`,
    alunoNome: `Aluno ${String(n).padStart(2, '0')}`,
    alunoNumeroMatricula: `2026${n}`,
    matriculaId: null,
    turmaId: null,
    turmaNome: '6º ano A',
    responsavelId: null,
    responsavelNome: null,
    descricao: 'Mensalidade de outubro',
    valor: 500,
    dataVencimento: '2026-11-10',
    status,
    canceladaEm: null,
    pagaEm: null,
    pagaAposCancelamento: false,
    formaPagamento: null,
    pagamentoIniciadoEm: null,
    editavel: true,
    pagamento: null,
    criadoEm: '2026-10-01T12:00:00Z',
    atualizadoEm: '2026-10-01T12:00:00Z',
  }
}

const SITUACOES: StatusCobranca[] = ['PENDENTE', 'EM_ATRASO', 'PAGA', 'CANCELADA']

/** Faz o papel do GET /cobrancas/pagina: filtra, corta a página e conta as situações sem status nem nome. */
function paginaDe(cobrancas: Cobranca[], caminho: string): PaginaCobrancas {
  const params = new URL(caminho, 'http://teste').searchParams
  const pagina = Number(params.get('pagina'))
  const tamanho = Number(params.get('tamanho'))
  const status = params.get('status')
  const nome = params.get('nome')?.toLowerCase()
  const alunoId = params.get('alunoId')
  const periodoLetivoId = params.get('periodoLetivoId')
  const vencimentoDe = params.get('vencimentoDe')
  const vencimentoAte = params.get('vencimentoAte')
  // Os filtros de aluno, período e vencimento valem também para os totais, como no backend.
  // No teste, a matrícula "mat-{n}" é do período letivo n; a avulsa (sem matrícula) não tem período.
  const doAluno = cobrancas.filter(
    (c) =>
      (!alunoId || c.alunoId === alunoId) &&
      (!periodoLetivoId || c.matriculaId === `mat-${periodoLetivoId}`) &&
      (!vencimentoDe || c.dataVencimento >= vencimentoDe) &&
      (!vencimentoAte || c.dataVencimento <= vencimentoAte),
  )
  const filtradas = doAluno.filter(
    (c) => (!status || c.status === status) && (!nome || (c.alunoNome ?? '').toLowerCase().includes(nome)),
  )
  return {
    conteudo: filtradas.slice(pagina * tamanho, (pagina + 1) * tamanho),
    pagina,
    tamanho,
    totalElementos: filtradas.length,
    totalPaginas: Math.ceil(filtradas.length / tamanho),
    totaisPorStatus: SITUACOES.map((s) => ({
      status: s,
      quantidade: doAluno.filter((c) => c.status === s).length,
      valor: 0,
    })),
  }
}

/** Mostra para onde a tela levou. */
function Destino() {
  const { pathname, search } = useLocation()
  return <p>Destino: {pathname + search}</p>
}

const RISCOS = '/cobrancas/risco-atraso?nivelMinimo=MEDIO'
const PERIODOS = '/academico/secretaria/periodos-letivos'

function periodoLetivo(id: number, nome: string): PeriodoLetivo {
  return {
    id,
    nome,
    dataInicio: '2026-02-01',
    dataFim: '2026-12-15',
    ativo: true,
    matriculasAbertas: false,
    encerradoEm: null,
  }
}

const PERIODOS_LETIVOS = [periodoLetivo(1, '2026/1'), periodoLetivo(2, '2026/2')]

/** Cobrança com o vencimento e a matrícula (do período letivo de número `periodo`) escolhidos. */
function cobrancaEm(n: number, dataVencimento: string, periodo: number | null): Cobranca {
  return { ...cobranca(n), dataVencimento, matriculaId: periodo === null ? null : `mat-${periodo}` }
}

function iso(data: Date) {
  const mes = String(data.getMonth() + 1).padStart(2, '0')
  const dia = String(data.getDate()).padStart(2, '0')
  return `${data.getFullYear()}-${mes}-${dia}`
}

function risco(alunoId: string, nivel: RiscoAtraso['nivel'], motivo: string): RiscoAtraso {
  return {
    alunoId,
    alunoNome: null,
    alunoNumeroMatricula: '2026',
    nivel,
    pontos: nivel === 'ALTO' ? 5 : 2,
    motivo,
    indicadores: {
      desde: '2025-11-11',
      cobrancasVencidas: 3,
      atrasos: 2,
      atrasosPagos: 1,
      atrasosEmAberto: 1,
      diasMediosAtraso: 20,
      valorEmAtraso: 500,
      valorEmAberto: 500,
    },
    fatores: [],
  }
}

function abrir(
  cobrancas: Cobranca[],
  rota = '/financeiro/cobrancas',
  riscos: RespostaApi = { corpo: [] },
  periodos: RespostaApi = { corpo: PERIODOS_LETIVOS },
): ChamadaApi[] {
  const chamadas = simularApi(({ caminho }) => {
    if (caminho.startsWith('/cobrancas/pagina?')) return { corpo: paginaDe(cobrancas, caminho) }
    if (caminho === RISCOS) return riscos
    if (caminho === PERIODOS) return periodos
    if (caminho === '/alunos/aluno-2') return { corpo: { id: 'aluno-2', nome: 'Aluno 02', matricula: '20262' } }
    return undefined
  })
  render(
    <MemoryRouter initialEntries={[rota]}>
      <Routes>
        <Route path="/financeiro/cobrancas" element={<Cobrancas />} />
        <Route path="*" element={<Destino />} />
      </Routes>
    </MemoryRouter>,
  )
  return chamadas
}

/** Parâmetros da última consulta à página de cobranças. */
function ultimaConsulta(chamadas: ChamadaApi[]) {
  const paginas = chamadas.filter((c) => c.caminho.startsWith('/cobrancas/pagina?'))
  return Object.fromEntries(new URL(paginas[paginas.length - 1].caminho, 'http://teste').searchParams)
}

/** Botão de abrir/fechar do card, pelo nome do aluno. */
function card(nome: string) {
  return screen.getByRole('button', { name: new RegExp(nome) })
}

describe('Cobrancas', () => {
  // Trocar de página rola até o topo da lista; o jsdom não tem scrollIntoView.
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('mostra o risco de atraso com o motivo nas cobranças em aberto do aluno', async () => {
    abrir([cobranca(1), cobranca(2, 'EM_ATRASO'), cobranca(3, 'PAGA'), cobranca(4)], undefined, {
      corpo: [
        risco('aluno-1', 'ALTO', 'Atraso médio de 40 dias; R$ 500,00 vencidos em aberto'),
        risco('aluno-2', 'MEDIO', '2 atrasos nos últimos 12 meses (2 pagos com atraso)'),
        risco('aluno-3', 'ALTO', 'Risco de quem já pagou'),
      ],
    })

    // O risco chega à parte da lista: espera o badge aparecer.
    expect(await within(await screen.findByRole('button', { name: /Aluno 01/ })).findByText('Risco alto')).toBeInTheDocument()
    expect(within(card('Aluno 01')).getByText(/Atraso médio de 40 dias/)).toBeInTheDocument()
    expect(within(card('Aluno 02')).getByText('Risco médio')).toBeInTheDocument()
    expect(within(card('Aluno 02')).getByText(/2 pagos com atraso/)).toBeInTheDocument()
    // Cobrança paga não mostra o risco; aluno sem risco médio ou alto também não.
    expect(screen.queryByText('Risco de quem já pagou')).not.toBeInTheDocument()
    expect(within(card('Aluno 04')).queryByText(/^Risco/)).not.toBeInTheDocument()
  })

  it('sem o risco, a lista aparece do mesmo jeito', async () => {
    abrir([cobranca(1)], undefined, problema(500, 'ERRO_INTERNO'))

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(screen.queryByText(/^Risco/)).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('pede ao backend só a página que mostra, 10 por vez', async () => {
    const chamadas = abrir(Array.from({ length: 23 }, (_, i) => cobranca(i + 1)))

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(screen.getByText('Aluno 10')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 11')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /anterior/i })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Aluno 11')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 01')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toMatchObject({ pagina: '1' })

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('21–23 de 23')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /próxima/i })).toBeDisabled()
  })

  it('as pílulas contam a escola inteira e o filtro volta para a primeira página', async () => {
    const chamadas = abrir([...Array.from({ length: 12 }, (_, i) => cobranca(i + 1)), cobranca(13, 'PAGA')])

    expect(await screen.findByRole('button', { name: /^todas/i })).toHaveTextContent('13')
    expect(screen.getByRole('button', { name: /^pagas/i })).toHaveTextContent('1')

    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^pagas/i }))
    expect(await screen.findByText('Aluno 13')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', status: 'PAGA' })
    expect(screen.queryByRole('navigation', { name: /páginas/i })).not.toBeInTheDocument()
  })

  it('a busca pelo nome vai ao backend, volta à primeira página e mantém o filtro', async () => {
    const chamadas = abrir([...Array.from({ length: 15 }, (_, i) => cobranca(i + 1)), cobranca(16, 'PAGA')])

    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.type(screen.getByRole('searchbox', { name: /nome do aluno/i }), 'aluno 1')
    // Aluno 10 estava na página 1: aparece quando a busca, já na primeira página, volta.
    expect(await screen.findByText('Aluno 10')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 09')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', nome: 'aluno 1' })
    // A digitação espera parar: uma consulta só pelo termo inteiro.
    expect(chamadas.filter((c) => c.caminho.includes('nome='))).toHaveLength(1)
    // As pílulas continuam contando a escola inteira.
    expect(screen.getByRole('button', { name: /^todas/i })).toHaveTextContent('16')

    await userEvent.click(screen.getByRole('button', { name: /^pagas/i }))
    expect(await screen.findByText('Aluno 16')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', status: 'PAGA', nome: 'aluno 1' })
  })

  it('aberta pela aba Financeiro do aluno, mostra só as cobranças dele até tirar o filtro', async () => {
    const chamadas = abrir([cobranca(1), cobranca(2), cobranca(3, 'PAGA')], '/financeiro/cobrancas?aluno=aluno-2')

    expect(await screen.findByRole('button', { name: /Aluno 02/ })).toBeInTheDocument()
    expect(screen.queryByText('Aluno 01')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', alunoId: 'aluno-2' })
    expect(await screen.findByText(/Só as cobranças de/)).toHaveTextContent('Só as cobranças de Aluno 02 · Matrícula 20262')
    // As pílulas contam só as cobranças do aluno.
    expect(screen.getByRole('button', { name: /^todas/i })).toHaveTextContent('1')

    await userEvent.click(screen.getByRole('button', { name: /ver de todos os alunos/i }))

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
    expect(screen.queryByText(/Só as cobranças de/)).not.toBeInTheDocument()
  })

  it('filtrada por um aluno, a nova cobrança já abre com ele escolhido', async () => {
    abrir([cobranca(2)], '/financeiro/cobrancas?aluno=aluno-2')
    // O nome do aluno chega depois da lista, no aviso do filtro.
    await screen.findByText('Aluno 02', { selector: 'strong' })

    await userEvent.click(screen.getByRole('button', { name: /nova cobrança/i }))

    expect(screen.getByText('Destino: /financeiro/cobrancas/nova?aluno=aluno-2')).toBeInTheDocument()
  })

  it('a quantidade por página escolhida vai ao backend', async () => {
    const chamadas = abrir(Array.from({ length: 23 }, (_, i) => cobranca(i + 1)))
    await screen.findByText('Aluno 01')

    await userEvent.click(screen.getByRole('combobox', { name: 'Itens por página' }))
    await userEvent.click(screen.getByRole('option', { name: '20' }))

    expect(await screen.findByText('Aluno 20')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '20' })
    expect(screen.getByText('1–20 de 23')).toBeInTheDocument()
  })

  it('filtra pelo período letivo, volta à primeira página e conta as pílulas só do período', async () => {
    const chamadas = abrir([
      ...Array.from({ length: 11 }, (_, i) => cobrancaEm(i + 1, '2026-03-10', 2)),
      cobrancaEm(12, '2026-03-10', 1),
      cobrancaEm(13, '2026-03-10', null),
    ])
    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox', { name: 'Período letivo' }))
    await userEvent.click(screen.getByRole('option', { name: '2026/1' }))

    expect(await screen.findByText('Aluno 12')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 13')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', periodoLetivoId: '1' })
    expect(screen.getByRole('button', { name: /^todas/i })).toHaveTextContent('1')
    expect(screen.getByText(/avulsas, sem matrícula, não entram/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /limpar período e vencimento/i }))

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
    expect(screen.queryByRole('button', { name: /limpar período e vencimento/i })).not.toBeInTheDocument()
  })

  it('filtra pelo intervalo de vencimento, sem deixar o fim antes do início', async () => {
    const hoje = new Date()
    const dia = (n: number) => new Date(hoje.getFullYear(), hoje.getMonth(), n)
    const chamadas = abrir([
      cobrancaEm(1, iso(dia(4)), 1),
      cobrancaEm(2, iso(dia(5)), 1),
      cobrancaEm(3, iso(dia(20)), null),
      cobrancaEm(4, iso(dia(21)), 1),
    ])
    await screen.findByText('Aluno 01')

    await userEvent.click(screen.getByRole('button', { name: 'Vencimento de' }))
    let calendario = screen.getByRole('dialog', { name: 'Escolher data' })
    await userEvent.click(within(calendario).getByRole('button', { name: rotuloDoDia(dia(5)) }))
    await vi.waitFor(() => expect(screen.queryByText('Aluno 01')).not.toBeInTheDocument())
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', vencimentoDe: iso(dia(5)) })

    await userEvent.click(screen.getByRole('button', { name: 'Vencimento até' }))
    calendario = screen.getByRole('dialog', { name: 'Escolher data' })
    expect(within(calendario).getByRole('button', { name: rotuloDoDia(dia(4)) })).toBeDisabled()
    await userEvent.click(within(calendario).getByRole('button', { name: rotuloDoDia(dia(20)) }))

    await vi.waitFor(() => expect(screen.queryByText('Aluno 04')).not.toBeInTheDocument())
    // A avulsa entra: só o filtro de período letivo depende da matrícula.
    expect(screen.getByText('Aluno 03')).toBeInTheDocument()
    expect(screen.getByText('Aluno 02')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({
      pagina: '0',
      tamanho: '10',
      vencimentoDe: iso(dia(5)),
      vencimentoAte: iso(dia(20)),
    })
    expect(screen.getByRole('button', { name: /^todas/i })).toHaveTextContent('2')
  })

  it('sem os períodos letivos, a lista aparece do mesmo jeito', async () => {
    abrir([cobranca(1)], undefined, undefined, problema(500, 'ERRO_INTERNO'))

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('combobox', { name: 'Período letivo' }))
    expect(screen.getAllByRole('option')).toHaveLength(1)
  })

  it('avisa quando nada passa no filtro', async () => {
    abrir([cobranca(1)])

    await screen.findByText('Aluno 01')
    await userEvent.type(screen.getByRole('searchbox', { name: /nome do aluno/i }), 'Zuleica')
    expect(await screen.findByText('Nenhuma cobrança encontrada para este filtro.')).toBeInTheDocument()
  })

  it('sem paginação com até 10 cobranças', async () => {
    abrir([cobranca(1), cobranca(2)])

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: /páginas/i })).not.toBeInTheDocument()
  })

  it('a seta abre e fecha os detalhes, um card por vez', async () => {
    abrir([cobranca(1), cobranca(2)])

    await screen.findByText('Aluno 01')
    expect(card('Aluno 01')).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Matrícula')).not.toBeInTheDocument()

    await userEvent.click(card('Aluno 01'))
    expect(card('Aluno 01')).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('20261')).toBeInTheDocument()
    expect(screen.getByText('6º ano A')).toBeInTheDocument()

    await userEvent.click(card('Aluno 02'))
    expect(card('Aluno 01')).toHaveAttribute('aria-expanded', 'false')
    expect(card('Aluno 02')).toHaveAttribute('aria-expanded', 'true')

    await userEvent.click(card('Aluno 02'))
    expect(screen.queryByText('Matrícula')).not.toBeInTheDocument()
  })

  it('cancela a cobrança depois de confirmar e lê a lista de novo', async () => {
    const cobrancas = [cobranca(1)]
    const chamadas = simularApi(({ metodo, caminho }) => {
      if (caminho.startsWith('/cobrancas/pagina?')) return { corpo: paginaDe(cobrancas, caminho) }
      if (metodo === 'PATCH' && caminho === '/cobrancas/cob-1/cancelar') {
        cobrancas[0] = { ...cobrancas[0], status: 'CANCELADA', editavel: false }
        return { corpo: cobrancas[0] }
      }
      return undefined
    })
    render(
      <MemoryRouter>
        <Cobrancas />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /Aluno 01/ }))
    await userEvent.click(screen.getByRole('button', { name: /cancelar cobrança/i }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /cancelar cobrança/i }))

    expect(await screen.findByText(/cobrança "mensalidade de outubro" cancelada/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'PATCH' && c.caminho === '/cobrancas/cob-1/cancelar')).toBe(true)
    expect(await screen.findByRole('button', { name: /^canceladas/i })).toHaveTextContent('1')
  })

  it('mostra o motivo quando o cancelamento é recusado por já estar paga', async () => {
    simularApi(({ metodo, caminho }) => {
      if (caminho.startsWith('/cobrancas/pagina?')) return { corpo: paginaDe([cobranca(1)], caminho) }
      if (metodo === 'PATCH') return problema(409, 'COBRANCA_JA_PAGA')
      return undefined
    })
    render(
      <MemoryRouter>
        <Cobrancas />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /Aluno 01/ }))
    await userEvent.click(screen.getByRole('button', { name: /cancelar cobrança/i }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('button', { name: /cancelar cobrança/i }))

    expect(await within(dialogo).findByText(/já foi paga/i)).toBeInTheDocument()
  })

  it('registra o pagamento manual com valor, dia e forma e lê a lista de novo', async () => {
    const cobrancas = [cobranca(1, 'EM_ATRASO')]
    const chamadas = simularApi(({ metodo, caminho }) => {
      if (caminho.startsWith('/cobrancas/pagina?')) return { corpo: paginaDe(cobrancas, caminho) }
      if (metodo === 'PATCH' && caminho === '/cobrancas/cob-1/pagar') {
        cobrancas[0] = { ...cobrancas[0], status: 'PAGA', editavel: false }
        return { corpo: cobrancas[0] }
      }
      return undefined
    })
    render(
      <MemoryRouter>
        <Cobrancas />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /Aluno 01/ }))
    await userEvent.click(screen.getByRole('button', { name: /registrar pagamento/i }))
    const dialogo = screen.getByRole('dialog')
    // Já vem com o valor da cobrança e o dia de hoje; a forma a escola escolhe.
    const valor = within(dialogo).getByRole('textbox', { name: /valor recebido/i })
    expect(valor).toHaveValue('500,00')
    await userEvent.clear(valor)
    await userEvent.type(valor, '480,00')
    await userEvent.click(within(dialogo).getByRole('combobox', { name: /forma de pagamento/i }))
    await userEvent.click(screen.getByRole('option', { name: 'Dinheiro' }))
    await userEvent.click(within(dialogo).getByRole('button', { name: /registrar pagamento/i }))

    expect(await screen.findByText(/pagamento de "mensalidade de outubro" registrado/i)).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'PATCH')?.corpo).toEqual({
      valorPago: 480,
      dataPagamento: hojeIso(),
      forma: 'DINHEIRO',
    })
    expect(await screen.findByRole('button', { name: /^pagas/i })).toHaveTextContent('1')
  })

  it('não envia a baixa sem a forma nem com valor zerado', async () => {
    const chamadas = simularApi(({ caminho }) =>
      caminho.startsWith('/cobrancas/pagina?') ? { corpo: paginaDe([cobranca(1)], caminho) } : undefined,
    )
    render(
      <MemoryRouter>
        <Cobrancas />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /Aluno 01/ }))
    await userEvent.click(screen.getByRole('button', { name: /registrar pagamento/i }))
    const dialogo = screen.getByRole('dialog')
    const valor = within(dialogo).getByRole('textbox', { name: /valor recebido/i })
    await userEvent.clear(valor)
    await userEvent.type(valor, '0')
    await userEvent.click(within(dialogo).getByRole('button', { name: /registrar pagamento/i }))

    expect(within(dialogo).getByText(/informe o valor recebido/i)).toBeInTheDocument()
    expect(within(dialogo).getByText(/escolha como a escola recebeu/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'PATCH')).toBe(false)
  })

  it('quando o Asaas já recebeu, explica a recusa e lê a lista de novo', async () => {
    const chamadas = simularApi(({ metodo, caminho }) => {
      if (caminho.startsWith('/cobrancas/pagina?')) return { corpo: paginaDe([cobranca(1)], caminho) }
      if (metodo === 'PATCH') return problema(409, 'COBRANCA_JA_PAGA')
      return undefined
    })
    render(
      <MemoryRouter>
        <Cobrancas />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /Aluno 01/ }))
    await userEvent.click(screen.getByRole('button', { name: /registrar pagamento/i }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('combobox', { name: /forma de pagamento/i }))
    await userEvent.click(screen.getByRole('option', { name: 'PIX' }))
    const leiturasAntes = chamadas.filter((c) => c.caminho.startsWith('/cobrancas/pagina?')).length
    await userEvent.click(within(dialogo).getByRole('button', { name: /registrar pagamento/i }))

    expect(await within(dialogo).findByText(/asaas já recebeu o pagamento online/i)).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(chamadas.filter((c) => c.caminho.startsWith('/cobrancas/pagina?')).length).toBeGreaterThan(leiturasAntes),
    )
  })
})

