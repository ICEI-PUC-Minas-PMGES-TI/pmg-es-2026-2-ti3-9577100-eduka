import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi } from '../../../../test/api'
import { ModalHorarioDisciplina } from './ModalHorarioDisciplina'

function renderizar() {
  const onSalvo = vi.fn()
  render(
    <ModalHorarioDisciplina
      turmaId={7}
      turmaNome="2º ano A"
      disciplinaId={3}
      disciplinaNome="Matemática"
      horarios={[{ disciplinaId: 3, diaSemana: 1, horaInicio: '07:30:00' }]}
      onSalvo={onSalvo}
      onFechar={vi.fn()}
    />,
  )
  return onSalvo
}

describe('ModalHorarioDisciplina', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('adiciona uma aula e grava o horário da disciplina', async () => {
    const chamadas = simularApi(({ metodo, caminho }) =>
      metodo === 'PUT' && caminho === '/turmas/7/disciplinas/3/horarios'
        ? {
            corpo: [
              { disciplinaId: 3, diaSemana: 1, horaInicio: '07:30:00' },
              { disciplinaId: 3, diaSemana: 3, horaInicio: '07:30:00' },
            ],
          }
        : undefined,
    )
    const onSalvo = renderizar()

    expect(screen.getByLabelText('Hora da aula 1')).toHaveValue('07:30')
    await userEvent.click(screen.getByRole('button', { name: /adicionar aula/i }))
    await userEvent.selectOptions(screen.getByLabelText('Dia da aula 2'), '3')
    await userEvent.click(screen.getByRole('button', { name: /salvar horário/i }))

    expect(chamadas[0].corpo).toEqual({
      horarios: [
        { diaSemana: 1, horaInicio: '07:30' },
        { diaSemana: 3, horaInicio: '07:30' },
      ],
    })
    expect(onSalvo).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ diaSemana: 3 })]))
  })

  it('mostra o conflito com outra aula da turma', async () => {
    simularApi(({ metodo }) =>
      metodo === 'PUT' ? problema(400, 'X', 'A turma ja tem outra aula nesse dia e horario') : undefined,
    )
    renderizar()

    await userEvent.click(screen.getByRole('button', { name: /salvar horário/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/já tem outra aula/i)
  })
})
