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
    pagaAposCancelamento: false,
    formaPagamento: null,
    pagamentoIniciadoEm: null,
    editavel: status === 'PENDENTE' || status === 'EM_ATRASO',
    pagamento: null,
    criadoEm: '2026-10-01T00:00:00Z',
    atualizadoEm: '2026-10-01T00:00:00Z',
  }
}

const SIMULAR = /simular pagamento/i

const ACOES = { onEditar: vi.fn(), onCancelar: vi.fn(), onVerComprovante: vi.fn() }

describe('CobrancaDetalhe', () => {
  it('cobrança em aberto mostra o atalho de Sandbox e avisa o resultado', async () => {
    const onSimular = vi.fn()
    render(
      <CobrancaDetalhe
        cobranca={cobranca('EM_ATRASO')}
        {...ACOES}
        onSimularPagamento={onSimular}
        mensagemSandbox={{ texto: 'ainda não foi emitida', erro: true }}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: SIMULAR }))

    expect(onSimular).toHaveBeenCalledWith(expect.objectContaining({ id: 'cob-1' }))
    expect(screen.getByRole('alert')).toHaveTextContent('ainda não foi emitida')
  })

  it('sem o atalho ou com a cobrança paga, o botão não aparece', () => {
    const { rerender } = render(<CobrancaDetalhe cobranca={cobranca('PENDENTE')} {...ACOES} />)
    expect(screen.queryByRole('button', { name: SIMULAR })).not.toBeInTheDocument()

    rerender(<CobrancaDetalhe cobranca={cobranca('PAGA')} {...ACOES} onSimularPagamento={vi.fn()} />)
    expect(screen.queryByRole('button', { name: SIMULAR })).not.toBeInTheDocument()
  })

  it('paga depois de cancelar avisa a escola e mostra quando foi cancelada', () => {
    render(
      <CobrancaDetalhe
        cobranca={{
          ...cobranca('PAGA'),
          canceladaEm: '2026-10-05T15:00:00Z',
          pagaAposCancelamento: true,
        }}
        {...ACOES}
      />,
    )

    expect(screen.getByRole('status')).toHaveTextContent(/depois do cancelamento/i)
    expect(screen.getByText('Cancelada em')).toBeInTheDocument()
  })

  it('paga sem cancelamento não mostra o aviso', () => {
    render(<CobrancaDetalhe cobranca={cobranca('PAGA')} {...ACOES} />)

    expect(screen.queryByText(/depois do cancelamento/i)).not.toBeInTheDocument()
  })

  it('em aberto e antes do Pagar, edita e cancela', async () => {
    const onEditar = vi.fn()
    const onCancelar = vi.fn()
    render(<CobrancaDetalhe cobranca={cobranca('PENDENTE')} {...ACOES} onEditar={onEditar} onCancelar={onCancelar} />)

    await userEvent.click(screen.getByRole('button', { name: 'Editar' }))
    await userEvent.click(screen.getByRole('button', { name: /cancelar cobrança/i }))

    expect(onEditar).toHaveBeenCalledWith(expect.objectContaining({ id: 'cob-1' }))
    expect(onCancelar).toHaveBeenCalledWith(expect.objectContaining({ id: 'cob-1' }))
    expect(screen.getByText(/aguardando o aluno clicar em pagar/i)).toBeInTheDocument()
  })

  it('depois do Pagar, bloqueia a edição, mostra a forma e ainda deixa cancelar', () => {
    render(
      <CobrancaDetalhe
        cobranca={{
          ...cobranca('PENDENTE'),
          editavel: false,
          formaPagamento: 'PIX',
          pagamentoIniciadoEm: '2026-10-08T15:00:00Z',
        }}
        {...ACOES}
      />,
    )

    expect(screen.getByRole('button', { name: 'Editar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: /cancelar cobrança/i })).toBeEnabled()
    expect(screen.getByText(/aluno clicou em pagar · PIX · 08\/10\/2026/i)).toBeInTheDocument()
    expect(screen.getByText(/aguardando a confirmação do banco/i)).toBeInTheDocument()
    expect(screen.getByRole('note')).toHaveTextContent(/edição bloqueada/i)
  })

  it('paga online abre o comprovante e não oferece editar nem cancelar', async () => {
    const onVerComprovante = vi.fn()
    render(
      <CobrancaDetalhe
        cobranca={{
          ...cobranca('PAGA'),
          pagamento: {
            origem: 'ONLINE',
            valorPago: 850,
            dataPagamento: '2026-10-09',
            forma: 'PIX',
            comprovanteUrl: '/cobrancas/cob-1/comprovante',
          },
        }}
        {...ACOES}
        onVerComprovante={onVerComprovante}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: /ver comprovante/i }))

    expect(onVerComprovante).toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(screen.getByText(/pagamento confirmado · 09\/10\/2026/i)).toBeInTheDocument()
  })
})
