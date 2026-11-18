import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import { logarComo } from '../../../../test/sessao'
import { LoginAdmin } from './LoginAdmin'

function abrir() {
  const router = createMemoryRouter(
    [
      { path: '/login/admin', element: <LoginAdmin /> },
      { path: '/super-admin/escolas', element: <h1>Painel das escolas</h1> },
    ],
    { initialEntries: ['/login/admin'] },
  )
  render(<RouterProvider router={router} />)
  return router
}

async function entrar(email: string, senha: string) {
  await userEvent.type(screen.getByLabelText(/e-mail administrativo/i), email)
  await userEvent.type(screen.getByLabelText(/^senha/i), senha)
  await userEvent.click(screen.getByRole('button', { name: /continuar/i }))
}

const SESSAO = {
  accessToken: 'cabecalho.eyJzdWIiOiJ1MSIsInBlcmZpbCI6IlNVUEVSX0FETUlOIn0.assinatura',
  refreshToken: null,
  usuarioId: 'u1',
  perfil: 'SUPER_ADMIN',
  trocaSenhaObrigatoria: false,
  verificacao: null,
}

describe('LoginAdmin', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('entra com e-mail e senha e abre o painel das escolas', async () => {
    const chamadas = simularApi(({ caminho }) => (caminho === '/login' ? { corpo: SESSAO } : undefined))
    const router = abrir()

    await entrar('  admin@eduka.com.br ', 'senha-forte')

    expect(await screen.findByRole('heading', { name: 'Painel das escolas' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/super-admin/escolas')
    // O e-mail vai sem os espaços das pontas.
    expect(chamadas[0].corpo).toEqual({ login: 'admin@eduka.com.br', senha: 'senha-forte' })
  })

  it('sem e-mail ou senha, avisa e não chama a API', async () => {
    const chamadas = simularApi(() => undefined)
    abrir()

    await userEvent.type(screen.getByLabelText(/e-mail administrativo/i), 'admin@eduka.com.br')
    await userEvent.click(screen.getByRole('button', { name: /continuar/i }))

    expect(screen.getByRole('alert')).toHaveTextContent('Informe o e-mail administrativo e a senha.')
    expect(chamadas).toHaveLength(0)
  })

  it('senha errada mostra o erro de credenciais e continua no login', async () => {
    simularApi(({ caminho }) => (caminho === '/login' ? { status: 401, corpo: { status: 401 } } : undefined))
    const router = abrir()

    await entrar('admin@eduka.com.br', 'senha-errada')

    expect(await screen.findByRole('alert')).toHaveTextContent(/credenciais inválidas/i)
    expect(router.state.location.pathname).toBe('/login/admin')
    expect(screen.getByRole('button', { name: /continuar/i })).toBeEnabled()
  })

  it('sem conexão com a API, diz que não conseguiu falar com o servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    abrir()

    await entrar('admin@eduka.com.br', 'senha-forte')

    expect(await screen.findByRole('alert')).toHaveTextContent(/não foi possível conectar ao servidor/i)
  })

  it('com verificação em duas etapas, pede o código e o Voltar limpa a senha', async () => {
    simularApi(({ caminho }) =>
      caminho === '/login'
        ? {
            corpo: {
              ...SESSAO,
              accessToken: null,
              verificacao: { id: 'v1', validadeSegundos: 600, reenvioSegundos: 60 },
            },
          }
        : undefined,
    )
    abrir()

    await entrar('admin@eduka.com.br', 'senha-forte')

    expect(await screen.findByRole('heading', { name: 'Código de autenticação' })).toBeInTheDocument()
    expect(screen.queryByLabelText(/^senha/i)).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /voltar para o login/i }))
    expect(screen.getByLabelText(/e-mail administrativo/i)).toHaveValue('admin@eduka.com.br')
    expect(screen.getByLabelText(/^senha/i)).toHaveValue('')
  })

  it('Mostrar exibe a senha digitada e Ocultar esconde de novo', async () => {
    simularApi(() => undefined)
    abrir()
    const senha = screen.getByLabelText(/^senha/i)

    await userEvent.type(senha, 'segredo')
    expect(senha).toHaveAttribute('type', 'password')
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar senha' }))
    expect(senha).toHaveAttribute('type', 'text')
    await userEvent.click(screen.getByRole('button', { name: 'Ocultar senha' }))
    expect(senha).toHaveAttribute('type', 'password')
  })

  it('Esqueci a senha orienta a procurar a infraestrutura', async () => {
    simularApi(() => undefined)
    abrir()

    await userEvent.click(screen.getByRole('button', { name: /esqueci a senha/i }))

    expect(screen.getByRole('status')).toHaveTextContent(/operador de infraestrutura/i)
  })

  it('quem já tem sessão de super admin vai direto para o painel', async () => {
    logarComo('SUPER_ADMIN')
    simularApi(() => undefined)
    const router = abrir()

    expect(await screen.findByRole('heading', { name: 'Painel das escolas' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/super-admin/escolas')
  })
})
