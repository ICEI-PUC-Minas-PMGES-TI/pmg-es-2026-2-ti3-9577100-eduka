import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { problema, simularApi } from '../../test/api'
import type { GrupoDemo } from '../../services/demoService'
import { LoginDemo } from './LoginDemo'

const GRUPOS: GrupoDemo[] = [
  {
    titulo: 'Colégio Ipê Amarelo',
    descricao: 'Escola de demonstração.',
    contas: [{ perfil: 'MASTER', rotulo: 'Master · Helena Duarte', login: 'helena.duarte@mock.eduka.com.br' }],
  },
]

function abrir() {
  const router = createMemoryRouter(
    [
      { path: '/login/demo', element: <LoginDemo /> },
      { path: '/master', element: <p>Painel do master</p> },
    ],
    { initialEntries: ['/login/demo'] },
  )
  render(<RouterProvider router={router} />)
}

async function preencherAcesso(senha = 'senha-do-acesso') {
  await userEvent.type(screen.getByLabelText(/e-mail/i), 'edukasystem@gmail.com')
  await userEvent.type(screen.getByLabelText(/senha/i), senha)
  await userEvent.click(screen.getByRole('button', { name: /ver contas de teste/i }))
}

describe('LoginDemo', () => {
  it('libera as contas com o acesso certo e entra na escolhida', async () => {
    const chamadas = simularApi(({ caminho }) => {
      if (caminho === '/demo/contas') return { corpo: GRUPOS }
      if (caminho === '/demo/entrar') {
        return {
          corpo: {
            accessToken: 'access',
            refreshToken: 'refresh',
            usuarioId: 'u1',
            perfil: 'MASTER',
            trocaSenhaObrigatoria: false,
            verificacao: null,
          },
        }
      }
      return undefined
    })
    abrir()

    await preencherAcesso()
    await userEvent.click(await screen.findByRole('button', { name: /helena duarte/i }))

    expect(await screen.findByText('Painel do master')).toBeInTheDocument()
    expect(chamadas[1].corpo).toEqual({
      login: 'edukasystem@gmail.com',
      senha: 'senha-do-acesso',
      conta: 'helena.duarte@mock.eduka.com.br',
    })
  })

  it('acesso errado mostra o erro e não lista contas', async () => {
    simularApi(() => problema(401, 'CREDENCIAIS_INVALIDAS'))
    abrir()

    await preencherAcesso('errada')

    expect(await screen.findByRole('alert')).toHaveTextContent(/inválidos/i)
    expect(screen.queryByRole('button', { name: /helena duarte/i })).not.toBeInTheDocument()
  })

  it('backend sem o perfil demo explica que o acesso está desligado', async () => {
    simularApi(() => ({ status: 404 }))
    abrir()

    await preencherAcesso()

    expect(await screen.findByRole('alert')).toHaveTextContent(/desligado/i)
  })
})
