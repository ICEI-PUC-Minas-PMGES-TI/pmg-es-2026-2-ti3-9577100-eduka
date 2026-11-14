import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { AulaDaSemana, DashboardProfessor } from '../../../../types/dashboard/dashboard'
import { hojeIso } from '../../../../utils/formatters'
import { Inicio } from './Inicio'

/** O dia de hoje menos `dias`, em "AAAA-MM-DD"; o meio-dia evita virar o dia no fuso. */
function diasAtras(dias: number): string {
  const data = new Date(`${hojeIso()}T12:00:00`)
  data.setDate(data.getDate() - dias)
  return data.toISOString().slice(0, 10)
}

function aula(dados: Partial<AulaDaSemana>): AulaDaSemana {
  return {
    data: hojeIso(),
    diaSemana: 'TERCA',
    horaInicio: '07:00:00',
    horaFim: '07:50:00',
    turmaId: 7,
    turmaNome: '2º ano A',
    disciplinaId: 3,
    disciplinaNome: 'Matemática',
    chamadaFeita: false,
    ...dados,
  }
}

function dashboard(aulasDaSemana: AulaDaSemana[]): DashboardProfessor {
  return {
    turmas: 1,
    disciplinas: 1,
    alunos: 10,
    avaliacoes: 0,
    proximasAvaliacoes: [],
    avaliacoesPendentes: [],
    aulasDaSemana,
  }
}

function abrir(dados: DashboardProfessor) {
  simularApi(({ caminho }) => (caminho === '/portal/professor/dashboard' ? { corpo: dados } : undefined))
  const router = createMemoryRouter(
    [
      { path: '/professor', element: <Inicio /> },
      { path: '/professor/chamada', element: <p>Tela de chamada</p> },
    ],
    { initialEntries: ['/professor'] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('Início do professor', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('mostra as aulas da semana com a chamada feita ou pendente', async () => {
    abrir(
      dashboard([
        aula({ data: diasAtras(1), diaSemana: 'SEGUNDA', chamadaFeita: true }),
        aula({ diaSemana: 'TERCA', turmaNome: '3º ano B', horaInicio: '09:50:00', horaFim: '10:40:00' }),
      ]),
    )

    expect(await screen.findByText('1 aula hoje · 1 chamada pendente')).toBeInTheDocument()
    const segunda = screen.getByRole('region', { name: 'Segunda' })
    expect(within(segunda).getByText('Chamada feita')).toBeInTheDocument()
    const hoje = screen.getByRole('region', { name: 'Terça, hoje' })
    expect(within(hoje).getByText('09h50 – 10h40')).toBeInTheDocument()
    expect(within(hoje).getByRole('button', { name: /fazer a chamada de 3º ano B/i })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Quarta' })).toHaveTextContent('Sem aulas')
  })

  it('fazer chamada abre a chamada já na turma, disciplina e dia da aula', async () => {
    const ontem = diasAtras(1)
    const router = abrir(dashboard([aula({ data: ontem, diaSemana: 'SEGUNDA' })]))

    await userEvent.click(await screen.findByRole('button', { name: /fazer a chamada/i }))

    expect(await screen.findByText('Tela de chamada')).toBeInTheDocument()
    expect(router.state.location.search).toBe(`?turma=7&disciplina=3&data=${ontem}`)
  })

  it('aula que ainda não aconteceu não pede chamada', async () => {
    abrir(dashboard([aula({ data: '2999-01-01', diaSemana: 'SEXTA' })]))

    expect(await screen.findByText('Nenhuma aula hoje')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /fazer a chamada/i })).not.toBeInTheDocument()
  })

  it('sem horário definido, avisa no painel', async () => {
    abrir(dashboard([]))

    expect(await screen.findByText(/ainda não definiu o horário das suas aulas/i)).toBeInTheDocument()
  })
})
