import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { DashboardSecretaria, OcupacaoTurma } from '../../../../types/dashboard/dashboard'
import type { SolicitacaoCorrecao } from '../../../../types/pessoas/solicitacaoCorrecao'
import { Inicio } from './Inicio'

function turma(
  turmaId: number,
  cursoNome: string,
  matriculasAtivas: number,
  extra: Partial<OcupacaoTurma> = {},
): OcupacaoTurma {
  return {
    turmaId,
    turmaNome: `Turma ${turmaId}`,
    periodoLetivoNome: '2026',
    cursoNome,
    turno: 'MATUTINO',
    vagas: 30,
    matriculasAtivas,
    ...extra,
  }
}

const DASHBOARD: DashboardSecretaria = {
  matriculasPorSituacao: { ATIVA: 58, TRANCADA: 2, CANCELADA: 4, CONCLUIDA: 16 },
  matriculasUltimos30Dias: 7,
  alunosSemTurma: 3,
  vagas: { ofertadas: 90, ocupadas: 58, livres: 32, turmasLotadas: 1 },
  turmas: [turma(1, 'Ensino Médio', 30), turma(2, 'Ensino Médio', 18), turma(3, 'Fundamental II', 10)],
}

/** Sem alunos sem turma nem turma lotada: nada a resolver. */
const EM_DIA: DashboardSecretaria = {
  ...DASHBOARD,
  alunosSemTurma: 0,
  vagas: { ...DASHBOARD.vagas, turmasLotadas: 0 },
}

const PEDIDO: SolicitacaoCorrecao = {
  id: 'pedido-1',
  alunoId: 'aluno-1',
  alunoNome: 'Lucas Andrade',
  alunoMatricula: '2026-0412',
  mensagem: 'Meu telefone mudou',
  status: 'ABERTA',
  criadoEm: '2026-10-30T12:00:00Z',
  atendidaEm: null,
}

/** Destino dos atalhos: mostra o caminho e o estado com que a tela foi aberta. */
function Destino() {
  const location = useLocation()
  return (
    <p>
      Abriu {location.pathname}
      {location.search} {JSON.stringify(location.state)}
    </p>
  )
}

function abrir(
  dashboard: RespostaApi | (() => RespostaApi) = { corpo: DASHBOARD },
  pedidos: SolicitacaoCorrecao[] = [],
) {
  simularApi(({ caminho }) => {
    if (caminho === '/dashboard/secretaria') return typeof dashboard === 'function' ? dashboard() : dashboard
    if (caminho === '/alunos/correcoes') return { corpo: pedidos }
    return undefined
  })
  const router = createMemoryRouter(
    [
      { path: '/secretaria/inicio', element: <Inicio /> },
      { path: '*', element: <Destino /> },
    ],
    { initialEntries: ['/secretaria/inicio'] },
  )
  render(<RouterProvider router={router} />)
}

