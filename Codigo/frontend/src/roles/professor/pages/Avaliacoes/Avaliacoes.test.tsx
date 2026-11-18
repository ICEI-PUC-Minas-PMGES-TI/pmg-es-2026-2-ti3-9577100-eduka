import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { EstadoNovaAvaliacao } from '../../../../routes/paths'
import { simularApi } from '../../../../test/api'
import type { DisciplinaDoProfessor, TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import { Avaliacoes } from './Avaliacoes'

function disciplina(id: number, nome: string): DisciplinaDoProfessor {
  return { id, nome, codigo: null, cargaHoraria: 80, horarios: [] }
}

const BASE = {
  codigo: null,
  turno: 'MATUTINO',
  ativa: true,
  periodoLetivoId: 1,
  periodoLetivoNome: '2026/2',
  cursoId: 1,
  cursoNome: 'Ensino Médio',
} as const

const TURMAS: TurmaDoProfessor[] = [
  { ...BASE, id: 7, nome: '2º ano A', disciplinas: [disciplina(3, 'Matemática'), disciplina(4, 'Física')] },
  { ...BASE, id: 8, nome: '3º ano B', disciplinas: [disciplina(5, 'Química')] },
]

function abrir(entrada: { search?: string; state?: EstadoNovaAvaliacao } = {}) {
  const chamadas = simularApi(({ metodo, caminho, corpo }) => {
    if (caminho === '/portal/professor/turmas') return { corpo: TURMAS }
    if (metodo === 'POST' && caminho === '/portal/professor/avaliacoes') {
      const { turmaId, disciplinaId, ...dados } = corpo as { turmaId: number; disciplinaId: number }
      const turma = TURMAS.find((t) => t.id === turmaId)
      const nomeDisciplina = turma?.disciplinas.find((d) => d.id === disciplinaId)?.nome
      return {
        corpo: {
          ...dados,
          id: 'av-nova',
          turmaId,
          turmaNome: turma?.nome,
          disciplinaId,
          disciplinaNome: nomeDisciplina,
          notasLancadas: 0,
          notasPublicadas: false,
          criadoEm: '2026-11-04T10:00:00',
          atualizadoEm: null,
        },
      }
    }
    if (caminho.includes('/notas')) return { corpo: [] }
    if (caminho.startsWith('/portal/professor/avaliacoes')) return { corpo: [] }
    return undefined
  })
  const router = createMemoryRouter([{ path: '/professor/avaliacoes', element: <Avaliacoes /> }], {
    initialEntries: [{ pathname: '/professor/avaliacoes', search: entrada.search ?? '', state: entrada.state }],
  })
  render(<RouterProvider router={router} />)
  return { router, chamadas }
}

function campo(dialogo: HTMLElement, nome: string) {
  return within(dialogo).getByRole('combobox', { name: nome })
}

async function escolher(dialogo: HTMLElement, nome: string, opcao: string) {
  await userEvent.click(campo(dialogo, nome))
  await userEvent.click(await screen.findByRole('option', { name: opcao }))
}

async function preencherEEnviar(dialogo: HTMLElement) {
  await escolher(dialogo, 'Tipo', 'Prova')
  await userEvent.click(within(dialogo).getByLabelText(/Data/))
  const calendario = screen.getByRole('dialog', { name: 'Escolher data' })
  await userEvent.click(within(calendario).getAllByRole('button', { name: / de .+ de \d{4}/ })[15])
  await userEvent.type(within(dialogo).getByLabelText(/Título/), 'Prova 1')
  await userEvent.type(within(dialogo).getByLabelText(/Valor/), '10')
  await userEvent.click(within(dialogo).getByRole('button', { name: /salvar avaliação/i }))
}

describe('Avaliacoes', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('pelo atalho do Início, o modal abre pedindo a turma', async () => {
    abrir({ state: { abrirCadastro: true } })

    const dialogo = await screen.findByRole('dialog')
    expect(campo(dialogo, 'Turma')).toHaveTextContent('Selecione…')
    expect(within(dialogo).getByPlaceholderText('Escolha a turma primeiro')).toBeInTheDocument()

    await userEvent.click(within(dialogo).getByRole('button', { name: /salvar avaliação/i }))
    expect(within(dialogo).getByText('Escolha a turma.')).toBeInTheDocument()
  })

  it('pelo botão da tela, o modal vem na turma e disciplina da tela e deixa trocar', async () => {
    abrir({ search: '?turma=7&disciplina=4' })

    await userEvent.click((await screen.findAllByRole('button', { name: /nova avaliação/i }))[0])

    const dialogo = await screen.findByRole('dialog')
    expect(campo(dialogo, 'Turma')).toHaveTextContent('2º ano A')
    expect(campo(dialogo, 'Disciplina')).toHaveTextContent('Física')

    await escolher(dialogo, 'Turma', '3º ano B')
    expect(campo(dialogo, 'Disciplina')).toHaveTextContent('Química')
  })

  it('pelo card de Minhas turmas, vem a turma e pede a disciplina se houver mais de uma', async () => {
    abrir({ search: '?turma=7&disciplina=3', state: { abrirCadastro: true, turmaId: 7 } })

    const dialogo = await screen.findByRole('dialog')
    expect(campo(dialogo, 'Turma')).toHaveTextContent('2º ano A')
    expect(campo(dialogo, 'Disciplina')).toHaveTextContent('Selecione…')

    await userEvent.click(within(dialogo).getByRole('button', { name: /salvar avaliação/i }))
    expect(within(dialogo).getByText('Escolha a disciplina.')).toBeInTheDocument()
  })

  it('cadastra na turma escolhida e abre a tela nela', async () => {
    const { router, chamadas } = abrir({ state: { abrirCadastro: true } })

    const dialogo = await screen.findByRole('dialog')
    await escolher(dialogo, 'Turma', '3º ano B')
    await preencherEEnviar(dialogo)

    expect(await screen.findByText(/cadastrada em 3º ano B · Química/)).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'POST')?.corpo).toMatchObject({ turmaId: 8, disciplinaId: 5 })
    expect(router.state.location.search).toBe('?turma=8&disciplina=5&avaliacao=av-nova')
  })
})
