import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type {
  AvaliacaoDoAluno,
  BimestreDaDisciplina,
  BoletimDisciplina,
  BoletimTurma,
  FrequenciaBoletim,
} from '../../../../types/portal/portalAluno'
import { somarFrequencias } from '../../formatacao'
import { Boletim } from './Boletim'

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
  freq: FrequenciaBoletim | null,
  extra: Partial<BoletimDisciplina> = {},
): BoletimDisciplina {
  return {
    disciplinaId: id,
    disciplinaNome: nome,
    professorNome: 'Ana Souza',
    avaliacoes: [],
    pontosObtidos: 0,
    pontosAvaliados: 0,
    pontosDistribuidos: 0,
    frequencia: freq,
    bimestres: [],
    ...extra,
  }
}

function turma(disciplinas: BoletimDisciplina[], freq: FrequenciaBoletim | null, extra: Partial<BoletimTurma> = {}): BoletimTurma {
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
    ...extra,
  }
}

function bimestre(numero: number, obtidos: number, avaliados: number, freq: FrequenciaBoletim | null = null): BimestreDaDisciplina {
  return { numero, pontosObtidos: obtidos, pontosAvaliados: avaliados, pontosDistribuidos: avaliados, frequencia: freq }
}

function avaliacao(id: string, titulo: string, data: string, bim: number, nota: number | null, valor = 10): AvaliacaoDoAluno {
  return {
    id,
    turmaId: 1,
    turmaNome: '1A',
    disciplinaId: 1,
    disciplinaNome: 'Matemática',
    tipo: 'PROVA',
    titulo,
    descricao: null,
    data,
    valor,
    nota,
    observacao: null,
    bimestre: bim,
  }
}

async function abrirBoletim(turmas: BoletimTurma[], url = '/') {
  simularApi(({ caminho }) => (caminho === '/portal/aluno/boletim' ? { corpo: turmas } : undefined))
  render(<RouterProvider router={createMemoryRouter([{ path: '/', element: <Boletim /> }], { initialEntries: [url] })} />)
  await screen.findByRole('region', { name: 'Resumo do boletim' })
}

function resumo() {
  return within(screen.getByRole('region', { name: 'Resumo do boletim' }))
}

/** A linha do quadro de notas da disciplina. */
function linha(nome: string) {
  return within(screen.getByRole('button', { name: new RegExp(nome) }).closest('tr') as HTMLElement)
}

describe('Frequência no boletim do aluno', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('preenche o card Frequência, a frequência de cada disciplina e o texto das regras', async () => {
    await abrirBoletim([
      turma(
        [disciplina(1, 'Matemática', frequencia(20, 2)), disciplina(2, 'Física', frequencia(20, 4))],
        frequencia(40, 6),
      ),
    ])

    expect(resumo().getByText('Frequência')).toBeInTheDocument()
    expect(resumo().getByText('85%')).toBeInTheDocument()
    expect(resumo().getByText('6 faltas em 40 aulas')).toBeInTheDocument()
    expect(linha('Matemática').getByText('90%')).toBeInTheDocument()
    expect(linha('Matemática').getByText('2 faltas em 20')).toBeInTheDocument()
    expect(linha('Física').getByText('80%')).toBeInTheDocument()
    expect(screen.getByText(/limite de 25% de faltas/)).toBeInTheDocument()
    expect(screen.queryByText('Faltas acima do limite')).not.toBeInTheDocument()
  })

  it('exatamente 25% de faltas não alerta; acima disso, alerta só na disciplina que passou', async () => {
    await abrirBoletim([
      turma(
        [disciplina(1, 'Matemática', frequencia(20, 5)), disciplina(2, 'Física', frequencia(1000, 251))],
        frequencia(1020, 256),
      ),
    ])

    expect(linha('Matemática').queryByText('Faltas acima do limite')).not.toBeInTheDocument()
    expect(linha('Física').getByText('74,9%')).toBeInTheDocument()
    expect(linha('Física').getByText('Faltas acima do limite')).toBeInTheDocument()
  })

  it('alerta também no card geral quando as faltas somadas passam do limite', async () => {
    await abrirBoletim([turma([disciplina(1, 'Matemática', frequencia(10, 3))], frequencia(10, 3))])

    expect(resumo().getByText('70%')).toBeInTheDocument()
    expect(resumo().getByText('3 faltas em 10 aulas · acima do limite de faltas')).toBeInTheDocument()
    expect(screen.getByText('Faltas acima do limite')).toBeInTheDocument()
  })

  it('disciplina e turma sem aula mostram "sem aulas", sem 0%, 100% nem alerta', async () => {
    await abrirBoletim([turma([disciplina(1, 'Matemática', null)], null)])

    expect(resumo().getByText('Sem aulas registradas ainda')).toBeInTheDocument()
    expect(linha('Matemática').getByText('Sem aulas')).toBeInTheDocument()
    expect(screen.queryByText('0%')).not.toBeInTheDocument()
    expect(screen.queryByText('100%')).not.toBeInTheDocument()
    expect(screen.queryByText('Faltas acima do limite')).not.toBeInTheDocument()
  })
})

