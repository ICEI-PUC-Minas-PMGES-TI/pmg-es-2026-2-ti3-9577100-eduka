import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi } from '../../../../../test/api'
import type { HorarioTurma } from '../../../../../types/academico/horario'
import { ModalHorarioDisciplina } from './ModalHorarioDisciplina'

const SEGUNDA: HorarioTurma = {
  id: 'h1',
  alocacaoId: 'aloc-1',
  turmaId: 7,
  diaSemana: 'SEGUNDA',
  horaInicio: '07:30:00',
  horaFim: '08:20:00',
  disciplinaId: 3,
  disciplinaNome: 'Matemática',
  professorId: 'p1',
  professorNome: 'João Martins',
}

function renderizar(horarios: HorarioTurma[] = [SEGUNDA]) {
  const onSalvo = vi.fn()
  render(
    <ModalHorarioDisciplina
      turmaId={7}
      turmaNome="2º ano A"
      disciplinaNome="Matemática"
      alocacaoId="aloc-1"
      horarios={horarios}
      onSalvo={onSalvo}
      onFechar={vi.fn()}
    />,
  )
  return onSalvo
}

describe('ModalHorarioDisciplina', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('grava só o que mudou: cria a aula nova e não mexe na que ficou igual', async () => {
    const chamadas = simularApi(({ metodo, caminho }) =>
      metodo === 'POST' && caminho === '/turmas/7/horario' ? { status: 201, corpo: SEGUNDA } : undefined,
    )
    const onSalvo = renderizar()

    expect(screen.getByLabelText('Início da aula 1')).toHaveValue('07:30')
    await userEvent.click(screen.getByRole('button', { name: /adicionar aula/i }))
    await userEvent.click(screen.getByRole('combobox', { name: 'Dia da aula 2' }))
    await userEvent.click(screen.getByRole('option', { name: 'Quarta' }))
    await userEvent.click(screen.getByRole('button', { name: /salvar horário/i }))

    expect(chamadas).toHaveLength(1)
    expect(chamadas[0].corpo).toEqual({
      alocacaoId: 'aloc-1',
      diaSemana: 'QUARTA',
      horaInicio: '07:30',
      horaFim: '08:20',
    })
    expect(onSalvo).toHaveBeenCalled()
  })

  it('remove a aula tirada e altera a mexida', async () => {
    const chamadas = simularApi(({ metodo }) => {
      if (metodo === 'DELETE') return { status: 204 }
      if (metodo === 'PUT') return { corpo: SEGUNDA }
      return undefined
    })
    renderizar([SEGUNDA, { ...SEGUNDA, id: 'h2', diaSemana: 'SEXTA' }])

    await userEvent.click(screen.getByRole('button', { name: 'Remover aula 1' }))
    await userEvent.clear(screen.getByLabelText('Fim da aula 1'))
    await userEvent.type(screen.getByLabelText('Fim da aula 1'), '09:10')
    await userEvent.click(screen.getByRole('button', { name: /salvar horário/i }))

    expect(chamadas.map((c) => `${c.metodo} ${c.caminho}`)).toEqual([
      'DELETE /turmas/7/horario/h1',
      'PUT /turmas/7/horario/h2',
    ])
    expect(chamadas[1].corpo).toEqual({ diaSemana: 'SEXTA', horaInicio: '07:30', horaFim: '09:10' })
  })

  it('mostra o conflito com o horário do professor', async () => {
    simularApi(({ metodo }) => (metodo === 'POST' ? problema(409, 'HORARIO_PROFESSOR_CONFLITANTE') : undefined))
    renderizar([])

    await userEvent.click(screen.getByRole('button', { name: /adicionar aula/i }))
    await userEvent.click(screen.getByRole('button', { name: /salvar horário/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/professor já tem aula/i)
  })
})
