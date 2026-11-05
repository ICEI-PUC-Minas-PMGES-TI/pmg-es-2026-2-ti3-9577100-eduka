import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import { descreverHorarios } from '../../../../types/horario'
import type { TurmaDoProfessor } from '../../../../types/turmaProfessor'
import { MinhasTurmas } from './MinhasTurmas'

const TURMA: TurmaDoProfessor = {
  id: 7,
  nome: '2º ano A',
  codigo: null,
  turno: 'MATUTINO',
  ativa: true,
  periodoLetivoId: 1,
  periodoLetivoNome: '2026/2',
  cursoId: 1,
  cursoNome: 'Ensino Médio',
  disciplinas: [
    {
      id: 3,
      nome: 'Matemática',
      codigo: null,
      cargaHoraria: 80,
      horarios: [
        { diaSemana: 'SEXTA', horaInicio: '07:30:00', horaFim: '08:20:00' },
        { diaSemana: 'SEGUNDA', horaInicio: '07:30:00', horaFim: '08:20:00' },
        { diaSemana: 'QUARTA', horaInicio: '07:30:00', horaFim: '08:20:00' },
      ],
    },
  ],
}

function abrir(turmas: TurmaDoProfessor[] = [TURMA]) {
  simularApi(({ caminho }) => {
    if (caminho === '/portal/professor/turmas') return { corpo: turmas }
    if (caminho === '/portal/professor/turmas/7/alunos') {
      return {
        corpo: [
          { alunoId: 'a1', nome: 'Ana Lima', numeroMatricula: '2026-0001', situacao: 'ATIVA' },
          { alunoId: 'a2', nome: 'Bruno Costa', numeroMatricula: '2026-0002', situacao: 'TRANCADA' },
        ],
      }
    }
    return undefined
  })
  const router = createMemoryRouter(
    [
      { path: '/professor/turmas', element: <MinhasTurmas /> },
      { path: '/professor/avaliacoes', element: <p>Tela de avaliações</p> },
    ],
    { initialEntries: ['/professor/turmas'] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('MinhasTurmas', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('resume o horário como na IU13', () => {
    expect(descreverHorarios(TURMA.disciplinas[0].horarios)).toBe('Seg, Qua e Sex · 07h30')
    expect(
      descreverHorarios([
        { diaSemana: 'TERCA', horaInicio: '09:20:00', horaFim: '10:10:00' },
        { diaSemana: 'QUINTA', horaInicio: '07:30:00', horaFim: '08:20:00' },
      ]),
    ).toBe('Qui · 07h30 · Ter · 09h20')
    expect(descreverHorarios([])).toBeNull()
  })

  it('mostra o horário no card e a grade da semana', async () => {
    abrir()

    expect(await screen.findByText('Seg, Qua e Sex · 07h30')).toBeInTheDocument()
    const grade = screen.getByRole('table')
    expect(within(grade).getByRole('rowheader', { name: '07h30' })).toBeInTheDocument()
    expect(within(grade).getAllByText('2º ano A')).toHaveLength(3)
    expect(within(grade).queryByRole('columnheader', { name: 'Sábado' })).not.toBeInTheDocument()
  })

  it('ver alunos lista quem está na turma', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /ver alunos/i }))

    const dialogo = screen.getByRole('dialog')
    expect(await within(dialogo).findByText('Ana Lima')).toBeInTheDocument()
    expect(within(dialogo).getByText('Bruno Costa')).toBeInTheDocument()
    expect(within(dialogo).getByText('1 aluno ativo')).toBeInTheDocument()
  })

  it('lançar notas abre as avaliações já na turma e na disciplina', async () => {
    const router = abrir()

    await userEvent.click(await screen.findByRole('button', { name: /lançar notas/i }))

    expect(await screen.findByText('Tela de avaliações')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?turma=7&disciplina=3')
  })

  it('criar avaliação abre o cadastro com a turma do card', async () => {
    const router = abrir()

    await userEvent.click(await screen.findByRole('button', { name: /criar avaliação/i }))

    expect(await screen.findByText('Tela de avaliações')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?turma=7&disciplina=3')
    expect(router.state.location.state).toEqual({ abrirCadastro: true, turmaId: 7 })
  })

  it('sem horário definido, avisa no card e na grade', async () => {
    abrir([{ ...TURMA, disciplinas: [{ ...TURMA.disciplinas[0], horarios: [] }] }])

    expect(await screen.findByText('Horário a definir')).toBeInTheDocument()
    expect(screen.getByText(/ainda não definiu o horário/i)).toBeInTheDocument()
  })
})
