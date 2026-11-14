import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { CampoData } from '../CampoData/CampoData'
import { CampoPeriodo, type Periodo } from './CampoPeriodo'

/** Escolhe uma opção no seletor de mês ou de ano do calendário aberto. */
async function escolherNoSeletor(calendario: HTMLElement, rotulo: RegExp, opcao: string) {
  await userEvent.click(within(calendario).getByRole('combobox', { name: rotulo }))
  await userEvent.click(screen.getByRole('option', { name: opcao }))
}

// O mês e o ano são Selects dentro do calendário: a lista deles abre dentro do
// popover do calendário, e escolher nela não conta como clicar fora.
describe('seletores de mês e ano dentro do calendário', () => {
  it('no CampoData, trocar o mês e o ano não fecha o calendário', async () => {
    function Campo() {
      const [valor, setValor] = useState('2026-10-15')
      return <CampoData aria-label="Data" valor={valor} onChange={setValor} />
    }
    render(<Campo />)

    await userEvent.click(screen.getByRole('button', { name: /15\/10\/2026/ }))
    const calendario = screen.getByRole('dialog', { name: 'Escolher data' })
    await escolherNoSeletor(calendario, /mês/i, 'Março')
    await escolherNoSeletor(calendario, /ano/i, '2024')

    expect(screen.getByRole('dialog', { name: 'Escolher data' })).toBeInTheDocument()
    expect(within(calendario).getByRole('grid', { name: /março 2024/i })).toBeInTheDocument()
  })

  it('no CampoPeriodo, troca o mês, escolhe as pontas e aplica', async () => {
    const aoMudar = vi.fn()
    function Campo() {
      const [valor, setValor] = useState<Periodo | null>(null)
      return (
        <CampoPeriodo
          valor={valor}
          max="2026-10-30"
          placeholder="Personalizado"
          onChange={(periodo) => {
            aoMudar(periodo)
            setValor(periodo)
          }}
        />
      )
    }
    render(<Campo />)

    await userEvent.click(screen.getByRole('button', { name: /personalizado/i }))
    const calendario = screen.getByRole('dialog', { name: 'Escolher período' })
    await escolherNoSeletor(calendario, /mês/i, 'Agosto')
    expect(screen.getByRole('dialog', { name: 'Escolher período' })).toBeInTheDocument()

    await userEvent.click(within(calendario).getByRole('button', { name: /(?<!\d)15 de agosto de 2026/i }))
    await escolherNoSeletor(calendario, /mês/i, 'Outubro')
    await userEvent.click(within(calendario).getByRole('button', { name: /20 de outubro de 2026/i }))
    await userEvent.click(within(calendario).getByRole('button', { name: 'Aplicar' }))

    expect(aoMudar).toHaveBeenCalledWith({ de: '2026-08-15', ate: '2026-10-20' })
    expect(screen.queryByRole('dialog', { name: 'Escolher período' })).not.toBeInTheDocument()
  })
})
