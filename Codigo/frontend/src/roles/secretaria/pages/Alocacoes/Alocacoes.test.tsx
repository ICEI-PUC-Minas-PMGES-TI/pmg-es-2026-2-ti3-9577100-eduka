import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import { logarComo } from '../../../../test/sessao'
import type { Alocacao, Turma } from '../../../../types/academico/alocacao'
import type { Professor } from '../../../../types/pessoas/professor'
import { Alocacoes } from './Alocacoes'

function turma(id: number, nome: string, disciplinas: [number, string][], ativa = true): Turma {
  return {
    id,
    nome,
    codigo: null,
    turno: 'MATUTINO',
    periodoLetivoId: 1,
    periodoLetivoNome: '2026',
    cursoNome: 'Ensino Médio',
    ativa,
    disciplinas: disciplinas.map(([idDisciplina, nomeDisciplina]) => ({
      id: idDisciplina,
      nome: nomeDisciplina,
      codigo: null,
      cargaHoraria: 80,
    })),
  }
}

function professor(id: string, nome: string, ativo = true): Professor {
  return {
    id,
    escolaId: 'escola-1',
    nome,
    cpf: null,
    email: null,
    telefone: null,
    formacao: null,
    dataAdmissao: null,
    vinculo: 'CLT_40H',
    ativo,
    aguardandoPrimeiroAcesso: false,
  }
}

function alocacao(id: string, prof: Professor, turmaId: number, disciplinaId: number, disciplinaNome: string): Alocacao {
  return {
    id,
    professorId: prof.id,
    professorNome: prof.nome,
    turmaId,
    turmaNome: `Turma ${turmaId}`,
    turno: 'MATUTINO',
    periodoLetivoId: 1,
    periodoLetivoNome: '2026',
    disciplinaId,
    disciplinaNome,
  }
}

const ANA = professor('p1', 'Ana Lima')
const BRUNO = professor('p2', 'Bruno Costa')
const CARLA = professor('p3', 'Carla Inativa', false)

// Volta da API fora de ordem: a tela ordena pelo nome e deixa a inativa de fora.
const TURMAS = [
  turma(2, '2º ano A', [[12, 'Física']]),
  turma(1, '1º ano A', [
    [11, 'Português'],
    [10, 'Matemática'],
  ]),
  turma(3, 'Turma antiga', [[13, 'Latim']], false),
]

const MATEMATICA_ANA = alocacao('al-1', ANA, 1, 10, 'Matemática')

type Rotas = {
  turmas?: RespostaApi
  alocacoes?: (turmaId: number, chamada: ChamadaApi) => RespostaApi
  escrita?: (chamada: ChamadaApi) => RespostaApi | undefined
}

function abrir({ turmas = { corpo: TURMAS }, alocacoes, escrita }: Rotas = {}) {
  const chamadas = simularApi((chamada) => {
    const { metodo, caminho } = chamada
    if (metodo === 'GET' && caminho === '/academico/secretaria/turmas') return turmas
    if (metodo === 'GET' && caminho === '/professores') return { corpo: [CARLA, BRUNO, ANA] }
    const daTurma = caminho.match(/^\/turmas\/(\d+)\/alocacoes$/)
    if (metodo === 'GET' && daTurma) {
      const id = Number(daTurma[1])
      return alocacoes ? alocacoes(id, chamada) : { corpo: id === 1 ? [MATEMATICA_ANA] : [] }
    }
    if (metodo === 'GET' && /^\/turmas\/\d+\/horario$/.test(caminho)) return { corpo: [] }
    return escrita?.(chamada)
  })
  const router = createMemoryRouter(
    [
      { path: '/secretaria/academico/alocacoes', element: <Alocacoes /> },
      { path: '/secretaria/inicio', element: <h1>Início da secretaria</h1> },
    ],
    { initialEntries: ['/secretaria/academico/alocacoes'] },
  )
  render(<RouterProvider router={router} />)
  return { chamadas, router }
}

async function escolher(disciplina: string, nomeProfessor: RegExp | string) {
  await userEvent.click(await screen.findByRole('button', { name: new RegExp(`^Professor de ${disciplina}:`) }))
  await userEvent.click(screen.getByRole('option', { name: nomeProfessor }))
}

const escritas = (chamadas: ChamadaApi[]) =>
  chamadas.filter((c) => c.metodo !== 'GET').map((c) => `${c.metodo} ${c.caminho}`)

