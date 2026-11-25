import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../../test/api'
import type { Alocacao } from '../../../../../types/academico/alocacao'
import type { Professor } from '../../../../../types/pessoas/professor'
import { AlocacoesProfessorModal, type VisaoAlocacoes } from './AlocacoesProfessorModal'

const PROFESSORA: Professor = {
  id: 'prof-1',
  escolaId: 'escola-1',
  nome: 'Paula Reis',
  cpf: null,
  email: 'paula@escola.com',
  telefone: null,
  formacao: null,
  dataAdmissao: null,
  vinculo: 'CLT_40H',
  ativo: true,
  aguardandoPrimeiroAcesso: false,
}

function alocacao(
  id: string,
  turmaId: number,
  turmaNome: string,
  periodo: string,
  disciplinaId: number,
  disciplinaNome: string,
): Alocacao {
  return {
    id,
    professorId: 'prof-1',
    professorNome: 'Paula Reis',
    turmaId,
    turmaNome,
    turno: 'MATUTINO',
    periodoLetivoId: periodo === '2026/2' ? 2 : 1,
    periodoLetivoNome: periodo,
    disciplinaId,
    disciplinaNome,
  }
}

// Matemática em duas turmas; Física só no 2º ano. O 1º ano é de um período anterior.
const ALOCACOES = [
  alocacao('a1', 1, '1º ano A', '2026/1', 10, 'Matemática'),
  alocacao('a2', 2, '2º ano A', '2026/2', 10, 'Matemática'),
  alocacao('a3', 2, '2º ano A', '2026/2', 11, 'Física'),
]

function abrir(visao: VisaoAlocacoes, alocacoes: Alocacao[] = ALOCACOES) {
  const onFechar = vi.fn()
  simularApi(({ caminho }) => (caminho === '/professores/prof-1/alocacoes' ? { corpo: alocacoes } : undefined))
  const router = createMemoryRouter(
    [
      {
        path: '/secretaria/professores',
        element: <AlocacoesProfessorModal professor={PROFESSORA} visao={visao} onFechar={onFechar} />,
      },
      { path: '/secretaria/academico/alocacoes', element: <p>Tela de alocações</p> },
    ],
    { initialEntries: ['/secretaria/professores'] },
  )
  render(<RouterProvider router={router} />)
  return onFechar
}

describe('AlocacoesProfessorModal', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('por disciplina: cada uma com as turmas onde o professor leciona, sem repetir', async () => {
    abrir('disciplinas')

    expect(screen.getByRole('dialog', { name: 'Disciplinas de Paula Reis' })).toBeInTheDocument()
    const lista = await screen.findByRole('list', { name: 'Disciplinas do professor' })
    expect(screen.getByText('2 disciplinas em 2 turmas')).toBeInTheDocument()

    const disciplinas = within(lista).getAllByRole('listitem').filter((li) => li.parentElement === lista)
    expect(disciplinas).toHaveLength(2)
    // Em ordem alfabética: Física antes de Matemática.
    expect(disciplinas[0]).toHaveTextContent(/física.*em 1 turma/i)
    const turmasDeMatematica = within(screen.getByRole('list', { name: 'Turmas de Matemática' })).getAllByRole('listitem')
    // Período mais recente primeiro.
    expect(turmasDeMatematica.map((t) => t.textContent)).toEqual(['2º ano A · 2026/2', '1º ano A · 2026/1'])
  })

  it('por turma: cada uma com o período, o turno e as disciplinas do professor nela', async () => {
    abrir('turmas')

    expect(screen.getByRole('dialog', { name: 'Turmas de Paula Reis' })).toBeInTheDocument()
    // Agrupadas por período, o mais recente primeiro.
    const periodos = await screen.findAllByRole('region', { name: /^Período / })
    expect(periodos.map((p) => p.getAttribute('aria-label'))).toEqual(['Período 2026/2', 'Período 2026/1'])
    expect(within(periodos[0]).getByText('2º ano A')).toBeInTheDocument()
    expect(within(periodos[0]).getByText('Matutino')).toBeInTheDocument()
    expect(within(periodos[1]).getByText('1º ano A')).toBeInTheDocument()
    expect(
      within(screen.getByRole('list', { name: 'Disciplinas em 2º ano A' }))
        .getAllByRole('listitem')
        .map((d) => d.textContent),
    ).toEqual(['Física', 'Matemática'])
  })

  it('sem alocação, explica onde alocar', async () => {
    abrir('turmas', [])

    expect(await screen.findByText(/ainda não está alocado em nenhuma turma/i)).toBeInTheDocument()
    expect(screen.getByText('0 disciplinas em 0 turmas')).toBeInTheDocument()
  })

  it('"Gerenciar alocações" fecha o modal e leva à tela de alocações', async () => {
    const onFechar = abrir('disciplinas')

    await screen.findByRole('list', { name: 'Disciplinas do professor' })
    await userEvent.click(screen.getByRole('button', { name: 'Gerenciar alocações' }))
    expect(onFechar).toHaveBeenCalled()
    expect(await screen.findByText('Tela de alocações')).toBeInTheDocument()
  })
})
