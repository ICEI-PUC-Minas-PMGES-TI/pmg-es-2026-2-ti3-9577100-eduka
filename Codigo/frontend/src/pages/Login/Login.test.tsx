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

describe('Login: recuperar senha', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('pede o código, redefine a senha e volta ao login com o aviso', async () => {
    const chamadas = simularApi(({ caminho }) => {
      if (caminho === '/senha/recuperacao') {
        return { status: 202, corpo: { validadeSegundos: 900, reenvioSegundos: 60 } }
      }
      if (caminho === '/senha/redefinicao') return { status: 204 }
      return undefined
    })
    abrir()

    await userEvent.type(screen.getByLabelText(/e-mail ou matrícula/i), 'maria@escola.com')
    await userEvent.click(screen.getByRole('button', { name: /esqueceu sua senha/i }))
    // O login já digitado vem preenchido.
    expect(screen.getByLabelText(/e-mail ou matrícula/i)).toHaveValue('maria@escola.com')
    await userEvent.click(screen.getByRole('button', { name: /enviar código/i }))

    expect(await screen.findByRole('status')).toHaveTextContent(/se houver uma conta/i)
    expect(screen.getByRole('button', { name: /reenviar código em/i })).toBeDisabled()

    await userEvent.type(screen.getByLabelText(/código de verificação/i), '12a3456')
    await userEvent.type(screen.getByLabelText(/^nova senha/i), 'senha-nova-123')
    await userEvent.type(screen.getByLabelText(/confirme a nova senha/i), 'senha-nova-123')
    await userEvent.click(screen.getByRole('button', { name: /salvar nova senha/i }))

    expect(await screen.findByRole('status')).toHaveTextContent(/senha redefinida/i)
    expect(screen.getByLabelText(/e-mail ou matrícula/i)).toHaveValue('maria@escola.com')
    expect(chamadas.map((c) => c.corpo)).toEqual([
      { login: 'maria@escola.com' },
      { login: 'maria@escola.com', codigo: '123456', novaSenha: 'senha-nova-123' },
    ])
  })

  it('mostra o erro de código inválido sem sair da etapa', async () => {
    simularApi(({ caminho }) => {
      if (caminho === '/senha/recuperacao') {
        return { status: 202, corpo: { validadeSegundos: 900, reenvioSegundos: 60 } }
      }
      if (caminho === '/senha/redefinicao') return problema(400, 'CODIGO_RECUPERACAO_INVALIDO')
      return undefined
    })
    abrir()

    await userEvent.click(screen.getByRole('button', { name: /esqueceu sua senha/i }))
    await userEvent.type(screen.getByLabelText(/e-mail ou matrícula/i), 'maria@escola.com')
    await userEvent.click(screen.getByRole('button', { name: /enviar código/i }))
    await userEvent.type(await screen.findByLabelText(/código de verificação/i), '000000')
    await userEvent.type(screen.getByLabelText(/^nova senha/i), 'senha-nova-123')
    await userEvent.type(screen.getByLabelText(/confirme a nova senha/i), 'senha-nova-123')
    await userEvent.click(screen.getByRole('button', { name: /salvar nova senha/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/código inválido ou expirado/i)
    expect(screen.getByRole('heading', { name: /nova senha/i })).toBeInTheDocument()
  })
})
