import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../../test/api'
import type { CobrancaDoHistorico, HistoricoFinanceiro } from '../../../../../types/financeiro/pagamentoAluno'
import type { RiscoAtraso } from '../../../../../types/financeiro/riscoAtraso'
import { FinanceiroAluno } from './FinanceiroAluno'

function cobranca(
  id: string,
  descricao: string,
  valor: number,
  dataVencimento: string,
  status: CobrancaDoHistorico['status'],
  pagamento: CobrancaDoHistorico['pagamento'] = null,
): CobrancaDoHistorico {
  return { id, descricao, valor, dataVencimento, status, turmaNome: '2º ano A', pagaEm: null, pagamento }
}

const HISTORICO: HistoricoFinanceiro = {
  alunoId: 'aluno-ana',
  alunoNome: 'Ana Souza',
  alunoNumeroMatricula: '2026001',
  resumo: {
    valorEmAberto: 2280,
    valorEmAtraso: 500,
    valorPago: 1190,
    quantidadePendente: 2,
    quantidadeEmAtraso: 1,
    quantidadePaga: 2,
    quantidadeCancelada: 1,
  },
  cobrancas: [
    cobranca('cob-dez', 'Mensalidade de dezembro', 890, '2026-12-10', 'PENDENTE'),
    cobranca('cob-nov', 'Mensalidade de novembro', 890, '2026-11-10', 'PENDENTE'),
    cobranca('cob-set', 'Mensalidade de setembro', 500, '2026-09-26', 'EM_ATRASO'),
    cobranca('cob-ago', 'Mensalidade de agosto', 890, '2026-08-10', 'PAGA', {
      origem: 'ONLINE',
      valorPago: 890,
      valorEstornado: 0,
      dataPagamento: '2026-08-09',
      forma: 'PIX',
      formaManual: null,
      comprovanteUrl: '/cobrancas/cob-ago/comprovante',
    }),
    cobranca('cob-mat', 'Material didático', 300, '2026-07-05', 'PAGA', {
      origem: 'MANUAL',
      valorPago: 300,
      valorEstornado: 0,
      dataPagamento: '2026-07-02',
      forma: null,
      formaManual: 'TRANSFERENCIA',
      comprovanteUrl: null,
    }),
    cobranca('cob-uni', 'Uniforme', 240, '2026-06-30', 'CANCELADA'),
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

/** Mostra para onde os atalhos levaram. */
function Destino() {
  const { pathname, search } = useLocation()
  return <p>Destino: {pathname + search}</p>
}

type Opcoes = {
  risco?: RespostaApi
  historico?: HistoricoFinanceiro
  responsavelFinanceiro?: string | null
  anonimizado?: boolean
  /** Área em que a tela Alunos está aberta. */
  area?: '/financeiro/alunos' | '/master/alunos'
}

function abrir({
  risco = { corpo: RISCO },
  historico = HISTORICO,
  responsavelFinanceiro,
  anonimizado,
  area = '/financeiro/alunos',
}: Opcoes = {}) {
  simularApi(({ caminho }) => {
    if (caminho === '/alunos/aluno-ana/financeiro') return { corpo: historico }
    if (caminho === '/alunos/aluno-ana/financeiro/risco') return risco
    return undefined
  })
  render(
    <MemoryRouter initialEntries={[area]}>
      <Routes>
        <Route
          path={area}
          element={
            <FinanceiroAluno
              alunoId="aluno-ana"
              responsavelFinanceiro={responsavelFinanceiro}
              anonimizado={anonimizado}
            />
          }
        />
        <Route path="*" element={<Destino />} />
      </Routes>
    </MemoryRouter>,
  )
}

/** A linha da tabela de uma cobrança, pela referência. */
function linha(referencia: string): HTMLElement {
  return screen.getByText(referencia).closest('tr') as HTMLElement
}

describe('Financeiro do aluno', () => {
  it('mostra o risco de atraso com o motivo e o que cada regra mediu', async () => {
    abrir()

    const risco = await screen.findByRole('region', { name: 'Risco de atraso' })
    expect(within(risco).getByText('Risco médio')).toBeInTheDocument()
    expect(within(risco).getByText(RISCO.motivo)).toBeInTheDocument()
    expect(within(risco).getByText('Dias médios de atraso:')).toBeInTheDocument()
    expect(within(risco).getByText('(+2 pontos)')).toBeInTheDocument()
    expect(within(risco).getByText('(+1 ponto)')).toBeInTheDocument()
    expect(screen.getByText('Mensalidade de setembro')).toBeInTheDocument()
  })

  it('sem o risco, o histórico aparece do mesmo jeito', async () => {
    abrir({ risco: problema(500, 'ERRO_INTERNO') })

    expect(await screen.findByText('Mensalidade de setembro')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Risco de atraso' })).not.toBeInTheDocument()
  })

  it('cards de total cobrado (sem as canceladas), já pago, em aberto com o próximo vencimento e em atraso', async () => {
    abrir()

    const cards = await screen.findByRole('region', { name: 'Resumo financeiro do aluno' })
    const card = (rotulo: string) => within(cards).getByText(rotulo).parentElement as HTMLElement
    expect(card('Total cobrado')).toHaveTextContent(/R\$\s3\.470,00/)
    expect(card('Total cobrado')).toHaveTextContent('5 cobranças geradas')
    expect(card('Já pago')).toHaveTextContent(/R\$\s1\.190,00/)
    expect(card('Já pago')).toHaveTextContent('2 pagamentos confirmados')
    expect(card('Em aberto')).toHaveTextContent(/R\$\s2\.280,00/)
    expect(card('Em aberto')).toHaveTextContent('Vence em 10/11/2026')
    expect(card('Em atraso')).toHaveTextContent(/R\$\s500,00/)
    expect(card('Em atraso')).toHaveTextContent('1 cobrança vencida')
  })

  it('aluno sem nada em aberto aparece em dia', async () => {
    abrir({
      historico: {
        ...HISTORICO,
        resumo: { ...HISTORICO.resumo, valorEmAberto: 0, valorEmAtraso: 0, quantidadePendente: 0, quantidadeEmAtraso: 0 },
        cobrancas: HISTORICO.cobrancas.filter((c) => c.status === 'PAGA'),
      },
    })

    const cards = await screen.findByRole('region', { name: 'Resumo financeiro do aluno' })
    expect(within(cards).getByText('Aluno em dia')).toBeInTheDocument()
    expect(within(cards).getByText('Nada em aberto')).toBeInTheDocument()
  })

  it('cada lançamento traz vencimento, pagamento, valor e situação', async () => {
    abrir()

    await screen.findByText('Mensalidade de agosto')
    expect(linha('Mensalidade de agosto')).toHaveTextContent('09/08/2026 · PIX')
    expect(within(linha('Mensalidade de agosto')).getByText('Paga')).toBeInTheDocument()
    expect(linha('Material didático')).toHaveTextContent('02/07/2026 · Baixa manual (Transferência)')
    expect(within(linha('Mensalidade de novembro')).getByText('A pagar')).toBeInTheDocument()
    expect(within(linha('Mensalidade de setembro')).getByText('Em atraso')).toBeInTheDocument()
    expect(within(linha('Uniforme')).getByText('Cancelada')).toBeInTheDocument()
  })

  it('o filtro mostra uma situação por vez, e "A pagar" junta a que vence e a vencida', async () => {
    abrir({ responsavelFinanceiro: 'Cláudia Andrade' })
    await screen.findByText('Mensalidade de agosto')
    expect(screen.getByText(/6 de 6 lançamentos · responsável financeiro: Cláudia Andrade/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'A pagar' }))
    expect(screen.getByText('Mensalidade de dezembro')).toBeInTheDocument()
    expect(screen.getByText('Mensalidade de novembro')).toBeInTheDocument()
    expect(screen.getByText('Mensalidade de setembro')).toBeInTheDocument()
    expect(screen.queryByText('Mensalidade de agosto')).not.toBeInTheDocument()
    expect(screen.getByText(/3 de 6 lançamentos/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Canceladas' }))
    expect(screen.getByText('Uniforme')).toBeInTheDocument()
    expect(screen.queryByText('Mensalidade de setembro')).not.toBeInTheDocument()
  })

  it('"Ver na tela de cobranças" abre Cobranças filtrada pelo aluno', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /ver na tela de cobranças/i }))

    expect(screen.getByText('Destino: /financeiro/cobrancas?aluno=aluno-ana')).toBeInTheDocument()
  })

  it('"Nova cobrança" abre o formulário com o aluno escolhido, na área de quem está usando', async () => {
    abrir({ area: '/master/alunos' })

    await userEvent.click(await screen.findByRole('button', { name: /nova cobrança/i }))

    expect(screen.getByText('Destino: /master/cobrancas/nova?aluno=aluno-ana')).toBeInTheDocument()
  })

  it('aluno anonimizado não recebe cobrança nova, e sem responsável as cobranças saem em nome dele', async () => {
    abrir({ anonimizado: true, responsavelFinanceiro: null })

    expect(await screen.findByRole('button', { name: /ver na tela de cobranças/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /nova cobrança/i })).not.toBeInTheDocument()
    expect(screen.getByText(/sem responsável financeiro: as cobranças saem em nome do aluno/)).toBeInTheDocument()
  })

  it('sem nenhuma cobrança, avisa e ainda deixa lançar a primeira', async () => {
    abrir({
      historico: {
        ...HISTORICO,
        resumo: {
          valorEmAberto: 0,
          valorEmAtraso: 0,
          valorPago: 0,
          quantidadePendente: 0,
          quantidadeEmAtraso: 0,
          quantidadePaga: 0,
          quantidadeCancelada: 0,
        },
        cobrancas: [],
      },
    })

    expect(await screen.findByText('Nenhuma cobrança lançada para este aluno.')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Filtrar por situação' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /nova cobrança/i })).toBeInTheDocument()
  })
})
