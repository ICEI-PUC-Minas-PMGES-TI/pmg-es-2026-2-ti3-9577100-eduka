import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Cobranca, StatusCobranca } from '../../../../types/cobranca'
import { CobrancaDetalhe } from './CobrancaDetalhe'

function cobranca(status: StatusCobranca): Cobranca {
  return {
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
    status,
    canceladaEm: null,
    pagaEm: null,
    pagamento: null,
    criadoEm: '2026-10-01T00:00:00Z',
    atualizadoEm: '2026-10-01T00:00:00Z',
  }
}

const SIMULAR = /simular pagamento/i

describe('CobrancaDetalhe', () => {
  it('cobrança em aberto mostra o atalho de Sandbox e avisa o resultado', async () => {
    const onSimular = vi.fn()
    render(
      <CobrancaDetalhe
        cobranca={cobranca('EM_ATRASO')}
        onVerBoleto={vi.fn()}
        onSimularPagamento={onSimular}
        mensagemSandbox={{ texto: 'ainda não foi emitida', erro: true }}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: SIMULAR }))

    expect(onSimular).toHaveBeenCalledWith(expect.objectContaining({ id: 'cob-1' }))
    expect(screen.getByRole('alert')).toHaveTextContent('ainda não foi emitida')
  })

  it('sem o atalho ou com a cobrança paga, o botão não aparece', () => {
    const { rerender } = render(<CobrancaDetalhe cobranca={cobranca('PENDENTE')} onVerBoleto={vi.fn()} />)
    expect(screen.queryByRole('button', { name: SIMULAR })).not.toBeInTheDocument()

    rerender(<CobrancaDetalhe cobranca={cobranca('PAGA')} onVerBoleto={vi.fn()} onSimularPagamento={vi.fn()} />)
    expect(screen.queryByRole('button', { name: SIMULAR })).not.toBeInTheDocument()
  })
})
