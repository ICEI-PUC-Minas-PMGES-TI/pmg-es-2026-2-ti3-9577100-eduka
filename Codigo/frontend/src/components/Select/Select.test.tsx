import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { Select } from './Select'

const OPCOES = [
  { valor: 'a', rotulo: 'Opção A' },
  { valor: 'b', rotulo: 'Opção B' },
]

function Campo() {
  const [valor, setValor] = useState('a')
  return <Select aria-label="Escolha" opcoes={OPCOES} valor={valor} onChange={setValor} />
}

// O destino do popover vale para todos os que usam o usePopoverFlutuante (Select, CampoData, CampoPeriodo...).
describe('Select: onde a lista abre', () => {
  it('fora de modal, sai do cartão de vidro para o body e já abre com o foco', async () => {
    render(
      <div data-testid="cartao" style={{ backdropFilter: 'blur(8px)' }}>
        <Campo />
      </div>,
    )

    await userEvent.click(screen.getByRole('combobox', { name: 'Escolha' }))

    const lista = screen.getByRole('listbox')
    expect(lista.parentElement).toBe(document.body)
    expect(screen.getByTestId('cartao')).not.toContainElement(lista)
    // O foco vai para a lista ao abrir: as setas do teclado andam pelas opções.
    expect(lista).toHaveFocus()
  })

  it('dentro de modal, fica no <dialog>: no body, ficaria atrás da camada do topo', async () => {
    render(
      <dialog open data-testid="modal">
        <div style={{ backdropFilter: 'blur(8px)' }}>
          <Campo />
        </div>
      </dialog>,
    )

    await userEvent.click(screen.getByRole('combobox', { name: 'Escolha' }))

    const lista = screen.getByRole('listbox')
    expect(lista.parentElement).toBe(screen.getByTestId('modal'))
    expect(lista).toHaveFocus()

    await userEvent.click(screen.getByRole('option', { name: 'Opção B' }))
    expect(screen.getByRole('combobox', { name: 'Escolha' })).toHaveTextContent('Opção B')
  })
})