describe('Boletim por ano e bimestre', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  // 2026 em dois semestres: o 1º (concluído) com o 1º e o 2º bimestre, o 2º (ativo) com o 3º e o 4º.
  const primeiroSemestre = turma(
    [
      disciplina(1, 'Matemática', frequencia(20, 2), {
        pontosObtidos: 14,
        pontosAvaliados: 20,
        pontosDistribuidos: 20,
        bimestres: [bimestre(1, 8, 10, frequencia(10, 1)), bimestre(2, 6, 10, frequencia(10, 1))],
        avaliacoes: [
          avaliacao('a1', 'Prova 1', '2026-03-10', 1, 8),
          avaliacao('a2', 'Prova 2', '2026-05-12', 2, 6),
        ],
      }),
    ],
    frequencia(20, 2),
    {
      turmaId: 1,
      turmaNome: '1A',
      periodoLetivoNome: '2026/1',
      situacaoMatricula: 'CONCLUIDA',
      bimestres: [
        { numero: 1, inicio: '2026-02-02', fim: '2026-04-18', frequencia: frequencia(10, 1) },
        { numero: 2, inicio: '2026-04-19', fim: '2026-07-03', frequencia: frequencia(10, 1) },
      ],
    },
  )
  const segundoSemestre = turma(
    [
      disciplina(1, 'Matemática', frequencia(10, 4), {
        professorNome: 'Carlos Lima',
        pontosObtidos: 3,
        pontosAvaliados: 10,
        pontosDistribuidos: 10,
        bimestres: [bimestre(3, 3, 10, frequencia(10, 4)), bimestre(4, 0, 0)],
        avaliacoes: [avaliacao('a3', 'Prova 3', '2026-09-01', 3, 3)],
      }),
    ],
    frequencia(10, 4),
    {
      turmaId: 2,
      turmaNome: '1B',
      periodoLetivoNome: '2026/2',
      bimestres: [
        { numero: 3, inicio: '2026-08-03', fim: '2026-10-10', frequencia: frequencia(10, 4) },
        { numero: 4, inicio: '2026-10-11', fim: '2026-12-18', frequencia: null },
      ],
    },
  )
  const anoPassado = turma([disciplina(9, 'Química', null)], null, {
    turmaId: 3,
    turmaNome: '9A',
    periodoLetivoNome: '2025',
    situacaoMatricula: 'CONCLUIDA',
    ano: 2025,
  })

  it('junta as turmas do ano num boletim só, com uma coluna por bimestre e o professor de agora', async () => {
    await abrirBoletim([segundoSemestre, primeiroSemestre, anoPassado])

    expect(screen.getByRole('heading', { level: 2, name: '2026' })).toBeInTheDocument()
    expect(screen.getByText('2026/1', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('2026/2', { exact: false })).toBeInTheDocument()
    for (const n of [1, 2, 3, 4]) {
      expect(screen.getByRole('columnheader', { name: `${n}º bim.` })).toBeInTheDocument()
    }
    // Uma linha só para Matemática, com o professor da turma ativa e o total do ano (17 de 30).
    expect(screen.getAllByRole('button', { name: /Matemática/ })).toHaveLength(1)
    expect(linha('Matemática').getByText('Carlos Lima')).toBeInTheDocument()
    expect(linha('Matemática').getByText('17')).toBeInTheDocument()
    expect(linha('Matemática').getByText('de 30')).toBeInTheDocument()
    expect(resumo().getByText('57%')).toBeInTheDocument()
    // Química é de 2025: não entra.
    expect(screen.queryByText('Química')).not.toBeInTheDocument()
  })

  it('o bimestre escolhido muda o resumo, a situação e as avaliações da disciplina', async () => {
    const user = userEvent.setup()
    await abrirBoletim([segundoSemestre, primeiroSemestre])

    await user.click(screen.getByRole('button', { name: /3º bimestre/ }))

    expect(screen.getByRole('button', { name: /3º bimestre/ })).toHaveAttribute('aria-pressed', 'true')
    expect(resumo().getByText('30%')).toBeInTheDocument()
    expect(resumo().getByText('3 de 10 pontos avaliados')).toBeInTheDocument()
    expect(resumo().getByText('4 faltas em 10 aulas · acima do limite de faltas')).toBeInTheDocument()
    expect(linha('Matemática').getByText('Abaixo da média')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Matemática/ }))
    expect(screen.getByRole('button', { name: /Matemática/ })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Avaliações do 3º bimestre · Matemática')).toBeInTheDocument()
    expect(screen.getByText('Prova 3')).toBeInTheDocument()
    expect(screen.queryByText('Prova 1')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Ano todo/ }))
    expect(screen.getByText('Avaliações do ano · Matemática')).toBeInTheDocument()
    expect(screen.getByText('Prova 1')).toBeInTheDocument()
    expect(screen.getByText('Prova 3')).toBeInTheDocument()
  })

  it('abre no ano pedido na URL e no bimestre dele', async () => {
    await abrirBoletim([segundoSemestre, primeiroSemestre, anoPassado], '/?ano=2026&bimestre=1')

    expect(screen.getByRole('button', { name: /1º bimestre/ })).toHaveAttribute('aria-pressed', 'true')
    expect(resumo().getByText('80%')).toBeInTheDocument()
  })

  it('mostra o ano passado com a turma concluída e sem bimestres quando o período não tem datas', async () => {
    await abrirBoletim([segundoSemestre, primeiroSemestre, anoPassado], '/?ano=2025')

    expect(screen.getByRole('heading', { level: 2, name: '2025' })).toBeInTheDocument()
    expect(screen.getByText('Concluída')).toBeInTheDocument()
    expect(linha('Química').getByText('Sem notas')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Ano todo/ })).not.toBeInTheDocument()
  })
})

describe('somarFrequencias', () => {
  it('é nula sem nenhuma aula e ignora as turmas sem aula', () => {
    expect(somarFrequencias([], 25)).toBeNull()
    expect(somarFrequencias([null, null], 25)).toBeNull()
    expect(somarFrequencias([null, frequencia(10, 1)], 25)).toEqual(frequencia(10, 1))
  })

  it('só alerta acima do limite', () => {
    expect(somarFrequencias([frequencia(8, 1), frequencia(12, 4)], 25)?.alertaFaltas).toBe(false) // 5/20 = 25%
    expect(somarFrequencias([frequencia(8, 2), frequencia(12, 4)], 25)?.alertaFaltas).toBe(true) // 6/20 = 30%
  })
})
