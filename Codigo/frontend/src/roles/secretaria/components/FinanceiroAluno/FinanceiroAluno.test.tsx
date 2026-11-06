import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { HistoricoFinanceiro } from '../../../../types/pagamentoAluno'
import type { RiscoAtraso } from '../../../../types/riscoAtraso'
import { FinanceiroAluno } from './FinanceiroAluno'

const HISTORICO: HistoricoFinanceiro = {
  alunoId: 'aluno-ana',
  alunoNome: 'Ana Souza',
  alunoNumeroMatricula: '2026001',
  resumo: {
    valorEmAberto: 500,
    valorEmAtraso: 500,
    valorPago: 0,
    quantidadePendente: 0,
    quantidadeEmAtraso: 1,
    quantidadePaga: 0,
    quantidadeCancelada: 0,
  },
  cobrancas: [
    {
      id: 'cob-1',
      descricao: 'Mensalidade de setembro',
      valor: 500,
      dataVencimento: '2026-09-26',
      status: 'EM_ATRASO',
      turmaNome: null,
      pagaEm: null,
      pagamento: null,
    },
  ],
}

const RISCO: RiscoAtraso = {
  alunoId: 'aluno-ana',
  alunoNome: 'Ana Souza',
  alunoNumeroMatricula: '2026001',
  nivel: 'MEDIO',
  pontos: 3,
  motivo: 'Atraso médio de 40 dias; R$ 500,00 vencidos em aberto (menos de 2 cobranças médias)',
  indicadores: {
    desde: '2025-11-05',
    cobrancasVencidas: 1,
    atrasos: 1,
    atrasosPagos: 0,
    atrasosEmAberto: 1,
    diasMediosAtraso: 40,
    valorEmAtraso: 500,
    valorEmAberto: 500,
  },
  fatores: [
    {
      regra: 'ATRASOS_ANTERIORES',
      pontos: 0,
      descricao: '1 atraso nos últimos 12 meses (1 ainda em aberto)',
    },
    { regra: 'DIAS_MEDIOS_ATRASO', pontos: 2, descricao: 'Atraso médio de 40 dias' },
    {
      regra: 'VALOR_EM_ABERTO',
      pontos: 1,
      descricao: 'R$ 500,00 vencidos em aberto (menos de 2 cobranças médias)',
    },
  ],
}

function abrir(risco: RespostaApi) {
  simularApi(({ caminho }) => {
    if (caminho === '/alunos/aluno-ana/financeiro') return { corpo: HISTORICO }
    if (caminho === '/alunos/aluno-ana/financeiro/risco') return risco
    return undefined
  })
  render(<FinanceiroAluno alunoId="aluno-ana" />)
}

describe('Financeiro do aluno', () => {
  it('mostra o risco de atraso com o motivo e o que cada regra mediu', async () => {
    abrir({ corpo: RISCO })

    const risco = await screen.findByRole('region', { name: 'Risco de atraso' })
    expect(within(risco).getByText('Risco médio')).toBeInTheDocument()
    expect(within(risco).getByText(RISCO.motivo)).toBeInTheDocument()
    expect(within(risco).getByText('Dias médios de atraso:')).toBeInTheDocument()
    expect(within(risco).getByText('(+2 pontos)')).toBeInTheDocument()
    expect(within(risco).getByText('(+1 ponto)')).toBeInTheDocument()
    expect(screen.getByText('Mensalidade de setembro')).toBeInTheDocument()
  })

  it('sem o risco, o histórico aparece do mesmo jeito', async () => {
    abrir(problema(500, 'ERRO_INTERNO'))

    expect(await screen.findByText('Mensalidade de setembro')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Risco de atraso' })).not.toBeInTheDocument()
  })
})
