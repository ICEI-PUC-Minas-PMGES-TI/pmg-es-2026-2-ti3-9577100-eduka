import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi } from '../../test/api'
import { AcessoSuspenso } from '../AcessoSuspenso/AcessoSuspenso'
import { LoginDev } from './LoginDev'

function abrir() {
  const router = createMemoryRouter(
    [
      { path: '/login/dev', element: <LoginDev /> },
      { path: '/acesso-suspenso', element: <AcessoSuspenso /> },
    ],
    { initialEntries: ['/login/dev'] },
  )
  render(<RouterProvider router={router} />)
}

describe('LoginDev', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('conta de escola suspensa vai para o aviso de suspensão, não para um erro genérico', async () => {
    simularApi(({ caminho }) => (caminho === '/login' ? problema(403, 'ESCOLA_SUSPENSA') : undefined))
    abrir()

    await userEvent.click(screen.getAllByRole('button', { name: /financeiro/i })[0])

    expect(await screen.findByRole('heading', { name: /acesso temporariamente indisponível/i })).toBeInTheDocument()
    expect(screen.queryByText('Você não tem permissão para acessar este recurso.')).not.toBeInTheDocument()
  })
})
