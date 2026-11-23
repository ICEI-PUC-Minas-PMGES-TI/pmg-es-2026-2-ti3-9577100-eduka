import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { esquecerAceitePrivacidade } from '../../routes/aceitePrivacidade'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { ACCESS_TOKEN_KEY } from '../../services/api'
import { USUARIO_KEY } from '../../services/acesso/authService'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../test/api'
import type { AvisoPrivacidade as Aviso } from '../../types/privacidade/privacidade'
import { AvisoPrivacidade } from './AvisoPrivacidade'

function aviso(versao: string, aceitoEm: string | null = null): Aviso {
  return {
    versao,
    publicadoEm: '2026-11-11',
    resumo: [
      { icone: 'user', titulo: 'Quais dados usamos', texto: 'Nome, CPF e notas.' },
      { icone: 'lock', titulo: 'Como protegemos', texto: 'Dados cifrados.' },
    ],
    secoes: [{ titulo: 'Seus direitos', paragrafos: [`Texto completo da versão ${versao}.`] }],
    aceitoEm,
  }
}

/** Token com o perfil nas claims, como o backend emite (a assinatura não é conferida no cliente). */
function entrarComo(perfil: string) {
  const claims = btoa(JSON.stringify({ sub: 'u1', perfil }))
  localStorage.setItem(ACCESS_TOKEN_KEY, `cabecalho.${claims}.assinatura`)
  localStorage.setItem(USUARIO_KEY, JSON.stringify({ login: 'ana@escola.com' }))
}

function abrir(url: string, responder: (chamada: ChamadaApi) => RespostaApi | undefined) {
  const chamadas = simularApi(responder)
  const router = createMemoryRouter(
    [
      { path: paths.login, element: <p>Tela de login</p> },
      { path: paths.avisoPrivacidade, element: <AvisoPrivacidade /> },
      {
        path: '/aluno',
        element: <RotaProtegida perfis={['ALUNO']} />,
        children: [{ path: 'boletim', element: <p>Painel do aluno</p> }],
      },
    ],
    { initialEntries: [url] },
  )
  render(<RouterProvider router={router} />)
  return { chamadas, router }
}

describe('Aviso de privacidade (IU29)', () => {
  beforeEach(() => {
    esquecerAceitePrivacidade()
    entrarComo('ALUNO')
  })

  afterEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('quem ainda não aceitou vai para o aviso e, ao aceitar, volta para a tela que ia abrir', async () => {
    let aceito: string | null = null
    const { chamadas } = abrir('/aluno/boletim', ({ metodo, caminho }) => {
      if (metodo === 'GET' && caminho === '/privacidade/aviso') return { corpo: aviso('1.0', aceito) }
      if (metodo === 'POST' && caminho === '/privacidade/aceite') {
        aceito = '2026-11-11T13:00:00Z'
        return { corpo: aviso('1.0', aceito) }
      }
      return undefined
    })

    expect(await screen.findByRole('heading', { name: 'Aviso de privacidade' })).toBeInTheDocument()
    expect(await screen.findByText('Quais dados usamos')).toBeInTheDocument()
    expect(screen.getByText(/Versão 1\.0, de 11\/11\/2026/)).toBeInTheDocument()
    expect(screen.queryByText('Painel do aluno')).not.toBeInTheDocument()

    // Sem confirmar a leitura, não dá para aceitar.
    const aceitar = screen.getByRole('button', { name: 'Aceitar e continuar' })
    expect(aceitar).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox', { name: /Li e entendi/ }))
    await userEvent.click(aceitar)

    expect(await screen.findByText('Painel do aluno')).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'POST')?.corpo).toEqual({ versao: '1.0' })
  })

  it('quem já aceitou a versão em vigor entra direto', async () => {
    const { chamadas } = abrir('/aluno/boletim', ({ caminho }) =>
      caminho === '/privacidade/aviso' ? { corpo: aviso('1.0', '2026-11-10T10:00:00Z') } : undefined,
    )

    expect(await screen.findByText('Painel do aluno')).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
  })

  it('Ler o aviso completo abre o texto inteiro', async () => {
    abrir(paths.avisoPrivacidade, ({ caminho }) =>
      caminho === '/privacidade/aviso' ? { corpo: aviso('1.0') } : undefined,
    )

    await userEvent.click(await screen.findByRole('button', { name: /Ler o aviso completo/ }))

    expect(await screen.findByText('Texto completo da versão 1.0.')).toBeInTheDocument()
  })

  it('Sair encerra a sessão sem aceitar', async () => {
    const { chamadas } = abrir(paths.avisoPrivacidade, ({ caminho }) =>
      caminho === '/privacidade/aviso' ? { corpo: aviso('1.0') } : undefined,
    )

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }))

    expect(await screen.findByText('Tela de login')).toBeInTheDocument()
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
  })

  it('se o aviso mudou enquanto lia, mostra a versão nova para ler de novo', async () => {
    let versao = '1.0'
    abrir(paths.avisoPrivacidade, ({ metodo, caminho }) => {
      if (metodo === 'GET' && caminho === '/privacidade/aviso') return { corpo: aviso(versao) }
      if (metodo === 'POST') {
        versao = '2.0'
        return problema(400, 'REQUISICAO_INVALIDA', 'Versao do aviso de privacidade desatualizada')
      }
      return undefined
    })

    await userEvent.click(await screen.findByRole('checkbox', { name: /Li e entendi/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Aceitar e continuar' }))

    expect(await screen.findByText(/foi atualizado/)).toBeInTheDocument()
    expect(await screen.findByText(/Versão 2\.0/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Li e entendi/ })).not.toBeChecked()
  })

  it('sessão vencida volta para o login', async () => {
    abrir('/aluno/boletim', ({ caminho }) => (caminho === '/privacidade/aviso' ? problema(401, 'NAO_AUTENTICADO') : undefined))

    expect(await screen.findByText('Tela de login')).toBeInTheDocument()
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
  })
})
