import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { Modal, ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { ApiError } from '../../../../services/api'
import { buscarMinhaFatura, pagarFatura } from '../../../../services/faturaPlataformaService'
import {
  atalhosSandboxHabilitados,
  mensagemErroSandbox,
  pagarFaturaNoSandbox,
} from '../../../../services/sandboxService'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import type { DadosBoleto, DadosPix, FormaPagamento } from '../../../../types/pagamentoAluno'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarLinhaDigitavel } from '../../../aluno/formatacao'
// Mesmo visual do pagamento do aluno (QR Code, copia e cola, passos).
import styles from '../../../aluno/components/ModalPagamento/ModalPagamento.module.css'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'

/** Com o PIX na tela, de quanto em quanto tempo a tela pergunta se o pagamento caiu. */
const INTERVALO_CONFIRMACAO_MS = 5000
/** Enquanto o provedor gera o PIX ou o boleto. */
const INTERVALO_PROCESSANDO_MS = 5000
/** Quantas vezes a tela pede de novo enquanto o provedor não termina (spec §11.2). */
const MAX_PEDIDOS = 6
/** Por quanto tempo a tela espera a confirmação do PIX (spec §11.2). */
const ESPERA_MAXIMA_MS = 10 * 60 * 1000

const NOME_FORMA: Record<FormaPagamento, string> = { PIX: 'o PIX', BOLETO: 'o boleto' }

type Pronto = { forma: 'PIX'; pix: DadosPix } | { forma: 'BOLETO'; boleto: DadosBoleto; vencimento: string }

type Etapa =
  | { tipo: 'gerando' }
  | { tipo: 'processando' }
  | { tipo: 'pronto'; dados: Pronto }
  | { tipo: 'pago' }
  | { tipo: 'indisponivel'; mensagem: string }
  | { tipo: 'erro'; mensagem: string }

function mensagemDaRecusa(erro: unknown, forma: FormaPagamento): Etapa {
  if (!(erro instanceof ApiError)) return { tipo: 'erro', mensagem: `Erro inesperado ao gerar ${NOME_FORMA[forma]}.` }
  switch (erro.codigo) {
    case 'COBRANCA_NAO_PAGAVEL':
      return { tipo: 'indisponivel', mensagem: 'Esta fatura não está mais aberta para pagamento.' }
    case 'PROVEDOR_NAO_CONFIGURADO':
      return { tipo: 'indisponivel', mensagem: 'Pagamentos online indisponíveis no momento.' }
    case 'PROVEDOR_INDISPONIVEL':
      return { tipo: 'erro', mensagem: 'O serviço de pagamento está fora do ar. Tente de novo em alguns minutos.' }
    case 'PROVEDOR_RECUSOU':
      return {
        tipo: 'erro',
        mensagem: erro.detalhe ?? 'O serviço de pagamento recusou a cobrança. Tente de novo mais tarde.',
      }
    default:
      return { tipo: 'erro', mensagem: erro.detalhe ?? erro.message }
  }
}

type ModalPagamentoFaturaProps = {
  fatura: FaturaPlataforma
  forma: FormaPagamento
  /** Pagamento confirmado: a lista recarrega. */
  onPago: () => void
  onFechar: () => void
}

/**
 * Pagar a fatura do Eduka sem sair da tela. PIX: QR Code e copia e cola, e a
 * tela consulta as faturas até o provedor confirmar. Boleto: linha digitável
 * para copiar. Fechar não cancela nada: o mesmo PIX ou boleto volta ao abrir
 * de novo.
 */
