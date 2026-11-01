import { render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { BoletimDisciplina, BoletimTurma, FrequenciaBoletim } from '../../../../types/portalAluno'
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

function disciplina(id: number, nome: string, freq: FrequenciaBoletim | null): BoletimDisciplina {
  return {
    disciplinaId: id,
    disciplinaNome: nome,
    professorNome: 'Ana Souza',
    avaliacoes: [],
    pontosObtidos: 0,
    pontosAvaliados: 0,
    pontosDistribuidos: 0,
    frequencia: freq,
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
  }
}

async function abrirBoletim(turmas: BoletimTurma[]) {
  simularApi(({ caminho }) => (caminho === '/portal/aluno/boletim' ? { corpo: turmas } : undefined))
  render(<RouterProvider router={createMemoryRouter([{ path: '/', element: <Boletim /> }], { initialEntries: ['/'] })} />)
  await screen.findByRole('region', { name: 'Resumo do boletim' })
}

function cartao(nome: string) {
  return within(screen.getByRole('heading', { level: 3, name: nome }).closest('article') as HTMLElement)
}

describe('Frequência no boletim do aluno', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('preenche o card Frequência, as faltas por disciplina e o texto das regras', async () => {
    await abrirBoletim([
      turma(
        [disciplina(1, 'Matemática', frequencia(20, 2)), disciplina(2, 'Física', frequencia(20, 4))],
        frequencia(40, 6),
      ),
    ])

    const resumo = within(screen.getByRole('region', { name: 'Resumo do boletim' }))
    expect(resumo.getByText('Frequência')).toBeInTheDocument()
    expect(resumo.getByText('85%')).toBeInTheDocument()
    expect(resumo.getByText('6 faltas em 40 aulas')).toBeInTheDocument()
    expect(cartao('Matemática').getByText('Frequência: 90% · 2 faltas em 20 aulas')).toBeInTheDocument()
    expect(cartao('Física').getByText('Frequência: 80% · 4 faltas em 20 aulas')).toBeInTheDocument()
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

    expect(within(cartao('Matemática').getByText(/Frequência: 75%/).closest('header') as HTMLElement).queryByText(
      'Faltas acima do limite',
    )).not.toBeInTheDocument()
    expect(within(cartao('Física').getByText(/Frequência: 74,9%/).closest('header') as HTMLElement).getByText(
      'Faltas acima do limite',
    )).toBeInTheDocument()
  })

  it('alerta também no card geral quando as faltas somadas passam do limite', async () => {
    await abrirBoletim([turma([disciplina(1, 'Matemática', frequencia(10, 3))], frequencia(10, 3))])

    const resumo = within(screen.getByRole('region', { name: 'Resumo do boletim' }))
    expect(resumo.getByText('70%')).toBeInTheDocument()
    expect(resumo.getByText('3 faltas em 10 aulas · acima do limite de faltas')).toBeInTheDocument()
    expect(screen.getByText('Faltas acima do limite')).toBeInTheDocument()
  })

  it('disciplina e turma sem aula mostram "sem aulas", sem 0%, 100% nem alerta', async () => {
    await abrirBoletim([turma([disciplina(1, 'Matemática', null)], null)])

    const resumo = within(screen.getByRole('region', { name: 'Resumo do boletim' }))
    expect(resumo.getByText('Sem aulas registradas ainda')).toBeInTheDocument()
    expect(cartao('Matemática').getByText('Frequência: sem aulas')).toBeInTheDocument()
    expect(screen.queryByText('0%')).not.toBeInTheDocument()
    expect(screen.queryByText('100%')).not.toBeInTheDocument()
    expect(screen.queryByText('Faltas acima do limite')).not.toBeInTheDocument()
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
