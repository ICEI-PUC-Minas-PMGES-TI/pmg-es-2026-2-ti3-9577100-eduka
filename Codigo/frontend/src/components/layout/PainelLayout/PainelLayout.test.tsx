import { act, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { EVENTO_MODULO_NAO_CONTRATADO } from '../../../services/api'
import { gravarSessao, tirarModulo } from '../../../services/acesso/modulosSessao'
import { obterToken } from '../../../services/acesso/authService'
import { simularApi } from '../../../test/api'
import { logarComo } from '../../../test/sessao'
import type { ModuloEscola } from '../../../types/escola/escola'
import { PainelLayout, type ItemMenu } from './PainelLayout'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: '/painel' },
  { label: 'Cobranças', icon: 'card', to: '/painel/cobrancas', modulo: 'GESTAO_FINANCEIRA' },
]

function abrir() {
  // O MASTER vê o aviso de suspensão prevista: sem suspensão, 204.
  simularApi(() => ({ status: 204 }))
  const router = createMemoryRouter(
    [
      {
        path: '/painel',
        element: (
          <PainelLayout contexto="Teste" titulo="Painel" menu={MENU} menuRotulo="Navegação de teste">
            <p>conteúdo</p>
          </PainelLayout>
        ),
      },
      { path: '/login', element: <p>tela de login</p> },
    ],
    { initialEntries: ['/painel'] },
  )
  render(<RouterProvider router={router} />)
  return router
}

function perderModulo(modulo: ModuloEscola) {
  act(() => {
    tirarModulo(modulo)
    window.dispatchEvent(new CustomEvent(EVENTO_MODULO_NAO_CONTRATADO, { detail: modulo }))
  })
}

describe('PainelLayout e os módulos da escola', () => {
  afterEach(() => localStorage.clear())

  it('item de módulo não contratado não aparece no menu', () => {
    logarComo('MASTER')
    gravarSessao('gestor@escola.com', ['PORTAL_ALUNO'])
    abrir()

    expect(screen.getByRole('link', { name: 'Início' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Cobranças' })).not.toBeInTheDocument()
  })

  it('sem a informação dos módulos, mostra tudo', () => {
    logarComo('MASTER')
    abrir()

    expect(screen.getByRole('link', { name: 'Cobranças' })).toBeInTheDocument()
  })

  it('módulo tirado com a tela aberta some do menu, sem derrubar o MASTER', () => {
    logarComo('MASTER')
    gravarSessao('gestor@escola.com', ['GESTAO_FINANCEIRA'])
    const router = abrir()
    expect(screen.getByRole('link', { name: 'Cobranças' })).toBeInTheDocument()

    perderModulo('GESTAO_FINANCEIRA')

    expect(screen.queryByRole('link', { name: 'Cobranças' })).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/painel')
    expect(obterToken()).not.toBeNull()
  })

  it('o FINANCEIRO que perde a gestão financeira sai e o login explica', async () => {
    logarComo('FINANCEIRO')
    gravarSessao('fernanda@escola.com', ['GESTAO_FINANCEIRA'])
    const router = abrir()

    perderModulo('GESTAO_FINANCEIRA')

    expect(await screen.findByText('tela de login')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?modulo=GESTAO_FINANCEIRA')
    expect(obterToken()).toBeNull()
  })
})
