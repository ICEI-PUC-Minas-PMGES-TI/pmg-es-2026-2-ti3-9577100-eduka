import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { DashboardEscola, DashboardFinanceiro } from '../../../../types/dashboard'
import { formatarMoeda } from '../../../../utils/formatters'
import { Inicio } from './Inicio'

/** Valor como a tela mostra, com o espaço comum que o getByText compara (o Intl usa o não separável). */
function reais(valor: number): string {
  return formatarMoeda(valor).replace(/\s/g, ' ')
}

const ESCOLA: DashboardEscola = {
  pessoas: {
    alunos: 120,
    alunosAtivos: 110,
    alunosSemTurma: 10,
    professoresAtivos: 8,
    professoresInativos: 1,
    secretarias: 2,
    financeiro: 1,
  },
  estrutura: { cursos: 3, disciplinas: 12, turmasAtivas: 6, turmas: 7, periodoLetivoAtual: '2026/2' },
  ocupacao: { vagasOfertadas: 180, matriculasAtivas: 110, percentual: 61 },
  desempenho: { avaliacoes: 40, notasLancadas: 300, aproveitamento: 72 },
}

const FINANCEIRO: DashboardFinanceiro = {
  recebidoNoMes: { valor: 4200, quantidade: 6 },
  faltaReceber: { valor: 2500, quantidade: 4 },
  emAtraso: { alunos: 2, cobrancas: 3, valor: 900, inadimplencia: 18 },
  despesasAPagar: { valor: 0, quantidade: 0, proximoVencimento: null },
  ultimosMeses: ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'].map((mes) => ({
    mes,
    entrou: 1000,
    saiu: 300,
  })),
  vencemNaSemana: [],
  ultimosPagamentos: [],
  ultimasAcoes: [
    {
      usuarioId: 'usuario-fernanda',
      usuarioNome: 'Fernanda Rocha',
      perfil: 'FINANCEIRO',
      operacao: 'PAGAR',
      entidade: 'COBRANCA',
      registroId: 'cob-1',
      dataHora: '2026-10-27T17:32:00Z',
    },
    {
      usuarioId: null,
      usuarioNome: null,
      perfil: null,
      operacao: 'CONFIRMAR_PAGAMENTO',
      entidade: 'COBRANCA',
      registroId: 'cob-2',
      dataHora: '2026-10-27T15:00:00Z',
    },
    {
      usuarioId: 'usuario-master',
      usuarioNome: 'Paula Gestora',
      perfil: 'MASTER',
      operacao: 'ALTERAR_SITUACAO',
      entidade: 'CONTA_RECEBIMENTO',
      registroId: 'conta-1',
      dataHora: '2026-10-26T13:00:00Z',
    },
  ],
}

function abrir(financeiro: RespostaApi) {
  simularApi(({ caminho }) => {
    if (caminho === '/dashboard/escola') return { corpo: ESCOLA }
    if (caminho === '/dashboard/financeiro') return financeiro
    return undefined
  })
  render(
    <MemoryRouter>
      <Inicio />
    </MemoryRouter>,
  )
}

describe('Início do master', () => {
  it('junta o resumo da escola com o financeiro', async () => {
    abrir({ corpo: FINANCEIRO })

    const resumo = await screen.findByRole('region', { name: 'Resumo da escola' })
    expect(within(resumo).getByText('De 120 alunos cadastrados')).toBeInTheDocument()
    expect(within(resumo).getByText(reais(4200))).toBeInTheDocument()
    expect(within(resumo).getByText('4 cobranças em aberto')).toBeInTheDocument()
    expect(within(resumo).getByText('Alunos · 18% de inadimplência')).toBeInTheDocument()

    expect(screen.getByRole('region', { name: 'Dinheiro que entrou e saiu' })).toBeInTheDocument()
    expect(screen.getByText('8 ativos de 9')).toBeInTheDocument()
  })

  it('conta quem fez cada ação, inclusive as que vêm do Asaas', async () => {
    abrir({ corpo: FINANCEIRO })

    const acoes = await screen.findByRole('region', { name: 'Últimas ações' })
    expect(within(acoes).getByText('Fernanda Rocha')).toBeInTheDocument()
    expect(within(acoes).getByText('Registrou o pagamento de uma cobrança')).toBeInTheDocument()
    expect(within(acoes).getByText('Asaas')).toBeInTheDocument()
    expect(within(acoes).getByText('Confirmou o pagamento de uma cobrança')).toBeInTheDocument()
    expect(within(acoes).getByText('Alterou a situação da conta de recebimento')).toBeInTheDocument()
  })

  it('sem o resumo financeiro, mostra o erro em vez de uma tela pela metade', async () => {
    abrir(problema(403, 'ACESSO_NEGADO'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Você não tem permissão para acessar este recurso.')
    expect(screen.queryByRole('region', { name: 'Resumo da escola' })).not.toBeInTheDocument()
  })
})
