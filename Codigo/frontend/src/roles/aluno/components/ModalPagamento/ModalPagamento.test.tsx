import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type {
  CobrancaAluno,
  CobrancaDoHistorico,
  DadosPagamento,
  FormaPagamento,
  SituacaoPagamento,
} from '../../../../types/financeiro/pagamentoAluno'
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

const PAGAR = 'POST /portal/aluno/cobrancas/cob-1/pagar'
const CONSULTA = 'GET /portal/aluno/cobrancas/cob-1'
const COMPROVANTE = 'GET /portal/aluno/cobrancas/cob-1/comprovante'

function pronto(forma: FormaPagamento, validade: string | null = null): RespostaApi {
  const dados: DadosPagamento = {
    situacao: 'PRONTO',
    forma,
    valor: 500,
    vencimento: '2026-11-10',
    pix: forma === 'PIX' ? { qrCodeBase64: 'iVBORw0KGgo=', copiaECola: '00020126580014br.gov.bcb.pix', validade } : null,
    boleto:
      forma === 'BOLETO'
        ? { linhaDigitavel: '23793381286000782713695000063305975520000045000', codigoBarras: '2379' }
        : null,
    recebedorNome: 'Colégio Horizonte',
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

const DADOS_COMPROVANTE = {
  numero: 'cob-1',
  beneficiarioNome: 'Colégio Horizonte',
  beneficiarioDocumento: null,
  pagadorNome: 'Marta',
  pagadorDocumentoMascarado: null,
  descricao: COBRANCA.descricao,
  vencimento: '2026-11-10',
  valorPago: 500,
  valorEstornado: 0,
  dataPagamento: '2026-11-09',
  forma: 'PIX',
  codigoTransacao: 'pay_1',
}

/** Backend em que a cobrança está paga a partir de `paga()`; o PIX vence em `validade`. */
function backend({
  paga = () => false,
  validade = null,
  extra,
}: {
  paga?: () => boolean
  validade?: string | null
  extra?: (chamada: ChamadaApi) => RespostaApi | undefined
} = {}) {
  return simularApi((chamada) => {
    const resposta = extra?.(chamada)
    if (resposta) return resposta
    const rota = `${chamada.metodo} ${chamada.caminho}`
    if (rota === PAGAR) return pronto((chamada.corpo as { forma: FormaPagamento }).forma, validade)
    if (rota === CONSULTA) return consulta(paga() ? 'PAGA' : 'AGUARDANDO_PAGAMENTO')
    if (rota === COMPROVANTE) return { corpo: DADOS_COMPROVANTE }
    return undefined
  })
}

function abrir(onPago = vi.fn()) {
  render(<ModalPagamento cobranca={COBRANCA} onPago={onPago} onFechar={vi.fn()} />)
  return onPago
}

function rodape() {
  return screen.getByRole('dialog')
}

describe('ModalPagamento (IU17-A a IU17-C)', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('o comando muda conforme a forma: Pagar com PIX ou Gerar boleto', async () => {
    const chamadas = backend()
    abrir()

    expect(screen.getByText(/Valor a pagar/)).toHaveTextContent('R$ 500,00')
    expect(screen.getByRole('button', { name: 'Pagar com PIX' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: /Boleto/ }))
    expect(screen.queryByRole('button', { name: 'Pagar com PIX' })).not.toBeInTheDocument()
    // Nada é pedido ao provedor antes do clique.
    expect(chamadas.some((c) => `${c.metodo} ${c.caminho}` === PAGAR)).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Gerar boleto' }))

    expect(await screen.findByRole('heading', { name: 'Pagar com boleto' })).toBeInTheDocument()
    expect(chamadas.find((c) => `${c.metodo} ${c.caminho}` === PAGAR)?.corpo).toEqual({ forma: 'BOLETO' })
  })

  it('PIX mostra QR Code, copia e cola, a situação e a escola que recebe', async () => {
    backend()
    abrir()

    await userEvent.click(screen.getByRole('button', { name: 'Pagar com PIX' }))

    expect(await screen.findByRole('img', { name: /QR Code do PIX/ })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'PIX copia e cola' })).toHaveValue('00020126580014br.gov.bcb.pix')
    expect(screen.getByText('Aguardando pagamento')).toBeInTheDocument()
    expect(screen.getByText('Colégio Horizonte')).toBeInTheDocument()
  })

  it('Trocar para boleto altera a forma da mesma cobrança, sem voltar à escolha', async () => {
    const chamadas = backend()
    abrir()
    await userEvent.click(screen.getByRole('button', { name: 'Pagar com PIX' }))
    await screen.findByRole('img', { name: /QR Code do PIX/ })

    await userEvent.click(screen.getByRole('button', { name: 'Trocar para boleto' }))

    const linha = await screen.findByRole('textbox', { name: /linha digitável/ })
    // Pontuada para leitura; o Copiar leva só os números.
    expect((linha as HTMLInputElement).value).toMatch(/^23793\.38128 /)
    expect(screen.getByText(/fica em atraso, mas continua podendo ser paga/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Copiar linha' })).toBeInTheDocument()
    expect(chamadas.filter((c) => `${c.metodo} ${c.caminho}` === PAGAR).map((c) => c.corpo)).toEqual([
      { forma: 'PIX' },
      { forma: 'BOLETO' },
    ])

    await userEvent.click(within(rodape()).getByRole('button', { name: 'Trocar para PIX' }))
    expect(await screen.findByRole('img', { name: /QR Code do PIX/ })).toBeInTheDocument()
  })

  it('atualiza sozinha quando o pagamento é confirmado e passa a mostrar o comprovante', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let pago = false
    backend({ paga: () => pago })
    const onPago = abrir()
    const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    await usuario.click(screen.getByRole('button', { name: 'Pagar com PIX' }))
    await screen.findByRole('img', { name: /QR Code do PIX/ })

    pago = true
    await act(() => vi.advanceTimersByTimeAsync(5000))

    expect(await screen.findByText('Pagamento confirmado!')).toBeInTheDocument()
    expect(await screen.findByText('pay_1')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Comprovante de pagamento' })).toBeInTheDocument()
    expect(onPago).toHaveBeenCalled()
  })

  it('o boleto também se atualiza sozinho, numa consulta mais espaçada', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let pago = false
    const chamadas = backend({ paga: () => pago })
    abrir()
    const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    await usuario.click(screen.getByRole('radio', { name: /Boleto/ }))
    await usuario.click(screen.getByRole('button', { name: 'Gerar boleto' }))
    await screen.findByRole('textbox', { name: /linha digitável/ })

    await act(() => vi.advanceTimersByTimeAsync(5000))
    expect(chamadas.some((c) => `${c.metodo} ${c.caminho}` === CONSULTA)).toBe(false)

    pago = true
    await act(() => vi.advanceTimersByTimeAsync(25000))
    expect(await screen.findByText('Pagamento confirmado!')).toBeInTheDocument()
  })

  it('QR Code vencido some e dá para gerar um novo', async () => {
    const chamadas = backend({ validade: '2020-01-01T00:00:00Z' })
    abrir()
    await userEvent.click(screen.getByRole('button', { name: 'Pagar com PIX' }))

    expect(await screen.findByText(/Este QR Code venceu/)).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /QR Code do PIX/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Gerar novo QR Code' }))
    expect(chamadas.filter((c) => `${c.metodo} ${c.caminho}` === PAGAR)).toHaveLength(2)
  })
})

