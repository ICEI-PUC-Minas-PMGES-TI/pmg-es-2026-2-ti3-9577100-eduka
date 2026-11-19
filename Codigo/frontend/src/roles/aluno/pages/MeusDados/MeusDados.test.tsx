import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { Aluno } from '../../../../types/pessoas/aluno'
import type { SolicitacaoCorrecao } from '../../../../types/pessoas/solicitacaoCorrecao'
import type { TurmaDoAluno } from '../../../../types/portal/portalAluno'
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

const TURMAS: TurmaDoAluno[] = [
  {
    matriculaId: 'mat-2026',
    situacaoMatricula: 'ATIVA',
    dataMatricula: '2026-02-01',
    turmaId: 2,
    turmaNome: '2º ano A',
    turmaCodigo: '2A-26',
    turno: 'MATUTINO',
    periodoLetivoNome: '2026',
    cursoNome: 'Ensino Médio',
    disciplinas: [
      { id: 10, nome: 'Matemática', codigo: 'MAT', cargaHoraria: 120, professorNome: 'Paula Reis' },
      { id: 11, nome: 'Química', codigo: null, cargaHoraria: 80, professorNome: null },
    ],
  },
  {
    matriculaId: 'mat-2025',
    situacaoMatricula: 'CONCLUIDA',
    dataMatricula: '2025-02-03',
    turmaId: 1,
    turmaNome: '1º ano A',
    turmaCodigo: null,
    turno: 'MATUTINO',
    periodoLetivoNome: '2025',
    cursoNome: 'Ensino Médio',
    disciplinas: [{ id: 20, nome: 'Biologia', codigo: 'BIO', cargaHoraria: 80, professorNome: 'Rui Lima' }],
  },
]

function abrir(aberta: RespostaApi, envio?: RespostaApi, turmas: RespostaApi = { corpo: [] }) {
  const chamadas = simularApi(({ metodo, caminho }) => {
    if (caminho === '/portal/aluno/me') return { corpo: ALUNO }
    if (caminho === '/portal/aluno/turmas') return turmas
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

  it('mostra todas as turmas, não só a atual, cada uma com as disciplinas e os professores', async () => {
    abrir({ status: 204 }, undefined, { corpo: TURMAS })

    // O resumo lateral continua com a turma atual e leva à lista completa.
    await userEvent.click(await screen.findByRole('button', { name: /ver as 2 turmas e disciplinas/i }))
    expect(screen.getByRole('tab', { name: 'Turmas' })).toHaveAttribute('aria-selected', 'true')

    const atual = screen.getByRole('article', { name: 'Turma 2º ano A' })
    expect(within(atual).getByText('Ativa')).toBeInTheDocument()
    expect(within(atual).getByText(/ensino médio · 2026 · matutino/i)).toBeInTheDocument()
    expect(within(atual).getByText(/matrícula em 01\/02\/2026/i)).toBeInTheDocument()
    const disciplinas = within(within(atual).getByRole('list', { name: /disciplinas de 2º ano a/i })).getAllByRole(
      'listitem',
    )
    expect(disciplinas).toHaveLength(2)
    expect(disciplinas[0]).toHaveTextContent(/matemática · mat.*paula reis · 120 h/i)
    expect(disciplinas[1]).toHaveTextContent(/química.*professor a definir · 80 h/i)

    const anterior = screen.getByRole('article', { name: 'Turma 1º ano A' })
    expect(within(anterior).getByText('Concluída')).toBeInTheDocument()
    expect(within(anterior).getByText(/biologia/i)).toBeInTheDocument()
    expect(anterior).toHaveTextContent(/rui lima · 80 h/i)
  })

  it('sem matrícula, a aba Turmas diz que ainda não há turma', async () => {
    abrir({ status: 204 })

    await userEvent.click(await screen.findByRole('tab', { name: 'Turmas' }))
    expect(screen.getByText(/ainda não está matriculado em nenhuma turma/i)).toBeInTheDocument()
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
