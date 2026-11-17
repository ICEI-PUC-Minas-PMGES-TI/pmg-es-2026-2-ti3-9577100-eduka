import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { DashboardSecretaria, OcupacaoTurma } from '../../../../types/dashboard/dashboard'
import { Inicio } from './Inicio'

function turma(turmaId: number, cursoNome: string, matriculasAtivas: number, vagas = 30): OcupacaoTurma {
  return {
    turmaId,
    turmaNome: `Turma ${turmaId}`,
    periodoLetivoNome: '2026',
    cursoNome,
    turno: 'MATUTINO',
    vagas,
    matriculasAtivas,
  }
}

const DASHBOARD: DashboardSecretaria = {
  matriculasPorSituacao: { ATIVA: 58, TRANCADA: 2, CANCELADA: 4, CONCLUIDA: 16 },
  matriculasUltimos30Dias: 7,
  alunosSemTurma: 3,
  vagas: { ofertadas: 90, ocupadas: 58, livres: 32, turmasLotadas: 1 },
  turmas: [turma(1, 'Ensino Médio', 30), turma(2, 'Ensino Médio', 18), turma(3, 'Fundamental II', 10)],
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

function abrir(dashboard: RespostaApi | (() => RespostaApi) = { corpo: DASHBOARD }) {
  simularApi(({ caminho }) => {
    if (caminho === '/dashboard/secretaria') return typeof dashboard === 'function' ? dashboard() : dashboard
    if (caminho === '/alunos/correcoes') return { corpo: [] }
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

  it('resume as matrículas nos cards', async () => {
    abrir()

    const cards = await screen.findByRole('region', { name: 'Resumo de matrículas' })
    expect(within(cards).getByText('58')).toBeInTheDocument()
    expect(within(cards).getByText('De 90 vagas abertas')).toBeInTheDocument()
    expect(within(cards).getByText('7')).toBeInTheDocument()
    expect(within(cards).getByText('Sem matrícula ativa')).toBeInTheDocument()
    // Trancamentos sobre o total das quatro situações (58 + 2 + 4 + 16).
    expect(within(cards).getByText('De 80 matrículas no total')).toBeInTheDocument()
  })

  it('mostra cada situação de matrícula num medidor sobre o total', async () => {
    abrir()

    const ativas = await screen.findByRole('meter', { name: 'Matrículas na situação Ativa' })
    expect(ativas).toHaveAttribute('aria-valuenow', '58')
    expect(ativas).toHaveAttribute('aria-valuetext', '58 de 80')
    expect(screen.getByRole('meter', { name: 'Matrículas na situação Concluída' })).toHaveAttribute(
      'aria-valuenow',
      '16',
    )
  })

  it('soma as matrículas ativas por curso e mostra a ocupação de cada turma', async () => {
    abrir()

    expect(await screen.findByText('48 alunos')).toBeInTheDocument()
    expect(screen.getByText('10 alunos')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Ocupação da turma Turma 1' })).toHaveAttribute('aria-valuenow', '30')
    expect(screen.getByText('30/30')).toBeInTheDocument()
    expect(screen.getByText('18/30')).toBeInTheDocument()
  })

  it('com mais de seis turmas, mostra seis e o Ver todas abre o resto', async () => {
    const turmas = Array.from({ length: 8 }, (_, i) => turma(i + 1, 'Ensino Médio', 20 - i))
    abrir({ corpo: { ...DASHBOARD, turmas } })

    await screen.findByText('Turma 6')
    expect(screen.queryByText('Turma 7')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ver todas as 8 turmas' }))
    expect(screen.getByText('Turma 8')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar menos' }))
    expect(screen.queryByText('Turma 8')).not.toBeInTheDocument()
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
    expect(screen.getAllByText('Nenhuma turma ativa no momento.')).toHaveLength(2)
    expect(screen.getByText('Todos matriculados')).toBeInTheDocument()
  })

  it('o atalho Cadastrar um aluno abre Alunos com o cadastro aberto', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /cadastrar um aluno/i }))

    expect(await screen.findByText(/abriu \/secretaria\/alunos/i)).toHaveTextContent('{"abrirCadastro":true}')
  })

  it('o atalho Criar turma abre o acadêmico na aba de turmas', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /criar turma ou disciplina/i }))

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