describe('Alocações', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('resume a alocação da escola só com as turmas ativas', async () => {
    abrir()

    const resumo = await screen.findByRole('region', { name: 'Resumo da alocação da escola' })
    // 1 das 3 disciplinas das turmas ativas tem professor; a turma inativa fica de fora.
    expect(within(resumo).getByText('33%')).toBeInTheDocument()
    expect(within(resumo).getByText('Faltam alocar')).toBeInTheDocument()
    expect(within(resumo).getByText('0 de 2')).toBeInTheDocument()
    // Bruno ainda sem turma, entre os 2 ativos (Carla está inativa).
    expect(within(resumo).getByText('1 de 2')).toBeInTheDocument()
    expect(screen.queryByText('Turma antiga')).not.toBeInTheDocument()
  })

  it('abre a primeira turma pelo nome, com o professor de cada disciplina', async () => {
    abrir()

    const grade = await screen.findByRole('region', { name: 'Professores da turma 1º ano A' })
    expect(screen.getByRole('button', { name: /^1º ano A/, pressed: true })).toBeInTheDocument()
    expect(grade).toHaveTextContent('1 de 2 disciplinas com professor')
    expect(within(grade).getByRole('button', { name: 'Professor de Matemática: Ana Lima' })).toBeInTheDocument()
    expect(within(grade).getByRole('button', { name: 'Professor de Português: sem professor' })).toBeInTheDocument()
    expect(within(grade).getByText('Tudo salvo')).toBeInTheDocument()
  })

  it('a lista de professores traz só os ativos, com a carga de cada um', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Professor de Português: sem professor' }))

    const lista = screen.getByRole('listbox', { name: 'Professor de Português' })
    expect(within(lista).getByRole('option', { name: /Ana Lima.*1 disciplina em 1 turma/ })).toBeInTheDocument()
    expect(within(lista).getByRole('option', { name: /Bruno Costa.*Ainda sem turma/ })).toBeInTheDocument()
    expect(within(lista).queryByRole('option', { name: /Carla/ })).not.toBeInTheDocument()
  })

  it('aloca um professor numa disciplina vaga e salva', async () => {
    const { chamadas } = abrir({
      escrita: ({ metodo, caminho, corpo }) =>
        metodo === 'POST' && caminho === '/professores/p2/alocacoes'
          ? { status: 201, corpo: alocacao('al-2', BRUNO, 1, (corpo as { disciplinaId: number }).disciplinaId, 'Português') }
          : undefined,
    })

    await escolher('Português', /Bruno Costa/)
    expect(screen.getByText('1 alteração não salva')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar alocação' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Alocação da turma 1º ano A salva.')
    expect(screen.getByText('Tudo salvo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Professor de Português: Bruno Costa' })).toBeInTheDocument()
    expect(escritas(chamadas)).toEqual(['POST /professores/p2/alocacoes'])
    expect(chamadas.find((c) => c.metodo === 'POST')?.corpo).toEqual({ turmaId: 1, disciplinaId: 11 })
    // A turma ficou completa: 2 de 2.
    expect(screen.getByText('1 de 2')).toBeInTheDocument()
  })

  it('trocar o professor remove a alocação atual e cria a nova', async () => {
    const { chamadas } = abrir({
      escrita: ({ metodo, caminho }) => {
        if (metodo === 'DELETE' && caminho === '/professores/p1/alocacoes/al-1') return { status: 204 }
        if (metodo === 'POST' && caminho === '/professores/p2/alocacoes') {
          return { status: 201, corpo: alocacao('al-3', BRUNO, 1, 10, 'Matemática') }
        }
        return undefined
      },
    })

    await escolher('Matemática', /Bruno Costa/)
    await userEvent.click(screen.getByRole('button', { name: 'Salvar alocação' }))

    expect(await screen.findByRole('status')).toHaveTextContent('salva')
    expect(escritas(chamadas)).toEqual(['DELETE /professores/p1/alocacoes/al-1', 'POST /professores/p2/alocacoes'])
  })

  it('Deixar sem professor só remove a alocação', async () => {
    const { chamadas } = abrir({
      escrita: ({ metodo }) => (metodo === 'DELETE' ? { status: 204 } : undefined),
    })

    await escolher('Matemática', 'Deixar sem professor')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar alocação' }))

    expect(await screen.findByRole('status')).toHaveTextContent('salva')
    expect(escritas(chamadas)).toEqual(['DELETE /professores/p1/alocacoes/al-1'])
    expect(screen.getByRole('button', { name: 'Professor de Matemática: sem professor' })).toBeInTheDocument()
  })

  it('falha ao salvar: diz qual disciplina, relê a turma e mantém a escolha para tentar de novo', async () => {
    const { chamadas } = abrir({
      escrita: ({ metodo }) => (metodo === 'POST' ? problema(409, 'CONFLITO', 'Disciplina já alocada') : undefined),
    })

    await escolher('Português', /Bruno Costa/)
    await userEvent.click(screen.getByRole('button', { name: 'Salvar alocação' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível salvar Português.')
    expect(screen.getByText('1 alteração não salva')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Professor de Português: Bruno Costa' })).toBeInTheDocument()
    // Releu as alocações da turma depois da falha (a carga inicial também lê).
    expect(chamadas.filter((c) => c.caminho === '/turmas/1/alocacoes')).toHaveLength(2)
  })

  it('Desfazer volta ao que está gravado', async () => {
    abrir()

    await escolher('Português', /Bruno Costa/)
    await userEvent.click(screen.getByRole('button', { name: 'Desfazer' }))

    expect(screen.getByRole('button', { name: 'Professor de Português: sem professor' })).toBeInTheDocument()
    expect(screen.getByText('Tudo salvo')).toBeInTheDocument()
  })

  it('trocar de turma guarda o rascunho e marca a turma com alterações não salvas', async () => {
    abrir()

    await escolher('Português', /Bruno Costa/)
    await userEvent.click(screen.getByRole('button', { name: /^2º ano A/ }))
    expect(await screen.findByRole('region', { name: 'Professores da turma 2º ano A' })).toBeInTheDocument()
    expect(
      within(screen.getByRole('button', { name: /^1º ano A/ })).getByRole('img', { name: 'Tem alterações não salvas' }),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^1º ano A/ }))
    expect(screen.getByRole('button', { name: 'Professor de Português: Bruno Costa' })).toBeInTheDocument()
  })

  it('sair com escolhas não salvas pede confirmação', async () => {
    logarComo('SECRETARIA')
    const { router } = abrir()

    await escolher('Português', /Bruno Costa/)
    await act(() => router.navigate('/secretaria/inicio'))

    const aviso = await screen.findByRole('dialog', { name: 'Sair sem salvar?' })
    await userEvent.click(within(aviso).getByRole('button', { name: 'Continuar editando' }))
    expect(router.state.location.pathname).toBe('/secretaria/academico/alocacoes')
    expect(screen.getByRole('button', { name: 'Professor de Português: Bruno Costa' })).toBeInTheDocument()

    await act(() => router.navigate('/secretaria/inicio'))
    await userEvent.click(
      within(await screen.findByRole('dialog', { name: 'Sair sem salvar?' })).getByRole('button', {
        name: 'Sair sem salvar',
      }),
    )
    expect(await screen.findByRole('heading', { name: 'Início da secretaria' })).toBeInTheDocument()
  })

  it('sem escolhas pendentes, sai sem perguntar', async () => {
    logarComo('SECRETARIA')
    const { router } = abrir()

    await screen.findByRole('region', { name: 'Professores da turma 1º ano A' })
    await act(() => router.navigate('/secretaria/inicio'))

    expect(await screen.findByRole('heading', { name: 'Início da secretaria' })).toBeInTheDocument()
  })

  it('sem turma ativa, orienta a cadastrar uma', async () => {
    abrir({ turmas: { corpo: [TURMAS[2]] } })

    expect(await screen.findByText(/Nenhuma turma ativa ainda/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Resumo da alocação da escola' })).not.toBeInTheDocument()
  })

  it('erro ao carregar oferece tentar de novo', async () => {
    let tentativas = 0
    abrir({
      turmas: { corpo: TURMAS },
      alocacoes: (id) => {
        tentativas += 1
        return tentativas === 1 ? problema(500, 'ERRO_INTERNO', 'Falha') : { corpo: id === 1 ? [MATEMATICA_ANA] : [] }
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByRole('region', { name: 'Professores da turma 1º ano A' })).toBeInTheDocument()
  })
})
