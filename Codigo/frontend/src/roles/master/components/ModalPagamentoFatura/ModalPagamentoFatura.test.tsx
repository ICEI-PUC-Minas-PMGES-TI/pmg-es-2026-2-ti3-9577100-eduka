import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import type { DadosPagamento, SituacaoPagamento } from '../../../../types/pagamentoAluno'
import { ModalPagamentoFatura } from './ModalPagamentoFatura'

const FATURA: FaturaPlataforma = {
  id: 'fatura-1',
  escolaId: 'escola-1',
  escolaNome: 'Escola Teste',
  competencia: '2026-10',
  descricao: 'Mensalidade Eduka',
  valor: 490,
  dataVencimento: '2026-10-30',
  status: 'PENDENTE',
  situacaoPagamento: 'AGUARDANDO_PAGAMENTO',
  formaPagamento: null,
  pagamento: null,
  acoes: ['PAGAR'],
}

const LINHA_DIGITAVEL = '23793381286000782713695000063305975520000049000'

function pix(): RespostaApi {
  const dados: DadosPagamento = {
    situacao: 'PRONTO',
    forma: 'PIX',
    valor: 490,
    vencimento: '2026-10-30',
    pix: { qrCodeBase64: 'iVBORw0KGgo=', copiaECola: '00020126580014br.gov.bcb.pix', validade: null },
    boleto: null,
  }
  return { corpo: dados }
}

function boleto(): RespostaApi {
  const dados: DadosPagamento = {
    situacao: 'PRONTO',
    forma: 'BOLETO',
    valor: 490,
    vencimento: '2026-10-30',
    pix: null,
    boleto: { linhaDigitavel: LINHA_DIGITAVEL, codigoBarras: '2379' },
  }
  return { corpo: dados }
}

function listaCom(situacaoPagamento: SituacaoPagamento): RespostaApi {
  return { corpo: [{ ...FATURA, situacaoPagamento }] }
}

const PAGAR = 'POST /config-pagamento/faturas/fatura-1/pagar'
const LISTAR = 'GET /config-pagamento/faturas'

/** Responde cada rota com a próxima resposta da fila; a última se repete. */
function backend(rotas: Record<string, RespostaApi[]>) {
  return simularApi(({ metodo, caminho }) => {
    const fila = rotas[`${metodo} ${caminho}`]
    if (!fila) return undefined
    return fila.length > 1 ? fila.shift() : fila[0]
  })
}

/** Deixa passar o intervalo da consulta (5 s) e as promessas que ele dispara. */
async function esperarConsulta() {
  await act(() => vi.advanceTimersByTimeAsync(5000))
}

describe('ModalPagamentoFatura', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  it('mostra o QR Code e o copia e cola do PIX e confirma quando o pagamento cai', async () => {
    const chamadas = backend({ [PAGAR]: [pix()], [LISTAR]: [listaCom('AGUARDANDO_PAGAMENTO'), listaCom('PAGA')] })
    const onPago = vi.fn()
    render(<ModalPagamentoFatura fatura={FATURA} forma="PIX" onPago={onPago} onFechar={vi.fn()} />)

    expect(await screen.findByAltText('QR Code do PIX para pagar a fatura')).toBeInTheDocument()
    expect(screen.getByLabelText('PIX copia e cola')).toHaveValue('00020126580014br.gov.bcb.pix')
    expect(chamadas[0]).toEqual({ metodo: 'POST', caminho: '/config-pagamento/faturas/fatura-1/pagar', corpo: { forma: 'PIX' } })

    await esperarConsulta()
    expect(screen.queryByText('Pagamento confirmado!')).not.toBeInTheDocument()

    await esperarConsulta()
    expect(await screen.findByText('Pagamento confirmado!')).toBeInTheDocument()
    expect(onPago).toHaveBeenCalledTimes(1)
  })

  it('mostra a linha digitável do boleto e copia só os dígitos', async () => {
    backend({ [PAGAR]: [boleto()] })
    const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(<ModalPagamentoFatura fatura={FATURA} forma="BOLETO" onPago={vi.fn()} onFechar={vi.fn()} />)

    const campo = await screen.findByLabelText('Código de barras (linha digitável)')
    expect(campo).toHaveValue('23793.38128 60007.827136 95000.063305 9 75520000049000')

    await usuario.click(screen.getByRole('button', { name: 'Copiar código' }))
    expect(await navigator.clipboard.readText()).toBe(LINHA_DIGITAVEL)
    expect(screen.getByRole('button', { name: 'Copiado!' })).toBeInTheDocument()
  })

  it('espera o provedor terminar de gerar e pede de novo', async () => {
    const chamadas = backend({
      [PAGAR]: [problema(409, 'PAGAMENTO_EM_PROCESSAMENTO'), pix()],
      [LISTAR]: [listaCom('AGUARDANDO_PAGAMENTO')],
    })
    render(<ModalPagamentoFatura fatura={FATURA} forma="PIX" onPago={vi.fn()} onFechar={vi.fn()} />)

    expect(await screen.findByText('Gerando seu pagamento…')).toBeInTheDocument()

    await esperarConsulta()
    expect(await screen.findByAltText('QR Code do PIX para pagar a fatura')).toBeInTheDocument()
    expect(chamadas.filter((c) => c.metodo === 'POST')).toHaveLength(2)
  })

  it('com o provedor fora do ar, avisa e deixa tentar de novo', async () => {
    const chamadas = backend({ [PAGAR]: [problema(503, 'PROVEDOR_INDISPONIVEL'), pix()] })
    const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(<ModalPagamentoFatura fatura={FATURA} forma="PIX" onPago={vi.fn()} onFechar={vi.fn()} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('O serviço de pagamento está fora do ar.')

    await usuario.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(await screen.findByAltText('QR Code do PIX para pagar a fatura')).toBeInTheDocument()
    expect(chamadas.filter((c) => c.metodo === 'POST')).toHaveLength(2)
  })

  it('fatura que não está mais aberta não oferece tentar de novo', async () => {
    backend({ [PAGAR]: [problema(409, 'COBRANCA_NAO_PAGAVEL')] })
    render(<ModalPagamentoFatura fatura={FATURA} forma="BOLETO" onPago={vi.fn()} onFechar={vi.fn()} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta fatura não está mais aberta para pagamento.')
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeInTheDocument()
  })

  it('fatura cancelada enquanto o PIX está na tela para de esperar o pagamento', async () => {
    const onPago = vi.fn()
    backend({ [PAGAR]: [pix()], [LISTAR]: [listaCom('CANCELADA')] })
    render(<ModalPagamentoFatura fatura={FATURA} forma="PIX" onPago={onPago} onFechar={vi.fn()} />)
    await screen.findByAltText('QR Code do PIX para pagar a fatura')

    await esperarConsulta()
    expect(await screen.findByRole('alert')).toHaveTextContent('Esta fatura foi cancelada pelo Eduka.')
    expect(onPago).not.toHaveBeenCalled()
  })
})
