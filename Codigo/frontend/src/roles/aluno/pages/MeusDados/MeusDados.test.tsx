import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { Aluno } from '../../../../types/aluno'
import type { SolicitacaoCorrecao } from '../../../../types/solicitacaoCorrecao'
import { MeusDados } from './MeusDados'

const ALUNO: Aluno = {
  id: 'aluno-1',
  nome: 'Lucas Andrade',
  matricula: '2026-0412',
  genero: null,
  escolaId: 'escola-1',
  cpf: '52998224725',
  email: 'lucas@email.com',
  dataNascimento: '2009-03-14',
  endereco: null,
  responsaveis: [],
}

const PEDIDO: SolicitacaoCorrecao = {
  id: 'pedido-1',
  alunoId: 'aluno-1',
  alunoNome: null,
  alunoMatricula: null,
  mensagem: 'Meu telefone mudou',
  status: 'ABERTA',
  criadoEm: '2026-10-30T12:00:00Z',
  atendidaEm: null,
}

function abrir(aberta: RespostaApi, envio?: RespostaApi) {
  const chamadas = simularApi(({ metodo, caminho }) => {
    if (caminho === '/portal/aluno/me') return { corpo: ALUNO }
    if (caminho === '/portal/aluno/turmas') return { corpo: [] }
    if (caminho === '/portal/aluno/correcoes/aberta') return aberta
    if (metodo === 'POST' && caminho === '/portal/aluno/correcoes') return envio
    return undefined
  })
  render(
    <MemoryRouter>
      <MeusDados />
    </MemoryRouter>,
  )
  return chamadas
}

describe('MeusDados', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('envia o pedido de correção à secretaria e passa a mostrar que está aguardando', async () => {
    const chamadas = abrir({ status: 204 }, { status: 201, corpo: PEDIDO })

    await userEvent.click(await screen.findByRole('button', { name: /pedir correção de dados/i }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.type(within(dialogo).getByLabelText(/o que está errado/i), 'Meu telefone mudou')
    await userEvent.click(within(dialogo).getByRole('button', { name: /enviar pedido/i }))

    expect(await screen.findByText(/pedido de correção enviado em 30\/10\/2026/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pedir correção de dados/i })).not.toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'POST')?.corpo).toEqual({ mensagem: 'Meu telefone mudou' })
  })

  it('com um pedido aberto, não deixa pedir outro', async () => {
    abrir({ corpo: PEDIDO })

    expect(await screen.findByText(/aguardando a secretaria/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pedir correção de dados/i })).not.toBeInTheDocument()
  })

  it('sem texto não envia, e a recusa do backend aparece no modal', async () => {
    const chamadas = abrir({ status: 204 }, problema(409, 'SOLICITACAO_ABERTA'))

    await userEvent.click(await screen.findByRole('button', { name: /pedir correção de dados/i }))
    const dialogo = screen.getByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('button', { name: /enviar pedido/i }))
    expect(within(dialogo).getByRole('alert')).toHaveTextContent(/conte o que precisa/i)
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)

    await userEvent.type(within(dialogo).getByLabelText(/o que está errado/i), 'CPF errado')
    await userEvent.click(within(dialogo).getByRole('button', { name: /enviar pedido/i }))
    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(/já tem um pedido/i)
  })
})