export function ModalPagamentoFatura({ fatura, forma, onPago, onFechar }: ModalPagamentoFaturaProps) {
  const [etapa, setEtapa] = useState<Etapa>({ tipo: 'gerando' })
  const [copiado, setCopiado] = useState<'sim' | 'falhou' | null>(null)
  const [pagandoNoSandbox, setPagandoNoSandbox] = useState(false)
  const [pagoNoSandbox, setPagoNoSandbox] = useState(false)
  const [erroSandbox, setErroSandbox] = useState<string | null>(null)
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

  const gerar = useCallback(() => {
    pedidos.current += 1
    if (pedidos.current > MAX_PEDIDOS) {
      pedidos.current = 0
      setEtapa({ tipo: 'erro', mensagem: 'Tente de novo em alguns minutos.' })
      return
    }
    setEtapa({ tipo: 'gerando' })
    pagarFatura(fatura.id, forma)
      .then((dados) => {
        if (!montado.current) return
        if (dados.situacao === 'PRONTO' && dados.pix) {
          setEtapa({ tipo: 'pronto', dados: { forma: 'PIX', pix: dados.pix } })
        } else if (dados.situacao === 'PRONTO' && dados.boleto) {
          setEtapa({ tipo: 'pronto', dados: { forma: 'BOLETO', boleto: dados.boleto, vencimento: dados.vencimento } })
        } else {
          setEtapa({ tipo: 'processando' })
        }
      })
      .catch((erro: unknown) => {
        if (!montado.current) return
        if (erro instanceof ApiError && erro.codigo === 'PAGAMENTO_EM_PROCESSAMENTO') {
          setEtapa({ tipo: 'processando' })
        } else {
          setEtapa(mensagemDaRecusa(erro, forma))
        }
      })
  }, [fatura.id, forma])

  useEffect(() => {
    gerar()
  }, [gerar])

  /** Pergunta ao backend em que pé está a fatura. Uma consulta por vez. */
  const consultar = useCallback(
    async (etapaAtual: Etapa['tipo']) => {
      if (consultando.current || document.visibilityState !== 'visible') return
      consultando.current = true
      try {
        // Só esta fatura, não a lista da escola: a consulta se repete a cada poucos segundos.
        const atual = await buscarMinhaFatura(fatura.id)
        if (!montado.current) return
        if (atual.situacaoPagamento === 'PAGA') {
          setEtapa({ tipo: 'pago' })
          onPagoRef.current()
        } else if (atual.situacaoPagamento === 'CANCELADA') {
          setEtapa({ tipo: 'indisponivel', mensagem: 'Esta fatura foi cancelada pelo Eduka.' })
        } else if (etapaAtual === 'processando' && atual.situacaoPagamento === 'AGUARDANDO_PAGAMENTO') {
          // O provedor terminou de gerar: o POST agora devolve os dados prontos.
          gerar()
        }
      } catch {
        // Falha de rede numa consulta: a próxima tenta de novo.
      } finally {
        consultando.current = false
      }
    },
    [fatura.id, gerar],
  )

  // PIX pronto: espera a confirmação. Boleto: só se tiver sido pago no Sandbox.
  const aguardaConfirmacao = etapa.tipo === 'pronto' && (etapa.dados.forma === 'PIX' || pagoNoSandbox)

  useEffect(() => {
    if (!aguardaConfirmacao && etapa.tipo !== 'processando') return
    const tipo = etapa.tipo
    const inicio = Date.now()
    const timer = window.setInterval(
      () => {
        if (Date.now() - inicio > ESPERA_MAXIMA_MS) {
          window.clearInterval(timer)
          return
        }
        void consultar(tipo)
      },
      aguardaConfirmacao ? INTERVALO_CONFIRMACAO_MS : INTERVALO_PROCESSANDO_MS,
    )
    return () => window.clearInterval(timer)
  }, [etapa.tipo, aguardaConfirmacao, consultar])

  /**
   * Ambiente de teste: paga no Sandbox como se a escola tivesse pago no banco.
   * O backend já grava o pagamento; a consulta logo em seguida mostra "pago".
   */
  async function pagarNoSandbox() {
    setPagandoNoSandbox(true)
    setErroSandbox(null)
    try {
      await pagarFaturaNoSandbox(fatura.id)
      if (!montado.current) return
      setPagoNoSandbox(true)
      await consultar(etapa.tipo)
    } catch (e) {
      if (montado.current) {
        setErroSandbox(
          mensagemErroSandbox(e, 'O pagamento ainda não foi registrado no Asaas. Tente de novo em instantes.'),
        )
      }
    } finally {
      if (montado.current) setPagandoNoSandbox(false)
    }
  }

  async function copiar(codigo: string) {
    try {
      await navigator.clipboard.writeText(codigo)
      setCopiado('sim')
    } catch {
      setCopiado('falhou')
    }
    window.setTimeout(() => {
      if (montado.current) setCopiado(null)
    }, 3000)
  }

  return (
    <Modal
      titulo={forma === 'PIX' ? 'Pagar fatura com PIX' : 'Pagar fatura com boleto'}
      subtitulo={`${fatura.descricao} · ${formatarMoeda(fatura.valor)} · vence ${formatarData(fatura.dataVencimento)}`}
      tamanho="medio"
      onFechar={onFechar}
      rodape={
        etapa.tipo === 'pago' ? (
          <Button variant="primary" onClick={onFechar}>
            Concluir
          </Button>
        ) : (
          <>
            {etapa.tipo === 'erro' && (
              <Button
                variant="outline"
                icon="arrowRight"
                onClick={() => {
                  pedidos.current = 0
                  gerar()
                }}
              >
                Tentar de novo
              </Button>
            )}
            <Button variant="primary" onClick={onFechar}>
              Fechar
            </Button>
          </>
        )
      }
    >
      <div className={styles.corpo} aria-live="polite">
        {etapa.tipo === 'gerando' && <ModalAviso>Gerando {NOME_FORMA[forma]}…</ModalAviso>}
        {etapa.tipo === 'processando' && <ModalAviso>Gerando seu pagamento…</ModalAviso>}
        {(etapa.tipo === 'erro' || etapa.tipo === 'indisponivel') && <ModalAlerta>{etapa.mensagem}</ModalAlerta>}

        {etapa.tipo === 'pago' && (
          <div className={styles.pago}>
            <span className={styles.pagoIcone}>
              <Icon name="check" size={28} strokeWidth={3} />
            </span>
            <strong>Pagamento confirmado!</strong>
            <span>A fatura de {fatura.competencia} está paga. O comprovante fica disponível na lista.</span>
          </div>
        )}

        {etapa.tipo === 'pronto' && etapa.dados.forma === 'PIX' && (
          <>
            <ol className={styles.passos}>
              <li>Abra o app do banco da escola e escolha pagar com PIX.</li>
              <li>Leia o QR Code ou use o PIX copia e cola.</li>
              <li>Confirme o pagamento. Esta tela atualiza sozinha quando ele for confirmado.</li>
            </ol>
            <div className={styles.qrCode}>
              <img
                src={`data:image/png;base64,${etapa.dados.pix.qrCodeBase64}`}
                width={220}
                height={220}
                alt="QR Code do PIX para pagar a fatura"
              />
            </div>
            <CampoCopiar
              id="fatura-pix-copia-e-cola"
              rotulo="PIX copia e cola"
              valor={etapa.dados.pix.copiaECola}
              copiado={copiado}
              onCopiar={copiar}
            />
            <p className={styles.aguardando}>
              <span className={styles.pulso} aria-hidden="true" />
              Aguardando a confirmação do pagamento…
            </p>
          </>
        )}

        {etapa.tipo === 'pronto' && etapa.dados.forma === 'BOLETO' && (
          <>
            <ol className={styles.passos}>
              <li>Copie o código de barras.</li>
              <li>Pague no app ou site do banco até {formatarData(etapa.dados.vencimento)}.</li>
              <li>O pagamento é confirmado em até 3 dias úteis e aparece aqui.</li>
            </ol>
            <CampoCopiar
              id="fatura-boleto-linha-digitavel"
              rotulo="Código de barras (linha digitável)"
              valor={formatarLinhaDigitavel(etapa.dados.boleto.linhaDigitavel)}
              valorCopiado={etapa.dados.boleto.linhaDigitavel.replace(/\D/g, '')}
              copiado={copiado}
              onCopiar={copiar}
            />
          </>
        )}

        {etapa.tipo === 'pronto' && atalhosSandboxHabilitados && (
          <div className={styles.sandbox}>
            <span className={styles.sandboxSelo}>Ambiente de teste</span>
            <p>
              Ninguém paga de verdade aqui. Clique em Pagar para simular no Asaas o pagamento {NOME_FORMA[forma]},
              como se a escola tivesse pago no banco. O valor cai na conta do Eduka.
            </p>
            {pagoNoSandbox ? (
              <span className={styles.validade}>Pagamento enviado ao Asaas. Aguardando a confirmação…</span>
            ) : (
              <Button variant="accent" icon="check" onClick={() => void pagarNoSandbox()} disabled={pagandoNoSandbox}>
                {pagandoNoSandbox ? 'Pagando…' : 'Pagar'}
              </Button>
            )}
            {erroSandbox && <ModalAlerta>{erroSandbox}</ModalAlerta>}
          </div>
        )}
      </div>
    </Modal>
  )
}

type CampoCopiarProps = {
  id: string
  rotulo: string
  valor: string
  valorCopiado?: string
  copiado: 'sim' | 'falhou' | null
  onCopiar: (codigo: string) => Promise<void>
}

function CampoCopiar({ id, rotulo, valor, valorCopiado, copiado, onCopiar }: CampoCopiarProps) {
  return (
    <div className={styles.copiaECola}>
      <label htmlFor={id} className={styles.rotulo}>
        {rotulo}
      </label>
      <div className={styles.copiaLinha}>
        <input id={id} className={styles.codigo} value={valor} readOnly onFocus={(e) => e.currentTarget.select()} />
        <Button
          size="sm"
          variant="accent"
          icon={copiado === 'sim' ? 'check' : undefined}
          onClick={() => void onCopiar(valorCopiado ?? valor)}
        >
          {copiado === 'sim' ? 'Copiado!' : 'Copiar código'}
        </Button>
      </div>
      {copiado === 'falhou' && (
        <span className={styles.dica}>Não foi possível copiar. Selecione o código acima e copie manualmente.</span>
      )}
    </div>
  )
}
