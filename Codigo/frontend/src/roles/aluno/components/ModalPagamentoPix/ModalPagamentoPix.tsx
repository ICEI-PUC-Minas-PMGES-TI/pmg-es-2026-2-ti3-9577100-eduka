import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { Modal, ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { ApiError } from '../../../../services/api'
import { buscarMinhaCobranca, pagarComPix } from '../../../../services/pagamentoAlunoService'
import type { CobrancaDoHistorico, DadosPix } from '../../../../types/pagamentoAluno'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import styles from './ModalPagamentoPix.module.css'

/** Com o PIX na tela, de quanto em quanto tempo a tela pergunta se o pagamento caiu. */
const INTERVALO_CONFIRMACAO_MS = 5000
/** Enquanto o provedor gera o PIX, a consulta é mais curta. */
const INTERVALO_PROCESSANDO_MS = 3000
/** Quantas vezes a tela pede o PIX de novo enquanto o provedor não termina antes de desistir. */
const MAX_PEDIDOS = 5

type Etapa =
  | { tipo: 'gerando' }
  | { tipo: 'processando' }
  | { tipo: 'pronto'; pix: DadosPix }
  | { tipo: 'pago' }
  /** Não dá para pagar por aqui (cancelada, escola sem recebimento online...). */
  | { tipo: 'indisponivel'; mensagem: string }
  /** Falha que vale tentar de novo. */
  | { tipo: 'erro'; mensagem: string }

/** Mensagem da tela para cada recusa do POST /pagar (propriedade `codigo` do backend). */
function mensagemDaRecusa(erro: unknown): Etapa {
  if (!(erro instanceof ApiError)) return { tipo: 'erro', mensagem: 'Erro inesperado ao gerar o PIX.' }
  switch (erro.codigo) {
    case 'PAGADOR_SEM_CPF':
      return {
        tipo: 'indisponivel',
        mensagem: 'Falta o CPF de quem paga no cadastro. Peça à secretaria da escola para completar.',
      }
    case 'RECEBIMENTOS_INDISPONIVEIS':
      return {
        tipo: 'indisponivel',
        mensagem: 'A escola ainda não recebe pagamentos pelo Eduka. Fale com a secretaria para pagar.',
      }
    case 'PROVEDOR_NAO_CONFIGURADO':
      return { tipo: 'indisponivel', mensagem: 'Pagamentos online indisponíveis no momento.' }
    case 'PROVEDOR_INDISPONIVEL':
      return { tipo: 'erro', mensagem: 'O serviço de pagamento não respondeu. Tente de novo em instantes.' }
    case 'PROVEDOR_RECUSOU':
      return {
        tipo: 'erro',
        mensagem: erro.detalhe ?? 'O serviço de pagamento recusou a cobrança. Tente de novo mais tarde.',
      }
    default:
      return { tipo: 'erro', mensagem: erro.detalhe ?? erro.message }
  }
}

function formatarValidade(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

type ModalPagamentoPixProps = {
  cobranca: CobrancaDoHistorico
  /** Pagamento confirmado: a lista recarrega. */
  onPago: () => void
  onFechar: () => void
}

/**
 * Pagar uma cobrança com PIX sem sair do Eduka: gera (ou reaproveita) o QR
 * Code e o copia e cola e fica consultando a cobrança até o provedor confirmar
 * o pagamento. Fechar não cancela nada: o mesmo PIX volta ao clicar em Pagar
 * de novo.
 */
export function ModalPagamentoPix({ cobranca, onPago, onFechar }: ModalPagamentoPixProps) {
  const [etapa, setEtapa] = useState<Etapa>({ tipo: 'gerando' })
  const [copiado, setCopiado] = useState<'sim' | 'falhou' | null>(null)
  const [verificando, setVerificando] = useState(false)
  const montado = useRef(true)
  const consultando = useRef(false)
  const pedidos = useRef(0)
  const onPagoRef = useRef(onPago)

  useEffect(() => {
    onPagoRef.current = onPago
  }, [onPago])

  useEffect(() => {
    montado.current = true
    return () => {
      montado.current = false
    }
  }, [])

  /** Recusa por "não pagável": a cobrança mudou desde que a lista abriu. A consulta diz por quê. */
  const explicarRecusa = useCallback(
    async (detalhe: string | null) => {
      try {
        const atual = await buscarMinhaCobranca(cobranca.id)
        if (!montado.current) return
        if (atual.situacaoPagamento === 'PAGA') {
          setEtapa({ tipo: 'pago' })
          onPagoRef.current()
          return
        }
        setEtapa({
          tipo: 'indisponivel',
          mensagem:
            atual.situacaoPagamento === 'CANCELADA'
              ? 'Esta cobrança foi cancelada pela escola.'
              : (detalhe ?? 'Esta cobrança não pode ser paga online. Fale com a secretaria.'),
        })
      } catch {
        if (montado.current) setEtapa({ tipo: 'erro', mensagem: 'Não foi possível consultar a cobrança.' })
      }
    },
    [cobranca.id],
  )

  const gerar = useCallback(() => {
    pedidos.current += 1
    if (pedidos.current > MAX_PEDIDOS) {
      pedidos.current = 0
      setEtapa({ tipo: 'erro', mensagem: 'O serviço de pagamento está demorando para gerar o PIX. Tente de novo.' })
      return
    }
    setEtapa({ tipo: 'gerando' })
    pagarComPix(cobranca.id)
      .then((dados) => {
        if (!montado.current) return
        setEtapa(dados.situacao === 'PRONTO' && dados.pix ? { tipo: 'pronto', pix: dados.pix } : { tipo: 'processando' })
      })
      .catch((erro: unknown) => {
        if (!montado.current) return
        if (erro instanceof ApiError && erro.codigo === 'PAGAMENTO_EM_PROCESSAMENTO') {
          setEtapa({ tipo: 'processando' })
        } else if (erro instanceof ApiError && erro.codigo === 'COBRANCA_NAO_PAGAVEL') {
          void explicarRecusa(erro.detalhe)
        } else {
          setEtapa(mensagemDaRecusa(erro))
        }
      })
  }, [cobranca.id, explicarRecusa])

  useEffect(() => {
    gerar()
  }, [gerar])

  function tentarDeNovo() {
    pedidos.current = 0
    gerar()
  }

  /** Pergunta ao backend em que pé está o pagamento. Uma consulta por vez. */
  const consultar = useCallback(
    async (etapaAtual: Etapa['tipo']) => {
      if (consultando.current) return
      consultando.current = true
      try {
        const atual = await buscarMinhaCobranca(cobranca.id)
        if (!montado.current) return
        if (atual.situacaoPagamento === 'PAGA') {
          setEtapa({ tipo: 'pago' })
          onPagoRef.current()
        } else if (atual.situacaoPagamento === 'CANCELADA') {
          setEtapa({ tipo: 'indisponivel', mensagem: 'Esta cobrança foi cancelada pela escola.' })
        } else if (etapaAtual === 'processando' && atual.situacaoPagamento === 'AGUARDANDO_PAGAMENTO') {
          // O provedor terminou de gerar: o POST agora devolve o PIX pronto.
          gerar()
        }
      } catch {
        // Falha de rede numa consulta: a próxima tenta de novo.
      } finally {
        consultando.current = false
      }
    },
    [cobranca.id, gerar],
  )

  useEffect(() => {
    if (etapa.tipo !== 'pronto' && etapa.tipo !== 'processando') return
    const tipo = etapa.tipo
    const intervalo = tipo === 'pronto' ? INTERVALO_CONFIRMACAO_MS : INTERVALO_PROCESSANDO_MS
    const timer = window.setInterval(() => void consultar(tipo), intervalo)
    return () => window.clearInterval(timer)
  }, [etapa.tipo, consultar])

  async function verificarAgora() {
    setVerificando(true)
    await consultar(etapa.tipo)
    if (montado.current) setVerificando(false)
  }

  async function copiar(codigo: string) {
    try {
      await navigator.clipboard.writeText(codigo)
      setCopiado('sim')
    } catch {
      // Sem permissão de área de transferência: o código fica selecionável no campo.
      setCopiado('falhou')
    }
    window.setTimeout(() => {
      if (montado.current) setCopiado(null)
    }, 3000)
  }

  const rodape =
    etapa.tipo === 'pago' ? (
      <Button variant="primary" onClick={onFechar}>
        Concluir
      </Button>
    ) : (
      <>
        {etapa.tipo === 'erro' && (
          <Button variant="outline" icon="arrowRight" onClick={tentarDeNovo}>
            Tentar de novo
          </Button>
        )}
        {etapa.tipo === 'pronto' && (
          <Button variant="outline" icon="clock" onClick={() => void verificarAgora()} disabled={verificando}>
            {verificando ? 'Verificando…' : 'Já paguei'}
          </Button>
        )}
        <Button variant="primary" onClick={onFechar}>
          Fechar
        </Button>
      </>
    )

  return (
    <Modal
      titulo="Pagar com PIX"
      subtitulo={`${cobranca.descricao} · ${formatarMoeda(cobranca.valor)} · vence ${formatarData(cobranca.dataVencimento)}`}
      tamanho="medio"
      onFechar={onFechar}
      rodape={rodape}
    >
      <div className={styles.corpo} aria-live="polite">
        {etapa.tipo === 'gerando' && <ModalAviso>Gerando o PIX…</ModalAviso>}

        {etapa.tipo === 'processando' && (
          <ModalAviso>O serviço de pagamento está preparando o PIX. Isso costuma levar poucos segundos…</ModalAviso>
        )}

        {etapa.tipo === 'erro' && <ModalAlerta>{etapa.mensagem}</ModalAlerta>}

        {etapa.tipo === 'indisponivel' && <ModalAlerta>{etapa.mensagem}</ModalAlerta>}

        {etapa.tipo === 'pago' && (
          <div className={styles.pago}>
            <span className={styles.pagoIcone}>
              <Icon name="check" size={28} strokeWidth={3} />
            </span>
            <strong>Pagamento confirmado!</strong>
            <span>
              {cobranca.descricao} está paga. O comprovante da baixa também vai para o e-mail cadastrado.
            </span>
          </div>
        )}

        {etapa.tipo === 'pronto' && (
          <>
            <ol className={styles.passos}>
              <li>Abra o app do seu banco e escolha pagar com PIX.</li>
              <li>Leia o QR Code ou use o PIX copia e cola.</li>
              <li>Confirme o pagamento. Esta tela atualiza sozinha quando ele for confirmado.</li>
            </ol>

            <div className={styles.qrCode}>
              <img
                src={`data:image/png;base64,${etapa.pix.qrCodeBase64}`}
                width={220}
                height={220}
                alt="QR Code do PIX para pagar a cobrança"
              />
              {etapa.pix.validade && (
                <span className={styles.validade}>Válido até {formatarValidade(etapa.pix.validade)}</span>
              )}
            </div>

            <div className={styles.copiaECola}>
              <label htmlFor="pix-copia-e-cola" className={styles.rotulo}>
                PIX copia e cola
              </label>
              <div className={styles.copiaLinha}>
                <input
                  id="pix-copia-e-cola"
                  className={styles.codigo}
                  value={etapa.pix.copiaECola}
                  readOnly
                  onFocus={(e) => e.currentTarget.select()}
                />
                <Button
                  size="sm"
                  variant="accent"
                  icon={copiado === 'sim' ? 'check' : undefined}
                  onClick={() => void copiar(etapa.pix.copiaECola)}
                >
                  {copiado === 'sim' ? 'Copiado!' : 'Copiar'}
                </Button>
              </div>
              {copiado === 'falhou' && (
                <span className={styles.dica}>Não foi possível copiar. Selecione o código acima e copie manualmente.</span>
              )}
            </div>

            <p className={styles.aguardando}>
              <span className={styles.pulso} aria-hidden="true" />
              Aguardando a confirmação do pagamento…
            </p>
          </>
        )}
      </div>
    </Modal>
  )
}
