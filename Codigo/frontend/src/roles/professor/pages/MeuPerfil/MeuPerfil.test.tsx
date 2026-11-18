import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import type { Professor } from '../../../../types/pessoas/professor'
import { MeuPerfil } from './MeuPerfil'

const PROFESSORA: Professor = {
  id: 'prof-1',
  escolaId: 'escola-1',
  nome: 'Paula Reis',
  cpf: '52998224725',
  email: 'paula@escola.com',
  telefone: '31999998888',
  formacao: 'Licenciatura em Matemática',
  dataAdmissao: '2024-02-01',
  vinculo: 'CLT_40H',
  ativo: true,
  aguardandoPrimeiroAcesso: false,
}

function turma(id: number, disciplinas: number[]): TurmaDoProfessor {
  return {
    id,
    nome: `Turma ${id}`,
    codigo: null,
    turno: 'MATUTINO',
    ativa: true,
    periodoLetivoId: 1,
    periodoLetivoNome: '2026',
    cursoId: 1,
    cursoNome: 'Ensino Médio',
    disciplinas: disciplinas.map((d) => ({ id: d, nome: `Disciplina ${d}`, codigo: null, cargaHoraria: 80, horarios: [] })),
  }
}

function abrir(perfil: RespostaApi = { corpo: PROFESSORA }) {
  // Matemática (3) nas duas turmas e Física (4) só na segunda: 2 disciplinas diferentes.
  const turmas = [turma(1, [3]), turma(2, [3, 4])]
  simularApi(({ caminho }) => {
    if (caminho === '/portal/professor/me') return perfil
    if (caminho === '/portal/professor/turmas') return { corpo: turmas }
    return undefined
  })
  const router = createMemoryRouter(
    [
      { path: '/professor/meu-perfil', element: <MeuPerfil /> },
      { path: '/professor/turmas', element: <p>Tela de turmas</p> },
    ],
    { initialEntries: ['/professor/meu-perfil'] },
  )
  render(<RouterProvider router={router} />)
}

describe('MeuPerfil', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('mostra os dados pessoais, profissionais e de acesso do professor logado', async () => {
    abrir()

    expect(await screen.findByRole('heading', { name: 'Paula Reis' })).toBeInTheDocument()
    expect(screen.getByText('Professor · CLT - 40h')).toBeInTheDocument()
    expect(screen.getByText('529.982.247-25')).toBeInTheDocument()
    expect(screen.getByText('paula@escola.com')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'Dados profissionais' }))
    expect(screen.getByText('Licenciatura em Matemática')).toBeInTheDocument()
    expect(screen.getAllByText('01/02/2024').length).toBeGreaterThan(0)

    await userEvent.click(screen.getByRole('tab', { name: 'Acesso' }))
    expect(screen.getByText('Perfil no sistema')).toBeInTheDocument()
    expect(screen.getByText(/esqueceu sua senha\?/i)).toBeInTheDocument()
  })

  it('o atalho leva a Minhas turmas', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Ver minhas turmas' }))
    expect(await screen.findByText('Tela de turmas')).toBeInTheDocument()
  })

  it('conta turmas e disciplinas sem repetir a mesma disciplina', async () => {
    abrir()

    expect(await screen.findByRole('heading', { name: 'Paula Reis' })).toBeInTheDocument()
    const turmas = await screen.findByText('Turmas')
    expect(turmas.nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Disciplinas').nextElementSibling).toHaveTextContent('2')
  })

  it('login sem cadastro de professor explica o que fazer', async () => {
    abrir(problema(404, 'NAO_ENCONTRADO'))

    expect(await screen.findByRole('alert')).toHaveTextContent(/não está vinculado a um cadastro de professor/i)
  })
})
