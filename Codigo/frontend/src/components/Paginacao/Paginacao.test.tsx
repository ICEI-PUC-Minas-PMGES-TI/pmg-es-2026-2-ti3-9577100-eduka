import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { usePaginacao } from '../../hooks/usePaginacao'
import { Paginacao } from './Paginacao'

const NUMEROS = Array.from({ length: 23 }, (_, i) => i + 1)

/** Lista de números com um filtro de pares, para ver a volta à primeira página. */
function Lista({ itens = NUMEROS }: { itens?: number[] }) {
  const [soPares, setSoPares] = useState(false)
  const visiveis = soPares ? itens.filter((n) => n % 2 === 0) : itens
  const { paginacao, topo } = usePaginacao<number, HTMLUListElement>(visiveis, { reiniciarCom: String(soPares) })
  return (
    <>
      <button type="button" onClick={() => setSoPares((atual) => !atual)}>
        Só pares
      </button>
      <ul ref={topo}>
        {paginacao.itensDaPagina.map((n) => (
          <li key={n}>{n}</li>
        ))}
      </ul>
      <Paginacao paginacao={paginacao} rotulo="Páginas dos números" />
    </>
  )
}

const itensVisiveis = () => screen.getAllByRole('listitem').map((li) => Number(li.textContent))

describe('Paginacao', () => {
  it('mostra 10 por página e anda com Anterior e Próxima', async () => {
    render(<Lista />)

    expect(itensVisiveis()).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    expect(screen.getByText('1–10 de 23')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }))
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }))

    expect(itensVisiveis()).toEqual([21, 22, 23])
    expect(screen.getByText('Página 3 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled()
  })

  it('volta para a primeira página quando o filtro muda', async () => {
    render(<Lista />)
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }))

    await userEvent.click(screen.getByRole('button', { name: 'Só pares' }))

    expect(itensVisiveis()).toEqual([2, 4, 6, 8, 10, 12, 14, 16, 18, 20])
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
  })

  it('não passa da última página quando a lista encolhe', async () => {
    const { rerender } = render(<Lista />)
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }))
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }))

    rerender(<Lista itens={NUMEROS.slice(0, 15)} />)

    expect(itensVisiveis()).toEqual([11, 12, 13, 14, 15])
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument()
  })

  it('some quando tudo cabe numa página', () => {
    render(<Lista itens={[1, 2, 3]} />)

    expect(screen.queryByRole('navigation', { name: 'Páginas dos números' })).not.toBeInTheDocument()
  })
})
