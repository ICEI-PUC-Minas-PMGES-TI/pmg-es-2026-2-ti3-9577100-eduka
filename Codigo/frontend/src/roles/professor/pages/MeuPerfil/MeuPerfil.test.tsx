import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { gravarSessao } from '../../../../services/acesso/modulosSessao'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import { MODULOS_ESCOLA } from '../../../../types/escola/escola'
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

function turma(id: number, disciplinas: number[], aulasPorDisciplina = 0): TurmaDoProfessor {
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
    alunosAtivos: 30,
    disciplinas: disciplinas.map((d) => ({
      id: d,
      nome: `Disciplina ${d}`,
      codigo: null,
      cargaHoraria: 80,
      horarios: (['SEGUNDA', 'QUARTA', 'SEXTA'] as const)
        .slice(0, aulasPorDisciplina)
        .map((dia) => ({ diaSemana: dia, horaInicio: '07:30:00', horaFim: '08:20:00' })),
    })),
  }
}

// Matemática (3) nas duas turmas e Física (4) só na segunda: 2 disciplinas diferentes.
// Na primeira turma, Matemática tem aula na segunda e na quarta; a segunda turma não tem grade.
const TURMAS = [turma(1, [3], 2), turma(2, [3, 4])]

function abrir(perfil: RespostaApi = { corpo: PROFESSORA }, turmas: TurmaDoProfessor[] = TURMAS) {
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
  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('mostra os dados pessoais e profissionais juntos, sem abas', async () => {
    abrir()

    expect(await screen.findByRole('heading', { name: 'Paula Reis' })).toBeInTheDocument()
    expect(screen.getByText('Professor · CLT - 40h')).toBeInTheDocument()

    const pessoais = screen.getByRole('region', { name: 'Dados pessoais' })
    expect(within(pessoais).getByText('529.982.247-25')).toBeInTheDocument()
    expect(within(pessoais).getByText('paula@escola.com')).toBeInTheDocument()
    expect(within(pessoais).getByText('(31) 99999-8888')).toBeInTheDocument()

    const profissionais = screen.getByRole('region', { name: 'Dados profissionais' })
    expect(within(profissionais).getByText('Licenciatura em Matemática')).toBeInTheDocument()
    expect(within(profissionais).getByText('01/02/2024')).toBeInTheDocument()

    expect(within(screen.getByRole('region', { name: 'Resumo do perfil' })).getByText('paula@escola.com')).toBeInTheDocument()
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
  })

  it('o atalho leva a Minhas turmas', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Ver minhas turmas' }))
    expect(await screen.findByText('Tela de turmas')).toBeInTheDocument()
  })

  it('resume turmas, disciplinas sem repetir e aulas por semana', async () => {
    abrir()

    const resumo = await screen.findByLabelText('Resumo das turmas')
    expect(await within(resumo).findByText('Turmas ativas')).toBeInTheDocument()
    expect(within(resumo).getByText('Turmas ativas').nextElementSibling).toHaveTextContent('2')
    expect(within(resumo).getByText('Disciplinas').nextElementSibling).toHaveTextContent('2')
    expect(within(resumo).getByText('Aulas por semana').nextElementSibling).toHaveTextContent('2')
  })

  it('mostra as turmas no carrossel com o horário de cada disciplina e os atalhos', async () => {
    abrir()

    const carrossel = await screen.findByRole('region', { name: 'Minhas turmas' })
    const primeira = await within(carrossel).findByRole('article', { name: 'Turma Turma 1' })
    expect(within(primeira).getByText(/1 disciplina sua · 2 aulas por semana/i)).toBeInTheDocument()
    expect(within(primeira).getByText(/seg e qua · 07h30/i)).toBeInTheDocument()
    expect(within(primeira).getByRole('link', { name: 'Avaliações' })).toHaveAttribute(
      'href',
      '/professor/avaliacoes?turma=1&disciplina=3',
    )
    expect(within(primeira).getByRole('link', { name: 'Chamada' })).toHaveAttribute(
      'href',
      '/professor/chamada?turma=1&disciplina=3',
    )

    const segunda = within(carrossel).getByRole('article', { name: 'Turma Turma 2' })
    expect(within(segunda).getAllByText(/horário a definir/i)).toHaveLength(2)
    expect(within(carrossel).getByRole('button', { name: 'Próxima turma' })).toBeEnabled()
  })

  it('turma encerrada vai para o fim do carrossel e não entra no resumo', async () => {
    const encerrada = { ...turma(9, [7], 3), nome: 'Turma antiga', ativa: false }
    abrir({ corpo: PROFESSORA }, [encerrada, ...TURMAS])

    const carrossel = await screen.findByRole('region', { name: 'Minhas turmas' })
    await within(carrossel).findByRole('article', { name: 'Turma Turma antiga' })
    const resumo = screen.getByLabelText('Resumo das turmas')
    expect(within(resumo).getByText('Turmas ativas').nextElementSibling).toHaveTextContent('2')
    expect(within(resumo).getByText('Aulas por semana').nextElementSibling).toHaveTextContent('2')

    expect(within(carrossel).getByText(/2 turmas ativas e 1 encerrada/i)).toBeInTheDocument()
    const cartoes = within(carrossel).getAllByRole('article')
    expect(cartoes[0]).toHaveAccessibleName('Turma Turma 1')
    expect(cartoes[2]).toHaveAccessibleName('Turma Turma antiga')
    expect(within(cartoes[2]).getByText('Encerrada')).toBeInTheDocument()
  })

  it('sem o controle de frequência contratado, o atalho da Chamada some', async () => {
    gravarSessao('paula@escola.com', MODULOS_ESCOLA.filter((m) => m !== 'CONTROLE_FREQUENCIA'))
    abrir()

    const carrossel = await screen.findByRole('region', { name: 'Minhas turmas' })
    const primeira = await within(carrossel).findByRole('article', { name: 'Turma Turma 1' })
    expect(within(primeira).getByRole('link', { name: 'Avaliações' })).toBeInTheDocument()
    expect(within(primeira).queryByRole('link', { name: 'Chamada' })).not.toBeInTheDocument()
  })

  it('sem turmas, diz que a secretaria faz a alocação', async () => {
    abrir({ corpo: PROFESSORA }, [])

    const carrossel = await screen.findByRole('region', { name: 'Minhas turmas' })
    expect(await within(carrossel).findByText(/ainda não está alocado em nenhuma turma/i)).toBeInTheDocument()
  })

  it('login sem cadastro de professor explica o que fazer', async () => {
    abrir(problema(404, 'NAO_ENCONTRADO'))

    expect(await screen.findByRole('alert')).toHaveTextContent(/não está vinculado a um cadastro de professor/i)
  })
})
