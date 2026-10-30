import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type {
  CobrancaAluno,
  CobrancaDoHistorico,
  DadosPagamento,
  FormaPagamento,
  SituacaoPagamento,
} from '../../../../types/pagamentoAluno'
import { ModalPagamento } from './ModalPagamento'

const COBRANCA: CobrancaDoHistorico = {
  id: 'cob-1',
  descricao: 'Mensalidade de outubro',
  valor: 500,
  dataVencimento: '2026-11-10',
  status: 'PENDENTE',
  turmaNome: null,
  pagaEm: null,
  pagamento: null,
}

function pronto(forma: FormaPagamento): RespostaApi {
  const dados: DadosPagamento = {
    situacao: 'PRONTO',
    forma,
    valor: 500,
    vencimento: '2026-11-10',
    pix:
      forma === 'PIX'
        ? { qrCodeBase64: 'iVBORw0KGgo=', copiaECola: '00020126580014br.gov.bcb.pix', validade: null }
        : null,
    boleto: forma === 'BOLETO' ? { linhaDigitavel: '2379338128600078271369500006330597552000004', codigoBarras: '2379' } : null,
  }
  return { corpo: dados }
}

function consulta(situacaoPagamento: SituacaoPagamento): RespostaApi {
  const cobranca: CobrancaAluno = {
    id: 'cob-1',
    descricao: COBRANCA.descricao,
    valor: 500,
    dataVencimento: COBRANCA.dataVencimento,
    status: situacaoPagamento === 'PAGA' ? 'PAGA' : 'PENDENTE',
    situacaoPagamento,
    formaPagamento: 'PIX',
    pagamento: null,
    acoes: [],
  }
  return { corpo: cobranca }
}

const PAGAR_NO_SANDBOX = 'POST /portal/aluno/cobrancas/cob-1/sandbox/pagar'

function backend(forma: FormaPagamento, sandbox: RespostaApi) {
  return simularApi(({ metodo, caminho }) => {
    if (`${metodo} ${caminho}` === 'POST /portal/aluno/cobrancas/cob-1/pagar') return pronto(forma)
    if (`${metodo} ${caminho}` === PAGAR_NO_SANDBOX) return sandbox
    if (`${metodo} ${caminho}` === 'GET /portal/aluno/cobrancas/cob-1') {
      return consulta(sandbox.status === 202 ? 'PAGA' : 'AGUARDANDO_PAGAMENTO')
    }
    return undefined
  })
}

async function gerar(forma: FormaPagamento) {
  if (forma === 'BOLETO') await userEvent.click(screen.getByRole('radio', { name: /boleto/i }))
  const rodape = screen.getByRole('button', { name: 'Cancelar' }).parentElement as HTMLElement
  await userEvent.click(within(rodape).getByRole('button', { name: 'Pagar' }))
}

describe('ModalPagamento no ambiente de teste', () => {
  it.each<FormaPagamento>(['PIX', 'BOLETO'])('Pagar com %s no Sandbox confirma o pagamento na hora', async (forma) => {
    const chamadas = backend(forma, { status: 202 })
    const onPago = vi.fn()
    render(<ModalPagamento cobranca={COBRANCA} onPago={onPago} onFechar={vi.fn()} />)

    await gerar(forma)
    expect(await screen.findByText('Ambiente de teste')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Pagar' }))

    expect(await screen.findByText('Pagamento confirmado!')).toBeInTheDocument()
    expect(onPago).toHaveBeenCalled()
    expect(chamadas.map((c) => `${c.metodo} ${c.caminho}`)).toContain(PAGAR_NO_SANDBOX)
  })

  it('cobrança ainda não emitida mostra o aviso e deixa tentar de novo', async () => {
    backend('PIX', problema(400, 'REQUISICAO_INVALIDA'))
    render(<ModalPagamento cobranca={COBRANCA} onPago={vi.fn()} onFechar={vi.fn()} />)

    await gerar('PIX')
    await userEvent.click(await screen.findByRole('button', { name: 'Pagar' }))

    expect(await screen.findByText(/ainda não foi registrado no Asaas/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pagar' })).toBeEnabled()
  })
})
