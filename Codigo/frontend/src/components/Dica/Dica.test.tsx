import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { Dica } from './Dica'

/** O balão é aria-hidden: o leitor de tela ouve o texto pela descrição do botão. */
const balao = () => screen.queryByRole('tooltip', { hidden: true })

describe('Dica', () => {
  it('aparece no mouse depois de um instante e some ao sair', async () => {
    render(
      <Dica texto="Envia uma nova senha">
        <button type="button">Reenviar acesso</button>
      </Dica>,
    )
    const botao = screen.getByRole('button', { name: 'Reenviar acesso' })

    await userEvent.hover(botao)
    expect(balao()).not.toBeInTheDocument()
    expect(await screen.findByRole('tooltip', { hidden: true })).toHaveTextContent('Envia uma nova senha')

    await userEvent.unhover(botao)
    expect(balao()).not.toBeInTheDocument()
  })

  it('pelo teclado abre na hora e fecha com Esc', async () => {
    render(
      <Dica texto="Ajuda e orientações">
        <button type="button">Ajuda</button>
      </Dica>,
    )

    await userEvent.tab()
    expect(await screen.findByRole('tooltip', { hidden: true })).toHaveTextContent('Ajuda e orientações')

    await userEvent.keyboard('{Escape}')
    expect(balao()).not.toBeInTheDocument()
  })

  it('liga o texto ao botão como descrição, sem repetir o que o nome já diz', () => {
    render(
      <>
        <Dica texto="Excluir (saiu da escola)">
          <button type="button" aria-label="Excluir Ana">
            x
          </button>
        </Dica>
        <Dica texto="Editar" descrever={false}>
          <button type="button" aria-label="Editar Prova 1">
            e
          </button>
        </Dica>
        <Dica texto="Ajuda">
          <button type="button" aria-label="Ajuda">
            ?
          </button>
        </Dica>
      </>,
    )

    expect(screen.getByRole('button', { name: 'Excluir Ana' })).toHaveAccessibleDescription('Excluir (saiu da escola)')
    expect(screen.getByRole('button', { name: 'Editar Prova 1' })).not.toHaveAttribute('aria-describedby')
    expect(screen.getByRole('button', { name: 'Ajuda' })).not.toHaveAttribute('aria-describedby')
  })

  it('funciona em botão desabilitado: o invólucro recebe o mouse no lugar dele', async () => {
    render(
      <Dica texto="Em breve">
        <button type="button" disabled>
          Relatórios
        </button>
      </Dica>,
    )
    const botao = screen.getByRole('button', { name: 'Relatórios' })

    await userEvent.hover(botao.parentElement as HTMLElement)
    expect(await screen.findByRole('tooltip', { hidden: true })).toHaveTextContent('Em breve')
    expect(botao).toHaveAccessibleDescription('Em breve')
  })

  it('a dica que aparece e some não remonta o botão nem tira o foco dele', async () => {
    function Vagas() {
      const [cheia, setCheia] = useState(false)
      return (
        <Dica texto={cheia ? 'Todas as vagas já foram escolhidas' : null}>
          <button type="button" onClick={() => setCheia((atual) => !atual)}>
            Matricular
          </button>
        </Dica>
      )
    }
    render(<Vagas />)
    const antes = screen.getByRole('button', { name: 'Matricular' })
    expect(antes).not.toHaveAttribute('aria-describedby')

    await userEvent.click(antes)

    const depois = screen.getByRole('button', { name: 'Matricular' })
    expect(depois).toBe(antes)
    expect(depois).toHaveFocus()
    expect(depois).toHaveAccessibleDescription('Todas as vagas já foram escolhidas')
  })

  it('sem texto não acende nada', async () => {
    render(
      <Dica texto={null}>
        <button type="button">Salvar</button>
      </Dica>,
    )

    await userEvent.hover(screen.getByRole('button', { name: 'Salvar' }))
    await new Promise((resolve) => setTimeout(resolve, 450))
    expect(balao()).not.toBeInTheDocument()
  })
})
