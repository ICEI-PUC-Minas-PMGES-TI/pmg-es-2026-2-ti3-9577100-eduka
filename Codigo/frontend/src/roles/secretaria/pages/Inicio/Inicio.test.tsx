import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { paths } from '../../../../routes/paths'
import { simularApi } from '../../../../test/api'
import type { DashboardSecretaria, OcupacaoTurma } from '../../../../types/dashboard/dashboard'
import { Inicio } from './Inicio'

function turma(dados: Partial<OcupacaoTurma> & Pick<OcupacaoTurma, 'turmaId' | 'turmaNome' | 'cursoNome'>): OcupacaoTurma {
  return { periodoLetivoNome: '2026/2', turno: 'MATUTINO', vagas: 35, matriculasAtivas: 10, ...dados }
}

const PAINEL: DashboardSecretaria = {
  matriculasPorSituacao: { ATIVA: 45, TRANCADA: 2, CONCLUIDA: 78, CANCELADA: 0 },
  matriculasUltimos30Dias: 4,
  alunosSemTurma: 0,
  vagas: { ofertadas: 105, ocupadas: 45, livres: 60, turmasLotadas: 0 },
  // O backend manda da mais cheia para a mais vazia; a tela agrupa por curso e ordena pelo nome.
  turmas: [
    turma({ turmaId: 3, turmaNome: '10º ano A', cursoNome: 'Ensino Fundamental II', matriculasAtivas: 20 }),
    turma({ turmaId: 1, turmaNome: '1º ano A', cursoNome: 'Ensino Médio', matriculasAtivas: 15 }),
    turma({ turmaId: 2, turmaNome: '6º ano A', cursoNome: 'Ensino Fundamental II', turno: 'VESPERTINO' }),
  ],
}

function abrir(dashboard: DashboardSecretaria, pedidos: unknown[] = []) {
  simularApi(({ caminho }) => {
    if (caminho === '/dashboard/secretaria') return { corpo: dashboard }
    if (caminho === '/alunos/correcoes') return { corpo: pedidos }
    return undefined
  })
  const router = createMemoryRouter(
    [
      { path: paths.secretaria.inicio, element: <Inicio /> },
      { path: paths.secretaria.matriculas, element: <p>Tela de matrículas</p> },
      { path: paths.secretaria.academico, element: <p>Tela acadêmica</p> },
    ],
    { initialEntries: [paths.secretaria.inicio] },
  )
  render(<RouterProvider router={router} />)
}

describe('Início da secretaria', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('mostra os números de matrícula sem cartões de zero', async () => {
    abrir(PAINEL)

    const resumo = await screen.findByRole('region', { name: 'Resumo de matrículas' })
    expect(within(resumo).getByText('45')).toBeInTheDocument()
    expect(within(resumo).getByText('Em 3 turmas ativas')).toBeInTheDocument()
    expect(within(resumo).getByText('43%')).toBeInTheDocument()
    expect(within(resumo).getByText('45 de 105 · 60 livres')).toBeInTheDocument()
    expect(within(resumo).getByText('4')).toBeInTheDocument()
    // Sem pendências, a faixa "Para resolver" não aparece.
    expect(screen.queryByRole('region', { name: 'Para resolver' })).not.toBeInTheDocument()
  })

  it('agrupa as turmas por curso, com o total de cada um, e ordena pelo nome', async () => {
    abrir(PAINEL)

    const fundamental = await screen.findByRole('region', { name: 'Ensino Fundamental II' })
    expect(within(fundamental).getByText('30 alunos · 2 turmas · 40 vagas livres')).toBeInTheDocument()
    const nomes = within(fundamental)
      .getAllByRole('listitem')
      .map((item) => within(item).getByText(/ano A$/).textContent)
    expect(nomes).toEqual(['6º ano A', '10º ano A'])
    expect(within(fundamental).getByText('Vespertino')).toBeInTheDocument()
    expect(within(fundamental).getByRole('meter', { name: 'Ocupação da turma 10º ano A' })).toHaveAttribute(
      'aria-valuenow',
      '20',
    )

    const medio = screen.getByRole('region', { name: 'Ensino Médio' })
    expect(within(medio).getByText('15 alunos · 1 turma · 20 vagas livres')).toBeInTheDocument()
  })

  it('resume a situação das matrículas numa barra com legenda', async () => {
    abrir(PAINEL)

    const situacao = await screen.findByRole('region', { name: 'Situação das matrículas' })
    expect(within(situacao).getByText('125 matrículas de todos os períodos.')).toBeInTheDocument()
    expect(within(situacao).getByRole('img')).toHaveAccessibleName(
      'Ativa: 45, Concluída: 78, Trancada: 2, Cancelada: 0',
    )
  })

  it('lista as pendências e leva para a tela que resolve cada uma', async () => {
    abrir({ ...PAINEL, alunosSemTurma: 3, vagas: { ...PAINEL.vagas, turmasLotadas: 1 } })

    const pendencias = await screen.findByRole('region', { name: 'Para resolver' })
    expect(within(pendencias).getByText(/alunos cadastrados estão sem turma/)).toBeInTheDocument()
    expect(within(pendencias).getByText(/turma está lotada/)).toBeInTheDocument()

    await userEvent.click(within(pendencias).getByRole('button', { name: 'Matricular' }))
    expect(await screen.findByText('Tela de matrículas')).toBeInTheDocument()
  })

  it('mostra os pedidos de correção abertos', async () => {
    abrir(PAINEL, [
      {
        id: 'pedido-1',
        alunoId: 'aluno-1',
        alunoNome: 'Lucas Andrade',
        alunoMatricula: '2026-0412',
        mensagem: 'Meu telefone mudou',
        status: 'ABERTA',
        criadoEm: '2026-10-30T12:00:00Z',
        atendidaEm: null,
      },
    ])

    const lista = await screen.findByRole('list', { name: /pedidos de correção abertos/i })
    expect(within(lista).getByText('Lucas Andrade · 2026-0412')).toBeInTheDocument()
  })

  it('sem turmas ativas, avisa em vez de mostrar barras vazias', async () => {
    abrir({
      ...PAINEL,
      matriculasPorSituacao: { ATIVA: 0, TRANCADA: 0, CONCLUIDA: 0, CANCELADA: 0 },
      vagas: { ofertadas: 0, ocupadas: 0, livres: 0, turmasLotadas: 0 },
      turmas: [],
    })

    expect(await screen.findByText('Nenhuma turma ativa no momento.')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma turma ativa')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma matrícula registrada ainda.')).toBeInTheDocument()
  })
})
