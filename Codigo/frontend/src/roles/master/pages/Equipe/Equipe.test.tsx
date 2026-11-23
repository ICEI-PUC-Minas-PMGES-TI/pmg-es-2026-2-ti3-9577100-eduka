import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { MembroEquipe } from '../../../../types/escola/equipe'
import { Equipe } from './Equipe'

const PATRICIA: MembroEquipe = {
  id: 'sec-1',
  perfil: 'SECRETARIA',
  nome: 'Patrícia Andrade',
  cpf: null,
  email: 'patricia@escola.com',
  telefone: null,
  login: 'patricia@escola.com',
  ativo: true,
  aguardandoPrimeiroAcesso: false,
}

const MARCOS: MembroEquipe = {
  id: 'fin-1',
  perfil: 'FINANCEIRO',
  nome: 'Marcos Teixeira',
  cpf: null,
  email: 'marcos@escola.com',
  telefone: null,
  login: 'marcos@escola.com',
  ativo: true,
  aguardandoPrimeiroAcesso: true,
}

function abrir(responder: (chamada: ChamadaApi) => RespostaApi | undefined) {
  const chamadas = simularApi(responder)
  render(
    <MemoryRouter>
      <Equipe />
    </MemoryRouter>,
  )
  return chamadas
}

describe('Equipe do master', () => {
  it('lista secretaria e financeiro e filtra pela função', async () => {
    abrir(() => ({ corpo: [MARCOS, PATRICIA] }))

    const tabela = await screen.findByRole('region', { name: 'Pessoas da equipe' })
    expect(within(tabela).getByText('Patrícia Andrade')).toBeInTheDocument()
    expect(within(tabela).getByText('Marcos Teixeira')).toBeInTheDocument()
    expect(within(tabela).getByText('Aguardando acesso')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Financeiro' }))
    expect(within(tabela).getByText('Marcos Teixeira')).toBeInTheDocument()
    expect(within(tabela).queryByText('Patrícia Andrade')).not.toBeInTheDocument()
  })

  it('cadastra alguém do financeiro e manda a função escolhida', async () => {
    const chamadas = abrir(({ metodo }) => {
      if (metodo === 'GET') return { corpo: [PATRICIA] }
      return { status: 201, corpo: { ...MARCOS, id: 'fin-novo', nome: 'Rita Souza', email: 'rita@escola.com' } }
    })
    await screen.findByText('Patrícia Andrade')

    await userEvent.click(screen.getByRole('button', { name: 'Adicionar à equipe' }))
    const modal = await screen.findByRole('dialog', { name: 'Adicionar à equipe' })
    await userEvent.click(within(modal).getByRole('radio', { name: /Financeiro/ }))
    await userEvent.type(within(modal).getByRole('textbox', { name: /Nome completo/ }), 'Rita Souza')
    await userEvent.type(within(modal).getByRole('textbox', { name: /CPF/ }), '52998224725')
    await userEvent.type(within(modal).getByRole('textbox', { name: /E-mail/ }), 'rita@escola.com')
    await userEvent.click(within(modal).getByRole('button', { name: 'Cadastrar e enviar acesso' }))

    expect(await screen.findByRole('status')).toHaveTextContent('O acesso foi enviado para rita@escola.com')
    const post = chamadas.find((c) => c.metodo === 'POST')
    expect(post?.caminho).toBe('/usuarios/equipe')
    expect(post?.corpo).toEqual({
      perfil: 'FINANCEIRO',
      nome: 'Rita Souza',
      cpf: '52998224725',
      email: 'rita@escola.com',
      telefone: null,
    })
    expect(screen.getByText('Rita Souza')).toBeInTheDocument()
  })

  it('e-mail já usado como login marca o campo do e-mail', async () => {
    abrir(({ metodo }) => {
      if (metodo === 'GET') return { corpo: [] }
      return problema(409, 'CONFLITO', 'Login ja cadastrado: rita@escola.com')
    })
    await screen.findByText(/Ninguém na equipe ainda/)

    await userEvent.click(screen.getByRole('button', { name: 'Adicionar à equipe' }))
    const modal = await screen.findByRole('dialog', { name: 'Adicionar à equipe' })
    await userEvent.type(within(modal).getByRole('textbox', { name: /Nome completo/ }), 'Rita Souza')
    await userEvent.type(within(modal).getByRole('textbox', { name: /CPF/ }), '52998224725')
    await userEvent.type(within(modal).getByRole('textbox', { name: /E-mail/ }), 'rita@escola.com')
    await userEvent.click(within(modal).getByRole('button', { name: 'Cadastrar e enviar acesso' }))

    expect(await within(modal).findByText('Este e-mail já é usado por outro usuário.')).toBeInTheDocument()
  })

  it('reenvia o acesso só de quem ainda não entrou e inativa pelo PATCH', async () => {
    const chamadas = abrir(({ metodo, caminho }) => {
      if (metodo === 'GET') return { corpo: [MARCOS, PATRICIA] }
      if (caminho === '/usuarios/equipe/fin-1/primeiro-acesso') return { status: 204 }
      if (caminho === '/usuarios/equipe/sec-1/inativar') return { corpo: { ...PATRICIA, ativo: false } }
      return undefined
    })
    const tabela = await screen.findByRole('region', { name: 'Pessoas da equipe' })

    // Patrícia já definiu a senha: não tem o botão.
    expect(within(tabela).getAllByRole('button', { name: 'Reenviar acesso' })).toHaveLength(1)
    await userEvent.click(within(tabela).getByRole('button', { name: 'Reenviar acesso' }))
    expect(await screen.findByRole('status')).toHaveTextContent('O acesso foi reenviado para marcos@escola.com.')

    const linhaPatricia = within(tabela).getByText('Patrícia Andrade').closest('tr') as HTMLElement
    await userEvent.click(within(linhaPatricia).getByRole('button', { name: 'Inativar' }))
    expect(await within(linhaPatricia).findByText('Inativo')).toBeInTheDocument()
    expect(chamadas.map((c) => `${c.metodo} ${c.caminho}`)).toContain('PATCH /usuarios/equipe/sec-1/inativar')
  })
})
