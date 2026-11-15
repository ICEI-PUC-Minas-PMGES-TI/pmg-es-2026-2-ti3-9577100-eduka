import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MenuBotao } from './MenuBotao'

function montar(disabled = false) {
  const csv = vi.fn()
  const planilha = vi.fn()
  const resultado = render(
    <>
      <MenuBotao
        disabled={disabled}
        itens={[
          { id: 'csv', rotulo: 'CSV', descricao: 'Arquivo simples', onSelecionar: csv },
          { id: 'planilha', rotulo: 'Planilha', onSelecionar: planilha },
        ]}
      >
        Exportar
      </MenuBotao>
      <p>Fora</p>
    </>,
  )
  return { csv, planilha, ...resultado }
}

describe('MenuBotao', () => {
  it('abre com o clique, destaca o primeiro item e escolhe pelo clique', async () => {
    const { csv, planilha } = montar()
    const usuario = userEvent.setup()

    await usuario.click(screen.getByRole('button', { name: 'Exportar' }))
    const menu = screen.getByRole('menu', { name: 'Exportar' })
    expect(menu).toHaveFocus()
    expect(menu).toHaveAttribute('aria-activedescendant', screen.getAllByRole('menuitem')[0].id)

    await usuario.click(screen.getByRole('menuitem', { name: /^Planilha/ }))
    expect(planilha).toHaveBeenCalledOnce()
    expect(csv).not.toHaveBeenCalled()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('setas dão a volta, End vai ao último e Esc fecha devolvendo o foco', async () => {
    const { csv } = montar()
    const usuario = userEvent.setup()
    const botao = screen.getByRole('button', { name: 'Exportar' })

    botao.focus()
    await usuario.keyboard('{ArrowUp}')
    const itens = screen.getAllByRole('menuitem')
    expect(screen.getByRole('menu')).toHaveAttribute('aria-activedescendant', itens[1].id)
    await usuario.keyboard('{ArrowDown}')
    expect(screen.getByRole('menu')).toHaveAttribute('aria-activedescendant', itens[0].id)
    await usuario.keyboard('{End}')
    expect(screen.getByRole('menu')).toHaveAttribute('aria-activedescendant', itens[1].id)

    await usuario.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(botao).toHaveFocus()
    expect(botao).toHaveAttribute('aria-expanded', 'false')
    expect(csv).not.toHaveBeenCalled()
  })

  it('fecha ao clicar fora sem escolher nada', async () => {
    const { csv, planilha } = montar()
    const usuario = userEvent.setup()

    await usuario.click(screen.getByRole('button', { name: 'Exportar' }))
    await usuario.click(screen.getByText('Fora'))

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(csv).not.toHaveBeenCalled()
    expect(planilha).not.toHaveBeenCalled()
  })

  it('desabilitado, não abre', async () => {
    montar(true)
    const usuario = userEvent.setup()

    await usuario.click(screen.getByRole('button', { name: 'Exportar' }))

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})
