import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ModalLancarFatura } from './ModalLancarFatura'

describe('ModalLancarFatura', () => {
  afterEach(() => vi.useRealTimers())

  it('a competência é escolhida na lista de meses, que abre no mês atual', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-15T15:00:00Z'))
    const onLancar = vi.fn().mockResolvedValue(undefined)
    render(<ModalLancarFatura escolaNome="Colégio Ipê" onFechar={vi.fn()} onLancar={onLancar} />)

    await userEvent.click(screen.getByRole('combobox', { name: /competência/i }))
    // 12 meses para trás e 12 para a frente; sem escolha, abre destacando o atual.
    expect(screen.getAllByRole('option')).toHaveLength(25)
    expect(screen.getByRole('option', { name: 'Novembro de 2025' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Novembro de 2027' })).toBeInTheDocument()
    await userEvent.keyboard('{ArrowDown}{Enter}')
    expect(screen.getByRole('combobox', { name: /competência/i })).toHaveTextContent('Dezembro de 2026')

    await userEvent.type(screen.getByLabelText(/descrição/i), 'Mensalidade Edúka de dezembro')
    await userEvent.type(screen.getByLabelText(/valor/i), '299,90')
    await userEvent.click(screen.getByRole('button', { name: /vencimento/i }))
    await userEvent.click(screen.getByRole('button', { name: /20 de novembro de 2026/i }))
    await userEvent.click(screen.getByRole('button', { name: 'Lançar fatura' }))

    expect(onLancar).toHaveBeenCalledWith({
      competencia: '2026-12',
      descricao: 'Mensalidade Edúka de dezembro',
      valor: 299.9,
      dataVencimento: '2026-11-20',
    })
  })

  it('sem a competência, não lança e mostra o erro no campo', async () => {
    const onLancar = vi.fn()
    render(<ModalLancarFatura escolaNome="Colégio Ipê" onFechar={vi.fn()} onLancar={onLancar} />)

    await userEvent.click(screen.getByRole('button', { name: 'Lançar fatura' }))

    expect(screen.getByRole('combobox', { name: /competência/i })).toHaveAccessibleDescription(
      'Escolha o mês de referência.',
    )
    expect(onLancar).not.toHaveBeenCalled()
  })
})
