import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { Comprovante, FaturaPlataforma } from '../../../../types/escola/faturaPlataforma'
import type { DadosPagamento, FormaPagamento, SituacaoPagamento } from '../../../../types/financeiro/pagamentoAluno'
import { ModalPagamentoFatura } from './ModalPagamentoFatura'

const FATURA: FaturaPlataforma = {
  id: 'fatura-1',
  escolaId: 'escola-1',
  escolaNome: 'Escola Teste',
  competencia: '2026-10',
  descricao: 'Plano Avançado · outubro/2026',
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

/** A fatura como o GET /config-pagamento/faturas/{id} devolve. */
function faturaCom(situacaoPagamento: SituacaoPagamento): RespostaApi {
  return { corpo: { ...FATURA, situacaoPagamento } }
}

const COMPROVANTE: Comprovante = {
  numero: 'fatura-1',
  beneficiarioNome: 'Eduka',
  beneficiarioDocumento: null,
  pagadorNome: 'Escola Teste',
  pagadorDocumentoMascarado: null,
  descricao: FATURA.descricao,
  vencimento: '2026-10-30',
  valorPago: 490,
  valorEstornado: 0,
  dataPagamento: '2026-10-29',
  forma: 'PIX',
  codigoTransacao: 'pay_fatura',
}

const PAGAR = 'POST /config-pagamento/faturas/fatura-1/pagar'
const CONSULTAR = 'GET /config-pagamento/faturas/fatura-1'
const BUSCAR_COMPROVANTE = 'GET /config-pagamento/faturas/fatura-1/comprovante'

/** Responde cada rota com a próxima resposta da fila; a última se repete. */
function backend(rotas: Record<string, RespostaApi[]>) {
  return simularApi(({ metodo, caminho }) => {
    const fila = rotas[`${metodo} ${caminho}`]
    if (!fila) return undefined
    return fila.length > 1 ? fila.shift() : fila[0]
  })
}

function abrir(onPago = vi.fn()) {
  const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  render(<ModalPagamentoFatura fatura={FATURA} onPago={onPago} onFechar={vi.fn()} />)
  return usuario
}

/** IU17-A: escolhe a forma e clica no comando de pagar. */
async function pagarCom(usuario: ReturnType<typeof userEvent.setup>, forma: FormaPagamento) {
  await usuario.click(screen.getByRole('radio', { name: forma === 'PIX' ? /pix/i : /boleto/i }))
  await usuario.click(screen.getByRole('button', { name: forma === 'PIX' ? 'Pagar com PIX' : 'Gerar boleto' }))
}

/** Deixa passar o intervalo da consulta (5 s) e as promessas que ele dispara. */
async function esperarConsulta() {
  await act(() => vi.advanceTimersByTimeAsync(5000))
}

describe('ModalPagamentoFatura', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  it('abre na escolha de PIX ou boleto, sem pedir nada ao provedor', () => {
    const chamadas = backend({ [PAGAR]: [pix()] })
    abrir()

    expect(screen.getByRole('heading', { name: 'Pagar fatura' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /pix/i })).toBeChecked()
    expect(screen.getByText('R$ 490,00')).toBeInTheDocument()
    expect(chamadas).toHaveLength(0)
  })

  it('PIX: QR Code e copia e cola; confirmado, mostra o comprovante', async () => {
    const chamadas = backend({
      [PAGAR]: [pix()],
      [CONSULTAR]: [faturaCom('AGUARDANDO_PAGAMENTO'), faturaCom('PAGA')],
      [BUSCAR_COMPROVANTE]: [{ corpo: COMPROVANTE }],
    })
    const onPago = vi.fn()
    const usuario = abrir(onPago)

    await pagarCom(usuario, 'PIX')

    expect(await screen.findByAltText('QR Code do PIX para pagar a fatura')).toBeInTheDocument()
    expect(screen.getByLabelText('PIX copia e cola')).toHaveValue('00020126580014br.gov.bcb.pix')
    expect(chamadas[0]).toEqual({ metodo: 'POST', caminho: '/config-pagamento/faturas/fatura-1/pagar', corpo: { forma: 'PIX' } })

    await esperarConsulta()
    expect(screen.queryByText('Pagamento confirmado!')).not.toBeInTheDocument()

    await esperarConsulta()
    expect(await screen.findByText('Pagamento confirmado!')).toBeInTheDocument()
    expect(await screen.findByText('pay_fatura')).toBeInTheDocument()
    expect(onPago).toHaveBeenCalledTimes(1)
  })

  it('boleto: linha digitável para copiar, sem PDF (a fatura não tem)', async () => {
    backend({ [PAGAR]: [boleto()], [CONSULTAR]: [faturaCom('AGUARDANDO_PAGAMENTO')] })
    const usuario = abrir()

    await pagarCom(usuario, 'BOLETO')

    const campo = await screen.findByLabelText('Código de barras (linha digitável)')
    expect(campo).toHaveValue('23793.38128 60007.827136 95000.063305 9 75520000049000')
    expect(screen.queryByRole('button', { name: /baixar boleto/i })).not.toBeInTheDocument()
    expect(screen.getByText(/aparece em Faturas anteriores/)).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Copiar linha' }))
    expect(await navigator.clipboard.readText()).toBe(LINHA_DIGITAVEL)
  })

  it('espera o provedor terminar de gerar e pede de novo', async () => {
    const chamadas = backend({
      [PAGAR]: [problema(409, 'PAGAMENTO_EM_PROCESSAMENTO'), pix()],
      [CONSULTAR]: [faturaCom('AGUARDANDO_PAGAMENTO')],
    })
    const usuario = abrir()

    await pagarCom(usuario, 'PIX')
    expect(await screen.findByText(/está preparando o PIX/)).toBeInTheDocument()

    await esperarConsulta()
    expect(await screen.findByAltText('QR Code do PIX para pagar a fatura')).toBeInTheDocument()
    expect(chamadas.filter((c) => c.metodo === 'POST')).toHaveLength(2)
  })

  it('com o provedor fora do ar, avisa e deixa tentar de novo', async () => {
    const chamadas = backend({ [PAGAR]: [problema(503, 'PROVEDOR_INDISPONIVEL'), pix()] })
    const usuario = abrir()

    await pagarCom(usuario, 'PIX')
    expect(await screen.findByRole('alert')).toHaveTextContent('O serviço de pagamento não respondeu.')

    await usuario.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(await screen.findByAltText('QR Code do PIX para pagar a fatura')).toBeInTheDocument()
    expect(chamadas.filter((c) => c.metodo === 'POST')).toHaveLength(2)
  })

  it('fatura que não está mais aberta não oferece tentar de novo', async () => {
    backend({ [PAGAR]: [problema(409, 'COBRANCA_NAO_PAGAVEL')], [CONSULTAR]: [faturaCom('AGUARDANDO_PAGAMENTO')] })
    const usuario = abrir()

    await pagarCom(usuario, 'BOLETO')

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta fatura não está mais aberta para pagamento.')
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeInTheDocument()
  })

  it('fatura cancelada enquanto o PIX está na tela para de esperar o pagamento', async () => {
    const onPago = vi.fn()
    backend({ [PAGAR]: [pix()], [CONSULTAR]: [faturaCom('CANCELADA')] })
    const usuario = abrir(onPago)

    await pagarCom(usuario, 'PIX')
    await screen.findByAltText('QR Code do PIX para pagar a fatura')

    await esperarConsulta()
    expect(await screen.findByRole('alert')).toHaveTextContent('Esta fatura foi cancelada pelo Eduka.')
    expect(onPago).not.toHaveBeenCalled()
  })

  describe('no ambiente de teste', () => {
    const PAGAR_NO_SANDBOX = 'POST /config-pagamento/faturas/fatura-1/sandbox/pagar'

    it.each(['PIX', 'BOLETO'] as const)('Pagar com %s no Sandbox confirma o pagamento na hora', async (forma) => {
      const chamadas = backend({
        [PAGAR]: [forma === 'PIX' ? pix() : boleto()],
        [PAGAR_NO_SANDBOX]: [{ status: 202 }],
        [CONSULTAR]: [faturaCom('PAGA')],
        [BUSCAR_COMPROVANTE]: [{ corpo: COMPROVANTE }],
      })
      const onPago = vi.fn()
      const usuario = abrir(onPago)

      await pagarCom(usuario, forma)
      expect(await screen.findByText('Ambiente de teste')).toBeInTheDocument()
      await usuario.click(screen.getByRole('button', { name: 'Pagar' }))

      expect(await screen.findByText('Pagamento confirmado!')).toBeInTheDocument()
      expect(onPago).toHaveBeenCalled()
      expect(chamadas.map((c) => `${c.metodo} ${c.caminho}`)).toContain(PAGAR_NO_SANDBOX)
    })
  })
})