const PAGAR_NO_SANDBOX = 'POST /portal/aluno/cobrancas/cob-1/sandbox/pagar'

function backendSandbox(sandbox: RespostaApi) {
  return backend({
    paga: () => sandbox.status === 202,
    extra: ({ metodo, caminho }) => (`${metodo} ${caminho}` === PAGAR_NO_SANDBOX ? sandbox : undefined),
  })
}

async function gerar(forma: FormaPagamento) {
  if (forma === 'BOLETO') await userEvent.click(screen.getByRole('radio', { name: /boleto/i }))
  await userEvent.click(screen.getByRole('button', { name: forma === 'PIX' ? 'Pagar com PIX' : 'Gerar boleto' }))
}

describe('ModalPagamento no ambiente de teste', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it.each<FormaPagamento>(['PIX', 'BOLETO'])('Pagar com %s no Sandbox confirma o pagamento na hora', async (forma) => {
    const chamadas = backendSandbox({ status: 202 })
    const onPago = abrir()

    await gerar(forma)
    expect(await screen.findByText('Ambiente de teste')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Pagar' }))

    expect(await screen.findByText('Pagamento confirmado!')).toBeInTheDocument()
    expect(onPago).toHaveBeenCalled()
    expect(chamadas.map((c) => `${c.metodo} ${c.caminho}`)).toContain(PAGAR_NO_SANDBOX)
  })

  it('cobrança ainda não emitida mostra o aviso e deixa tentar de novo', async () => {
    backendSandbox(problema(400, 'REQUISICAO_INVALIDA'))
    abrir()

    await gerar('PIX')
    await userEvent.click(await screen.findByRole('button', { name: 'Pagar' }))

    expect(await screen.findByText(/ainda não foi registrado no Asaas/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pagar' })).toBeEnabled()
  })
})
