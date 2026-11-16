import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { AvaliacaoDoAluno } from '../../../../types/portal/portalAluno'
import { hojeIso } from '../../../../utils/formatters'
import { AgendaAvaliacoes } from './AgendaAvaliacoes'

function diasDepois(dias: number): string {
  const [ano, mes, dia] = hojeIso().split('-').map(Number)
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10)
}

function avaliacao(id: string, titulo: string, data: string, bimestre: number | null, nota: number | null = null): AvaliacaoDoAluno {
  return {
    id,
    turmaId: 1,
    turmaNome: '1A',
    disciplinaId: 1,
    disciplinaNome: 'Matemática',
    tipo: 'PROVA',
    titulo,
    descricao: null,
    data,
    valor: 10,
    nota,
    observacao: null,
    bimestre,
  }
}

const AVALIACOES = [
  avaliacao('p1', 'Prova do 3º', diasDepois(-40), 3, 8),
  avaliacao('t1', 'Tarefa sem nota', diasDepois(-2), 4),
  avaliacao('p2', 'Prova do 4º', diasDepois(10), 4),
]

function abrir(url = '/') {
  simularApi(({ caminho }) => (caminho === '/portal/aluno/avaliacoes' ? { corpo: AVALIACOES } : undefined))
  render(
    <RouterProvider router={createMemoryRouter([{ path: '/', element: <AgendaAvaliacoes /> }], { initialEntries: [url] })} />,
  )
}

describe('Tarefas e provas do aluno', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('filtra por bimestre, com quantas avaliações cada um tem', async () => {
    abrir()

    const filtro = await screen.findByRole('group', { name: 'Filtrar por bimestre' })
    expect(within(filtro).getByRole('button', { name: /Todos/ })).toHaveAttribute('aria-pressed', 'true')
    expect(within(filtro).getByRole('button', { name: '3º bimestre 1' })).toBeInTheDocument()
    expect(within(filtro).getByRole('button', { name: '4º bimestre 2' })).toBeInTheDocument()
    expect(screen.getByText('Prova do 3º')).toBeInTheDocument()

    await userEvent.click(within(filtro).getByRole('button', { name: /3º bimestre/ }))

    expect(screen.queryByText('Prova do 4º')).not.toBeInTheDocument()
    expect(screen.getByText('Prova do 3º')).toBeInTheDocument()
    expect(screen.getByText('Nada marcado daqui para frente no 3º bimestre.')).toBeInTheDocument()
  })

  it('abre no bimestre da URL e marca o que já passou sem nota, sem repetir o bimestre em cada linha', async () => {
    abrir('/?bimestre=4')

    const filtro = await screen.findByRole('group', { name: 'Filtrar por bimestre' })
    expect(within(filtro).getByRole('button', { name: /4º bimestre/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByText('Prova do 3º')).not.toBeInTheDocument()
    expect(screen.getByText('Prova do 4º')).toBeInTheDocument()
    expect(screen.getByText('sem nota')).toBeInTheDocument()
    expect(screen.queryByText(/· 4º bimestre/)).not.toBeInTheDocument()
  })

  it('ignora bimestre da URL que não tem avaliação', async () => {
    abrir('/?bimestre=1')

    const filtro = await screen.findByRole('group', { name: 'Filtrar por bimestre' })
    expect(within(filtro).getByRole('button', { name: /Todos/ })).toHaveAttribute('aria-pressed', 'true')
  })
})
