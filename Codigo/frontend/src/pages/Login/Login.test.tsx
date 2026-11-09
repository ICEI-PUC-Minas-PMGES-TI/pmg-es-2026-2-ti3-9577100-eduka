import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AcessoSuspenso } from '../AcessoSuspenso/AcessoSuspenso'
import { problema, simularApi } from '../../test/api'
import { Login } from './Login'

function abrir() {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <Login /> },
      { path: '/acesso-suspenso', element: <AcessoSuspenso /> },
    ],
    { initialEntries: ['/login'] },
  )
  render(<RouterProvider router={router} />)
}

describe('Login', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('suspensa por fatura em atraso, o aviso explica o motivo', async () => {
    simularApi(({ caminho }) =>
      caminho === '/login'
        ? { status: 403, corpo: { status: 403, codigo: 'ESCOLA_SUSPENSA', motivo: 'FATURA_EM_ATRASO' } }
        : undefined,
    )
    abrir()

    await userEvent.type(screen.getByLabelText(/e-mail ou matrícula/i), 'maria@escola.com')
    await userEvent.type(screen.getByLabelText(/^senha/i), 'senha-certa')
    await userEvent.click(screen.getByRole('button', { name: /acessar/i }))

    expect(await screen.findByText(/porque uma fatura do Edúka está em atraso/)).toBeInTheDocument()
  })

  it('leva ao aviso de suspensão quando a escola está suspensa', async () => {
    simularApi(({ caminho }) =>
      caminho === '/login' ? problema(403, 'ESCOLA_SUSPENSA', 'A assinatura da escola esta suspensa') : undefined,
    )
    abrir()

    await userEvent.type(screen.getByLabelText(/e-mail ou matrícula/i), 'maria@escola.com')
    await userEvent.type(screen.getByLabelText(/^senha/i), 'senha-certa')
    await userEvent.click(screen.getByRole('button', { name: /acessar/i }))

    expect(await screen.findByRole('heading', { name: /acesso temporariamente indisponível/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /voltar para o login/i })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: /suporte técnico/i })).toHaveAttribute(
      'href',
      expect.stringContaining('mailto:'),
    )
  })

  it('mostra o erro de credenciais sem sair do login', async () => {
    simularApi(({ caminho }) => (caminho === '/login' ? { status: 401, corpo: { status: 401 } } : undefined))
    abrir()

    await userEvent.type(screen.getByLabelText(/e-mail ou matrícula/i), 'maria@escola.com')
    await userEvent.type(screen.getByLabelText(/^senha/i), 'senha-errada')
    await userEvent.click(screen.getByRole('button', { name: /acessar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/e-mail ou senha incorretos/i)
  })
})