describe('Início da secretaria', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('resume as matrículas em três números', async () => {
    abrir()

    const cards = await screen.findByRole('region', { name: 'Resumo de matrículas' })
    expect(within(cards).getByText('58')).toBeInTheDocument()
    expect(within(cards).getByText('Em 3 turmas ativas')).toBeInTheDocument()
    // 58 de 90 vagas: 64,4%, arredondado.
    expect(within(cards).getByText('64%')).toBeInTheDocument()
    expect(within(cards).getByText('58 de 90 · 32 livres')).toBeInTheDocument()
    expect(within(cards).getByText('7')).toBeInTheDocument()
  })

  it('lista as pendências e leva para a tela que resolve cada uma', async () => {
    abrir()

    const pendencias = await screen.findByRole('region', { name: 'Para resolver' })
    expect(within(pendencias).getByText(/alunos cadastrados estão sem turma/)).toBeInTheDocument()
    expect(within(pendencias).getByText(/turma está lotada/)).toBeInTheDocument()

    await userEvent.click(within(pendencias).getByRole('button', { name: 'Matricular' }))
    expect(await screen.findByText(/abriu \/secretaria\/matriculas/i)).toBeInTheDocument()
  })

  it('sem pendências, a faixa Para resolver não aparece', async () => {
    abrir({ corpo: EM_DIA })

    await screen.findByRole('region', { name: 'Resumo de matrículas' })
    expect(screen.queryByRole('region', { name: 'Para resolver' })).not.toBeInTheDocument()
  })

  it('agrupa as turmas por curso, com o total de cada um, e mostra a ocupação', async () => {
    abrir()

    const medio = await screen.findByRole('region', { name: 'Ensino Médio' })
    expect(within(medio).getByText('48 alunos · 2 turmas · 12 vagas livres')).toBeInTheDocument()
    expect(within(medio).getByRole('meter', { name: 'Ocupação da turma Turma 1' })).toHaveAttribute(
      'aria-valuenow',
      '30',
    )
    expect(within(medio).getByText('30/30')).toBeInTheDocument()
    expect(within(medio).getByText('18/30')).toBeInTheDocument()

    const fundamental = screen.getByRole('region', { name: 'Fundamental II' })
    expect(within(fundamental).getByText('10 alunos · 1 turma · 20 vagas livres')).toBeInTheDocument()
  })

  it('ordena as turmas do curso pelo nome, com o número em ordem natural', async () => {
    abrir({
      corpo: {
        ...EM_DIA,
        // O backend manda da mais cheia para a mais vazia.
        turmas: [
          turma(10, 'Fundamental II', 20, { turmaNome: '10º ano A' }),
          turma(6, 'Fundamental II', 10, { turmaNome: '6º ano A', turno: 'VESPERTINO' }),
        ],
      },
    })

    const curso = await screen.findByRole('region', { name: 'Fundamental II' })
    const nomes = within(curso)
      .getAllByRole('listitem')
      .map((item) => within(item).getByText(/ano A$/).textContent)
    expect(nomes).toEqual(['6º ano A', '10º ano A'])
    expect(within(curso).getByText('Vespertino')).toBeInTheDocument()
  })

  it('mostra todas as turmas, sem esconder nenhuma atrás de um botão', async () => {
    const turmas = Array.from({ length: 8 }, (_, i) => turma(i + 1, 'Ensino Médio', 20 - i))
    abrir({ corpo: { ...EM_DIA, turmas } })

    expect(await screen.findByText('Turma 8')).toBeInTheDocument()
    expect(screen.getAllByRole('meter', { name: /Ocupação da turma/ })).toHaveLength(8)
    expect(screen.queryByRole('button', { name: /ver todas/i })).not.toBeInTheDocument()
  })

  it('resume a situação das matrículas numa barra com legenda', async () => {
    abrir()

    const situacao = await screen.findByRole('region', { name: 'Situação das matrículas' })
    expect(within(situacao).getByText('80 matrículas de todos os períodos.')).toBeInTheDocument()
    expect(within(situacao).getByRole('img')).toHaveAccessibleName(
      'Ativa: 58, Concluída: 16, Trancada: 2, Cancelada: 4',
    )
  })

  it('mostra os pedidos de correção abertos', async () => {
    abrir({ corpo: EM_DIA }, [PEDIDO])

    const lista = await screen.findByRole('list', { name: /pedidos de correção abertos/i })
    expect(within(lista).getByText('Lucas Andrade · 2026-0412')).toBeInTheDocument()
  })

  it('escola sem turmas nem matrículas mostra os painéis vazios', async () => {
    abrir({
      corpo: {
        matriculasPorSituacao: { ATIVA: 0, TRANCADA: 0, CANCELADA: 0, CONCLUIDA: 0 },
        matriculasUltimos30Dias: 0,
        alunosSemTurma: 0,
        vagas: { ofertadas: 0, ocupadas: 0, livres: 0, turmasLotadas: 0 },
        turmas: [],
      },
    })

    expect(await screen.findByText('Nenhuma matrícula registrada ainda.')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma turma ativa no momento.')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma turma ativa')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma vaga aberta')).toBeInTheDocument()
  })

  it('o atalho Cadastrar aluno abre Alunos com o cadastro aberto', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /cadastrar aluno/i }))

    expect(await screen.findByText(/abriu \/secretaria\/alunos/i)).toHaveTextContent('{"abrirCadastro":true}')
  })

  it('o atalho Criar turma abre o acadêmico na aba de turmas', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /criar turma/i }))

    expect(await screen.findByText(/abriu \/secretaria\/academico\?aba=turmas/i)).toBeInTheDocument()
  })

  it('Ver matrículas leva à tela de matrículas', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Ver matrículas' }))

    expect(await screen.findByText(/abriu \/secretaria\/matriculas/i)).toBeInTheDocument()
  })

  it('erro ao carregar o resumo oferece tentar de novo, sem esconder os pedidos de correção', async () => {
    let tentativas = 0
    abrir(() => {
      tentativas += 1
      return tentativas === 1 ? problema(500, 'ERRO_INTERNO', 'Falha') : { corpo: DASHBOARD }
    })

    expect(await screen.findByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
    expect(screen.getByText('Nenhum pedido aguardando.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByRole('region', { name: 'Resumo de matrículas' })).toBeInTheDocument()
  })
})
