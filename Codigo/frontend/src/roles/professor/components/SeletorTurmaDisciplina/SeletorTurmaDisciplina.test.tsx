import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { HorarioDaDisciplina } from '../../../../types/academico/horario'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import type { TurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import { SeletorTurmaDisciplina } from './SeletorTurmaDisciplina'

// Quarta-feira, 25/11/2026, 09:40 em Brasília (12:40 UTC).
const AGORA = new Date('2026-11-25T12:40:00Z')

function turma(id: number, nome: string, extra: Partial<TurmaDoProfessor> = {}): TurmaDoProfessor {
  return {
    id,
    nome,
    codigo: null,
    turno: 'MATUTINO',
    ativa: true,
    periodoLetivoId: 1,
    periodoLetivoNome: '2026',
    cursoId: 1,
    cursoNome: 'Fundamental',
    alunosAtivos: 30,
    disciplinas: [],
    ...extra,
  }
}

function opcao(t: TurmaDoProfessor, disciplina: string, horarios: HorarioDaDisciplina[] = []): TurmaDisciplina {
  const disciplinaId = disciplina === 'Matemática' ? 10 : 11
  return {
    chave: `${t.id}-${disciplinaId}`,
    turma: t,
    disciplina: { id: disciplinaId, nome: disciplina, codigo: null, cargaHoraria: 60, horarios },
  }
}

const SEXTO = turma(1, '6º ano A', { alunosAtivos: 32 })
const PRIMEIRO = turma(2, '1º ano A', { alunosAtivos: 28 })
const TERCEIRO = turma(3, '3º ano A', { alunosAtivos: 1 })
const ANTIGA = turma(4, '9º ano B', { ativa: false, periodoLetivoNome: '2025' })

const OPCOES: TurmaDisciplina[] = [
  // A ordem de chegada é por nome; a lista reordena pela próxima aula.
  opcao(PRIMEIRO, 'Matemática', [{ diaSemana: 'QUARTA', horaInicio: '14:00:00', horaFim: '14:50:00' }]),
  opcao(TERCEIRO, 'Matemática', [{ diaSemana: 'QUINTA', horaInicio: '08:00:00', horaFim: '08:50:00' }]),
  opcao(SEXTO, 'Matemática', [{ diaSemana: 'QUARTA', horaInicio: '09:20:00', horaFim: '10:10:00' }]),
  opcao(SEXTO, 'Física'),
  opcao(ANTIGA, 'Matemática', [{ diaSemana: 'QUARTA', horaInicio: '09:20:00', horaFim: '10:10:00' }]),
]

function montar(selecionada = OPCOES[2]) {
  const onChange = vi.fn()
  render(<SeletorTurmaDisciplina opcoes={OPCOES} selecionada={selecionada} onChange={onChange} />)
  return onChange
}

describe('SeletorTurmaDisciplina', () => {
  beforeEach(() => {
    // Só o relógio: os timers de verdade seguem para o userEvent.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(AGORA)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('mostra a turma e a disciplina escolhidas no botão', () => {
    montar()

    expect(screen.getByRole('button', { name: 'Turma: 6º ano A · Matemática' })).toBeInTheDocument()
  })

  it('lista por disciplina, com os alunos e a próxima aula, a mais próxima primeiro', async () => {
    montar()

    await userEvent.click(screen.getByRole('button', { name: /Turma/ }))

    const matematica = screen.getByRole('group', { name: 'Matemática · 2026' })
    const opcoes = within(matematica).getAllByRole('option')
    expect(opcoes.map((o) => o.textContent)).toEqual([
      '6º ano A32 alunosAula agora',
      '1º ano A28 alunosHoje 14:00',
      '3º ano A1 alunoAmanhã 08:00',
    ])
    expect(opcoes[0]).toHaveAttribute('aria-selected', 'true')
    // Sem horário na grade, a opção vem sem próxima aula.
    expect(within(screen.getByRole('group', { name: 'Física · 2026' })).getByRole('option')).toHaveTextContent(
      /^6º ano A32 alunos$/,
    )
  })

  it('deixa as turmas encerradas recolhidas até pedir', async () => {
    montar()
    await userEvent.click(screen.getByRole('button', { name: /Turma/ }))

    expect(screen.queryByRole('option', { name: /9º ano B/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Turmas encerradas (1)' }))

    const encerradas = screen.getByRole('listbox', { name: 'Turmas encerradas' })
    // Encerrada não tem próxima aula, mesmo com horário na grade.
    expect(within(encerradas).getByRole('option')).toHaveTextContent(/^9º ano B30 alunos$/)
  })

  it('busca pelo nome da turma, incluindo as encerradas', async () => {
    montar()
    await userEvent.click(screen.getByRole('button', { name: /Turma/ }))

    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar turma' }), '9º')

    expect(screen.getAllByRole('option')).toHaveLength(1)
    expect(screen.getByRole('option')).toHaveTextContent('9º ano B')

    await userEvent.clear(screen.getByRole('searchbox', { name: 'Buscar turma' }))
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar turma' }), 'xyz')
    expect(screen.getByText('Nenhuma turma encontrada.')).toBeInTheDocument()
  })

  it('escolhe pelo clique e pelo teclado', async () => {
    const onChange = montar()

    await userEvent.click(screen.getByRole('button', { name: /Turma/ }))
    await userEvent.click(screen.getByRole('option', { name: /1º ano A/ }))
    expect(onChange).toHaveBeenLastCalledWith('2-10')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Turma/ }))
    // Abre destacando a escolhida (6º ano A); duas para baixo, o 3º ano A.
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}')
    expect(onChange).toHaveBeenLastCalledWith('3-10')
  })

  it('aberta numa turma encerrada, já mostra as encerradas', async () => {
    montar(OPCOES[4])

    expect(screen.getByRole('button', { name: 'Turma: 9º ano B · Matemática (encerrada)' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Turma/ }))

    expect(screen.getByRole('option', { name: /9º ano B/ })).toHaveAttribute('aria-selected', 'true')
  })
})
