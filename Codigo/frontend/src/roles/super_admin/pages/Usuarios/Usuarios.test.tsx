import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { cortarPagina, parametros, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { UsuarioPlataforma } from '../../../../types/acesso/usuarioPlataforma'
import type { Escola } from '../../../../types/escola/escola'
import { Usuarios } from './Usuarios'

function usuario(dados: Partial<UsuarioPlataforma>): UsuarioPlataforma {
  return {
    id: crypto.randomUUID(),
    login: 'alguem@escola.com',
    perfil: 'MASTER',
    escolaId: 'e-1',
    escolaNome: 'Colégio Horizonte',
    ativo: true,
    aguardandoPrimeiroAcesso: false,
    ultimoAcessoEm: '2026-11-20T13:05:00Z',
    criadoEm: '2026-01-10T12:00:00Z',
    gerenciavel: true,
    ...dados,
  }
}

const GESTORA = usuario({ id: 'u-gestora', login: 'gestora@horizonte.com' })
const NOVO_GESTOR = usuario({
  id: 'u-novo',
  login: 'novo@sabia.com',
  escolaId: 'e-2',
  escolaNome: 'Escola Sabiá',
  aguardandoPrimeiroAcesso: true,
  ultimoAcessoEm: null,
})
const ALUNA = usuario({ id: 'u-aluna', login: 'aluna@horizonte.com', perfil: 'ALUNO', gerenciavel: false })
const ADMIN = usuario({
  id: 'u-admin',
  login: 'admin@eduka.com',
  perfil: 'SUPER_ADMIN',
  escolaId: null,
  escolaNome: null,
  gerenciavel: false,
})

const ESCOLAS = [
  { id: 'e-1', nome: 'Colégio Horizonte' },
  { id: 'e-2', nome: 'Escola Sabiá' },
] as Escola[]

function abrir(responder: (chamada: ChamadaApi) => RespostaApi | undefined = () => undefined) {
  let lista = [ADMIN, ALUNA, GESTORA, NOVO_GESTOR]
  const chamadas = simularApi((chamada) => {
    const proprio = responder(chamada)
    if (proprio) return proprio
    if (chamada.caminho === '/escolas') return { corpo: ESCOLAS }
    if (chamada.caminho.startsWith('/usuarios/plataforma?')) {
      const { escolaId, perfil, ativo, busca } = parametros(chamada.caminho)
      const filtrados = lista.filter(
        (u) =>
          (!escolaId || u.escolaId === escolaId) &&
          (!perfil || u.perfil === perfil) &&
          (!ativo || String(u.ativo) === ativo) &&
          (!busca || u.login.includes(busca.toLowerCase())),
      )
      return { corpo: cortarPagina(filtrados, chamada.caminho) }
    }
    const inativar = /^\/usuarios\/plataforma\/([^/]+)\/inativar$/.exec(chamada.caminho)
    if (inativar && chamada.metodo === 'PATCH') {
      lista = lista.map((u) => (u.id === inativar[1] ? { ...u, ativo: false } : u))
      return { corpo: lista.find((u) => u.id === inativar[1]) }
    }
    return undefined
  })
  render(
    <MemoryRouter>
      <Usuarios />
    </MemoryRouter>,
  )
  return chamadas
}

function linha(login: string): HTMLElement {
  const celula = screen.getByText(login)
  const tr = celula.closest('tr')
  if (!tr) throw new Error(`linha de ${login} não encontrada`)
  return tr
}

describe('Usuários do Super Admin (S5-14)', () => {
  afterEach(() => localStorage.clear())

  it('lista os logins com a escola, o perfil, o último acesso e a situação', async () => {
    abrir()

    expect(await screen.findByText('gestora@horizonte.com')).toBeInTheDocument()
    expect(within(linha('gestora@horizonte.com')).getByText('Colégio Horizonte')).toBeInTheDocument()
    expect(within(linha('gestora@horizonte.com')).getByText('20/11/2026 às 10:05')).toBeInTheDocument()
    expect(within(linha('novo@sabia.com')).getByText('Nunca entrou')).toBeInTheDocument()
    expect(within(linha('novo@sabia.com')).getByText('Aguardando acesso')).toBeInTheDocument()
    expect(within(linha('admin@eduka.com')).getByText('Plataforma')).toBeInTheDocument()
    expect(screen.getByText(/4 logins na plataforma/)).toBeInTheDocument()
  })

  it('só o gestor tem ações; os demais são geridos pela escola', async () => {
    abrir()

    await screen.findByText('aluna@horizonte.com')
    expect(within(linha('aluna@horizonte.com')).getByText('Gerido pela escola')).toBeInTheDocument()
    expect(within(linha('aluna@horizonte.com')).queryByRole('button')).not.toBeInTheDocument()
    expect(within(linha('gestora@horizonte.com')).getByRole('button', { name: 'Inativar' })).toBeInTheDocument()
    // Reenviar só para quem ainda não entrou.
    expect(within(linha('gestora@horizonte.com')).queryByRole('button', { name: /reenviar/i })).not.toBeInTheDocument()
    expect(within(linha('novo@sabia.com')).getByRole('button', { name: /reenviar acesso/i })).toBeInTheDocument()
  })

  it('filtra por escola, perfil e situação no backend, e busca pelo login', async () => {
    const chamadas = abrir()
    await screen.findByText('gestora@horizonte.com')

    await userEvent.click(screen.getByRole('combobox', { name: 'Filtrar por escola' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Escola Sabiá' }))
    await waitFor(() => expect(screen.queryByText('gestora@horizonte.com')).not.toBeInTheDocument())
    expect(screen.getByText('novo@sabia.com')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }))
    await userEvent.type(screen.getByRole('searchbox', { name: /buscar pelo login/i }), 'aluna')
    await waitFor(() => expect(screen.queryByText('novo@sabia.com')).not.toBeInTheDocument())
    expect(screen.getByText('aluna@horizonte.com')).toBeInTheDocument()

    const ultima = chamadas.filter((c) => c.caminho.startsWith('/usuarios/plataforma?')).at(-1)
    expect(parametros(ultima?.caminho ?? '')).toMatchObject({ busca: 'aluna', pagina: '0' })
  })

  it('inativar o gestor confirma e atualiza a lista', async () => {
    abrir()
    await screen.findByText('gestora@horizonte.com')

    await userEvent.click(within(linha('gestora@horizonte.com')).getByRole('button', { name: 'Inativar' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'O acesso de gestora@horizonte.com foi inativado e não entra mais no Eduka.',
    )
    expect(await within(linha('gestora@horizonte.com')).findByRole('button', { name: 'Reativar' })).toBeInTheDocument()
  })

  it('reativar com a escola no limite explica o limite do plano', async () => {
    const inativa = usuario({ id: 'u-inativa', login: 'inativa@horizonte.com', ativo: false })
    abrir((chamada) => {
      if (chamada.caminho.startsWith('/usuarios/plataforma?')) {
        return { corpo: cortarPagina([inativa], chamada.caminho) }
      }
      if (chamada.caminho === '/usuarios/plataforma/u-inativa/ativar') {
        return { status: 409, corpo: { status: 409, codigo: 'LIMITE_USUARIOS_ATINGIDO', limite: 50 } }
      }
      return undefined
    })
    await screen.findByText('inativa@horizonte.com')

    await userEvent.click(within(linha('inativa@horizonte.com')).getByRole('button', { name: 'Reativar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/limite de 50 usuários ativos do plano/)
  })
})
