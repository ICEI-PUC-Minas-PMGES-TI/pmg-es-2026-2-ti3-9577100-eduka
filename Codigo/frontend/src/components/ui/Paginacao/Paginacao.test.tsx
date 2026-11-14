import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { usePaginacao } from '../../../hooks/usePaginacao'
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

  it('numera a primeira, a última e as vizinhas da atual, e vai direto para uma delas', async () => {
    render(<Lista itens={Array.from({ length: 95 }, (_, i) => i + 1)} />)

    const numeros = () =>
      screen.getAllByRole('button', { name: /^Página \d+$/ }).map((b) => b.textContent)
    expect(numeros()).toEqual(['1', '2', '10'])
    expect(screen.getByRole('button', { name: 'Página 1' })).toHaveAttribute('aria-current', 'page')

    await userEvent.click(screen.getByRole('button', { name: 'Página 10' }))
    expect(itensVisiveis()).toEqual([91, 92, 93, 94, 95])
    expect(numeros()).toEqual(['1', '9', '10'])

    await userEvent.click(screen.getByRole('button', { name: 'Página 9' }))
    expect(numeros()).toEqual(['1', '8', '9', '10'])
  })

  it('trocar a quantidade por página mantém na tela o primeiro item que estava nela', async () => {
    render(<Lista itens={Array.from({ length: 95 }, (_, i) => i + 1)} />)
    await userEvent.click(screen.getByRole('button', { name: 'Página 2' }))
    await userEvent.click(screen.getByRole('button', { name: 'Página 3' }))
    expect(itensVisiveis()[0]).toBe(21)

    await userEvent.click(screen.getByRole('combobox', { name: 'Itens por página' }))
    await userEvent.click(screen.getByRole('option', { name: '20' }))

    // O 21 abre a página 2 de 20 em 20.
    expect(itensVisiveis()).toEqual(Array.from({ length: 20 }, (_, i) => i + 21))
    expect(screen.getByText('21–40 de 95')).toBeInTheDocument()
  })

  it('com a página maior que a lista, fica só a escolha da quantidade para poder voltar', async () => {
    render(<Lista />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Itens por página' }))
    await userEvent.click(screen.getByRole('option', { name: '50' }))

    expect(itensVisiveis()).toHaveLength(23)
    expect(screen.queryByRole('button', { name: 'Próxima' })).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Itens por página' })).toBeInTheDocument()
  })

  it('some quando tudo cabe numa página', () => {
    render(<Lista itens={[1, 2, 3]} />)

    expect(screen.queryByRole('navigation', { name: 'Páginas dos números' })).not.toBeInTheDocument()
  })
})
