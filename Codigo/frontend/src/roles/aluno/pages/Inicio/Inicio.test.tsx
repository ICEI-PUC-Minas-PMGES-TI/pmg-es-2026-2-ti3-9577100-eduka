import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { CobrancaDoHistorico, HistoricoFinanceiro, StatusCobrancaAluno } from '../../../../types/financeiro/pagamentoAluno'
import type { AvaliacaoDoAluno, BoletimDisciplina, BoletimTurma, FrequenciaBoletim } from '../../../../types/portal/portalAluno'
import { hojeIso } from '../../../../utils/formatters'
import { Inicio } from './Inicio'

function diasDepois(dias: number): string {
  const [ano, mes, dia] = hojeIso().split('-').map(Number)
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10)
}

function cobranca(id: string, descricao: string, vencimento: string, status: StatusCobrancaAluno): CobrancaDoHistorico {
  return { id, descricao, valor: 650, dataVencimento: vencimento, status, turmaNome: '1A', pagaEm: null, pagamento: null }
}

function financeiro(cobrancas: CobrancaDoHistorico[]): HistoricoFinanceiro {
  return {
    alunoId: 'ana',
    alunoNome: 'Ana',
    alunoNumeroMatricula: '1',
    resumo: {
      valorEmAberto: 0,
      valorEmAtraso: 0,
      valorPago: 0,
      quantidadePendente: 0,
      quantidadeEmAtraso: 0,
      quantidadePaga: 0,
      quantidadeCancelada: 0,
    },
    cobrancas,
  } as HistoricoFinanceiro
}

function frequencia(totalAulas: number, faltas: number): FrequenciaBoletim {
  return {
    totalAulas,
    faltas,
    percentualFrequencia: Math.round(((totalAulas - faltas) / totalAulas) * 1000) / 10,
    alertaFaltas: faltas * 100 > totalAulas * 25,
  }
}

function disciplina(
  id: number,
  nome: string,
  obtidos: number,
  avaliados: number,
  freq: FrequenciaBoletim | null,
): BoletimDisciplina {
  return {
    disciplinaId: id,
    disciplinaNome: nome,
    professorNome: null,
    avaliacoes: [],
    pontosObtidos: obtidos,
    pontosAvaliados: avaliados,
    pontosDistribuidos: 100,
    frequencia: freq,
    bimestres: [],
  }
}

function turma(disciplinas: BoletimDisciplina[], freq: FrequenciaBoletim | null): BoletimTurma {
  return {
    turmaId: 1,
    turmaNome: '1A',
    periodoLetivoNome: '2026',
    cursoNome: 'Ensino Médio',
    situacaoMatricula: 'ATIVA',
    disciplinas,
    frequencia: freq,
    limiteFaltasPercentual: 25,
    ano: 2026,
    periodoLetivoInicio: null,
    periodoLetivoFim: null,
    bimestres: [],
  }
}

function avaliacao(id: string, titulo: string, data: string): AvaliacaoDoAluno {
  return {
    id,
    turmaId: 1,
    turmaNome: '1A',
    disciplinaId: 10,
    disciplinaNome: 'Matemática',
    tipo: 'PROVA',
    titulo,
    descricao: null,
    data,
    valor: 10,
    nota: null,
    observacao: null,
    bimestre: null,
  }
}

const BOLETIM = [
  turma(
    [
      disciplina(10, 'Matemática', 30, 40, frequencia(20, 2)),
      disciplina(11, 'História', 10, 40, frequencia(20, 6)),
    ],
    frequencia(40, 8),
  ),
]

type Backend = {
  cobrancas?: CobrancaDoHistorico[]
  boletim?: BoletimTurma[]
  avaliacoes?: AvaliacaoDoAluno[]
}

function abrir({ cobrancas = [], boletim = BOLETIM, avaliacoes = [] }: Backend = {}) {
  simularApi(({ caminho }) => {
    if (caminho === '/portal/aluno/financeiro') return { corpo: financeiro(cobrancas) }
    if (caminho === '/portal/aluno/boletim') return { corpo: boletim }
    if (caminho === '/portal/aluno/avaliacoes') return { corpo: avaliacoes }
    return undefined
  })
  render(
    <RouterProvider
      router={createMemoryRouter(
        [
          { path: '/', element: <Inicio /> },
          { path: '/aluno/boletim', element: <p>Tela do boletim</p> },
        ],
        { initialEntries: ['/'] },
      )}
    />,
  )
}

describe('Início do aluno', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('destaca a cobrança em aberto que vence primeiro e abre o pagamento', async () => {
    abrir({
      cobrancas: [
        cobranca('dez', 'Mensalidade de dezembro', diasDepois(30), 'PENDENTE'),
        cobranca('nov', 'Mensalidade de novembro', diasDepois(5), 'PENDENTE'),
        cobranca('out', 'Mensalidade de outubro', diasDepois(-25), 'PAGA'),
      ],
    })

    const destaque = (await screen.findByText('Próxima mensalidade')).closest('section') as HTMLElement
    expect(within(destaque).getByText('Mensalidade de novembro')).toBeInTheDocument()
    expect(within(destaque).getByText('Vence em 5 dias')).toBeInTheDocument()

    await userEvent.click(within(destaque).getByRole('button', { name: /Pagar Mensalidade de novembro/ }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('sem cobrança em aberto, não mostra a mensalidade', async () => {
    abrir({ cobrancas: [cobranca('out', 'Mensalidade de outubro', diasDepois(-25), 'PAGA')] })

    await screen.findByRole('region', { name: 'Seu desempenho' })
    expect(screen.queryByText('Próxima mensalidade')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Pagar/ })).not.toBeInTheDocument()
  })

  it('mostra a média de 0 a 10, as disciplinas em recuperação e a frequência com a disciplina de mais faltas', async () => {
    abrir()

    const resumo = await screen.findByRole('region', { name: 'Seu desempenho' })
    // 40 de 80 pontos avaliados: média 5, abaixo de 6.
    expect(within(resumo).getByText('5')).toBeInTheDocument()
    expect(within(resumo).getByText(/em recuperação: História/)).toBeInTheDocument()
    expect(within(resumo).getByText('80%')).toBeInTheDocument()
    expect(within(resumo).getByText('Mais faltas em História: 6 faltas')).toBeInTheDocument()
  })

  it('lista só as provas e tarefas dos próximos 15 dias', async () => {
    abrir({
      avaliacoes: [
        avaliacao('passada', 'Prova passada', diasDepois(-1)),
        avaliacao('hoje', 'Prova de hoje', diasDepois(0)),
        avaliacao('quinze', 'Trabalho no dia 15', diasDepois(15)),
        avaliacao('longe', 'Prova distante', diasDepois(16)),
      ],
    })

    const secao = await screen.findByRole('region', { name: 'Próximas provas e tarefas' })
    expect(within(secao).getByText('Prova de hoje')).toBeInTheDocument()
    expect(within(secao).getByText('Trabalho no dia 15')).toBeInTheDocument()
    expect(within(secao).queryByText('Prova passada')).not.toBeInTheDocument()
    expect(within(secao).queryByText('Prova distante')).not.toBeInTheDocument()
  })

  it('Ver boletim abre o boletim', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Ver boletim' }))
    expect(await screen.findByText('Tela do boletim')).toBeInTheDocument()
  })

  it('sem turma ativa, avisa em vez de mostrar média zerada', async () => {
    abrir({ boletim: [] })

    expect(await screen.findByText('Você ainda não está cursando nenhuma turma.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Seu desempenho' })).not.toBeInTheDocument()
  })
})
