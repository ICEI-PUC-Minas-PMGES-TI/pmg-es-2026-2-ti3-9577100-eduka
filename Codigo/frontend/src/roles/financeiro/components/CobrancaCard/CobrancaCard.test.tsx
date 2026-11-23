import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Cobranca } from '../../../../types/financeiro/cobranca'
import { CobrancaCard } from './CobrancaCard'

const PAGA: Cobranca = {
  id: 'cob-1',
  alunoId: 'aluno-1',
  alunoNome: 'Laura Fernandes',
  alunoNumeroMatricula: null,
  matriculaId: null,
  turmaId: null,
  turmaNome: null,
  responsavelId: null,
  responsavelNome: null,
  descricao: 'Mensalidade outubro/2026',
  valor: 850,
  dataVencimento: '2026-10-10',
  status: 'PAGA',
  canceladaEm: null,
  pagaEm: '2026-10-08T12:00:00Z',
  pagaAposCancelamento: false,
  formaPagamento: null,
  pagamentoIniciadoEm: null,
  editavel: false,
  pagamento: null,
  criadoEm: '2026-10-01T00:00:00Z',
  atualizadoEm: '2026-10-08T12:00:00Z',
}

describe('CobrancaCard', () => {
  it('paga comum mostra o selo de paga', () => {
    render(<CobrancaCard cobranca={PAGA} aberta={false} onAlternar={vi.fn()} />)

    expect(screen.getByText('Paga')).toBeInTheDocument()
  })

  it('paga depois de cancelar troca o selo pelo alerta', () => {
    render(
      <CobrancaCard
        cobranca={{ ...PAGA, canceladaEm: '2026-10-05T15:00:00Z', pagaAposCancelamento: true }}
        aberta={false}
        onAlternar={vi.fn()}
      />,
    )

    expect(screen.getByText('Paga após cancelar')).toBeInTheDocument()
    expect(screen.queryByText('Paga')).not.toBeInTheDocument()
  })
})
