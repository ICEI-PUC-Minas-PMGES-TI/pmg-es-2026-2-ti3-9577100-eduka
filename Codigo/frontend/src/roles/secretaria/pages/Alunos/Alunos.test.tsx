import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { TurmaAcademica } from '../../../../types/academico'
import type { AlunoDaLista } from '../../../../types/aluno'
import { Alunos } from './Alunos'

const ALUNOS: AlunoDaLista[] = [
  { id: 'ana', nome: 'Ana Souza', matricula: '2026001', genero: null, situacao: 'MATRICULADO', turmaId: 1, turmaNome: '1A' },
  { id: 'bia', nome: 'Bia Lima', matricula: '2026002', genero: null, situacao: 'TRANCADO', turmaId: 2, turmaNome: '2B' },
  { id: 'caio', nome: 'Caio Dias', matricula: '2026003', genero: null, situacao: 'SEM_TURMA', turmaId: null, turmaNome: null },
]

const TURMA: TurmaAcademica = {
  id: 1,
  periodoLetivoId: 5,
  periodoLetivoNome: '2026/2',
  cursoId: 7,
  cursoNome: 'Ensino Médio',
  nome: '1A',
  codigo: null,
  vagas: 30,
  turno: 'MATUTINO',
  ativa: true,
  disciplinas: [],
  matriculasAtivas: 1,
}

describe('Alunos', () => {
  it('mostra turma e situação que vêm com cada aluno, sem buscar as matrículas', async () => {
    const chamadas = simularApi(({ caminho }) => {
      if (caminho === '/alunos') return { corpo: ALUNOS }
      if (caminho === '/academico/secretaria/turmas') return { corpo: [TURMA] }
      return undefined
    })
    render(
      <MemoryRouter>
        <Alunos />
      </MemoryRouter>,
    )

    const linhaDaAna = (await screen.findByText('Ana Souza')).closest('tr') as HTMLElement
    expect(within(linhaDaAna).getByText('1A')).toBeInTheDocument()
    expect(within(linhaDaAna).getByText('Matriculado')).toBeInTheDocument()
    const linhaDaBia = screen.getByText('Bia Lima').closest('tr') as HTMLElement
    expect(within(linhaDaBia).getByText('Trancado')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^sem turma/i }))
    expect(screen.getByText('Caio Dias')).toBeInTheDocument()
    expect(screen.queryByText('Ana Souza')).not.toBeInTheDocument()

    expect(chamadas.some((c) => c.caminho.startsWith('/matriculas'))).toBe(false)
  })

  it('aluno anonimizado não conta como cadastrado e só pode ser visto', async () => {
    const anonimizado: AlunoDaLista = {
      id: 'dani',
      nome: 'Aluno anonimizado',
      matricula: '2024009',
      genero: null,
      situacao: 'ANONIMIZADO',
      turmaId: null,
      turmaNome: null,
    }
    simularApi(({ caminho }) => {
      if (caminho === '/alunos') return { corpo: [...ALUNOS, anonimizado] }
      if (caminho === '/academico/secretaria/turmas') return { corpo: [TURMA] }
      return undefined
    })
    render(
      <MemoryRouter>
        <Alunos />
      </MemoryRouter>,
    )

    const linha = (await screen.findByText('Aluno anonimizado')).closest('tr') as HTMLElement
    expect(within(linha).getByText('Anonimizado')).toBeInTheDocument()
    expect(within(linha).getByRole('button', { name: 'Ver' })).toBeInTheDocument()
    expect(within(linha).queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(within(linha).queryByRole('button', { name: 'Reenviar acesso' })).not.toBeInTheDocument()

    const resumo = screen.getByRole('region', { name: 'Resumo dos alunos' })
    expect(within(resumo).getByText('3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^anonimizados/i })).toBeInTheDocument()
  })
})
