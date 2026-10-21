import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { Modal, ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { ApiError } from '../../../../services/api'
import { baixarBoletoPdf, buscarMinhaCobranca, pagarCobranca } from '../../../../services/pagamentoAlunoService'
import type {
  CobrancaDoHistorico,
  DadosBoleto,
  DadosPix,
  FormaPagamento,
} from '../../../../types/pagamentoAluno'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { formatarLinhaDigitavel } from '../../formatacao'
import styles from './ModalPagamento.module.css'

/** Com o PIX na tela, de quanto em quanto tempo a tela pergunta se o pagamento caiu. */
const INTERVALO_CONFIRMACAO_MS = 5000
/** Enquanto o provedor gera o PIX ou o boleto, a consulta é mais curta. */
const INTERVALO_PROCESSANDO_MS = 3000
/** Quantas vezes a tela pede de novo enquanto o provedor não termina antes de desistir. */
const MAX_PEDIDOS = 5

const NOME_FORMA: Record<FormaPagamento, string> = { PIX: 'o PIX', BOLETO: 'o boleto' }

type Pronto = { forma: 'PIX'; pix: DadosPix } | { forma: 'BOLETO'; boleto: DadosBoleto; vencimento: string }

type Etapa =
  | { tipo: 'gerando' }
  | { tipo: 'processando' }
  | { tipo: 'pronto'; dados: Pronto }
  | { tipo: 'pago' }
  /** Não dá para pagar por aqui (cancelada, escola sem recebimento online...). */
  | { tipo: 'indisponivel'; mensagem: string }
  /** Falha que vale tentar de novo. */
  | { tipo: 'erro'; mensagem: string }

/** Mensagem da tela para cada recusa do POST /pagar (propriedade `codigo` do backend). */
function mensagemDaRecusa(erro: unknown, forma: FormaPagamento): Etapa {
  if (!(erro instanceof ApiError)) return { tipo: 'erro', mensagem: `Erro inesperado ao gerar ${NOME_FORMA[forma]}.` }
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

/** Dispara o download do PDF no navegador sem abrir nova aba. */
function salvarArquivo(conteudo: Blob, nome: string) {
  const url = URL.createObjectURL(conteudo)
  const link = document.createElement('a')
  link.href = url
  link.download = nome
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Dá tempo de o navegador começar o download antes de liberar a memória.
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Nome do arquivo a partir da descrição: "Mensalidade de março" -> boleto-mensalidade-de-marco.pdf. */
function nomeDoArquivo(descricao: string): string {
  const base = descricao
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `boleto-${base || 'cobranca'}.pdf`
}

type ModalPagamentoProps = {
  cobranca: CobrancaDoHistorico
  forma: FormaPagamento
  /** Pagamento confirmado: a lista recarrega. */
  onPago: () => void
  onFechar: () => void
}

/**
 * Pagar uma cobrança sem sair do Eduka. PIX: QR Code e copia e cola, e a tela
 * fica consultando a cobrança até o provedor confirmar o pagamento. Boleto:
 * linha digitável para copiar e o PDF oficial para baixar; a compensação leva
 * dias úteis, então não há espera na tela. Fechar não cancela nada: o mesmo
 * PIX ou boleto volta ao abrir de novo.
 */
export function ModalPagamento({ cobranca, forma, onPago, onFechar }: ModalPagamentoProps) {
  const [etapa, setEtapa] = useState<Etapa>({ tipo: 'gerando' })
  const [copiado, setCopiado] = useState<'sim' | 'falhou' | null>(null)
  const [verificando, setVerificando] = useState(false)
  const [baixando, setBaixando] = useState(false)
  const [erroDownload, setErroDownload] = useState<string | null>(null)
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
      setEtapa({
        tipo: 'erro',
        mensagem: `O serviço de pagamento está demorando para gerar ${NOME_FORMA[forma]}. Tente de novo.`,
      })
      return
    }
    setEtapa({ tipo: 'gerando' })
    pagarCobranca(cobranca.id, forma)
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
        } else if (erro instanceof ApiError && erro.codigo === 'COBRANCA_NAO_PAGAVEL') {
          void explicarRecusa(erro.detalhe)
        } else {
          setEtapa(mensagemDaRecusa(erro, forma))
        }
      })
  }, [cobranca.id, forma, explicarRecusa])

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
          // O provedor terminou de gerar: o POST agora devolve os dados prontos.
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

  // PIX pronto: espera a confirmação. Boleto pronto: nada a esperar (compensa em dias úteis).
  const aguardaConfirmacao = etapa.tipo === 'pronto' && etapa.dados.forma === 'PIX'

  useEffect(() => {
    if (!aguardaConfirmacao && etapa.tipo !== 'processando') return
    const tipo = etapa.tipo
    const intervalo = aguardaConfirmacao ? INTERVALO_CONFIRMACAO_MS : INTERVALO_PROCESSANDO_MS
    const timer = window.setInterval(() => void consultar(tipo), intervalo)
    return () => window.clearInterval(timer)
  }, [etapa.tipo, aguardaConfirmacao, consultar])

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

  async function baixarPdf() {
    setBaixando(true)
    setErroDownload(null)
    try {
      salvarArquivo(await baixarBoletoPdf(cobranca.id), nomeDoArquivo(cobranca.descricao))
    } catch (erro: unknown) {
      if (montado.current) {
        setErroDownload(
          erro instanceof ApiError && erro.codigo === 'PROVEDOR_INDISPONIVEL'
            ? 'O serviço de pagamento não respondeu. Tente baixar de novo em instantes.'
            : erro instanceof ApiError
              ? (erro.detalhe ?? erro.message)
              : 'Não foi possível baixar o boleto.',
        )
      }
    } finally {
      if (montado.current) setBaixando(false)
    }
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
        {aguardaConfirmacao && (
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
      titulo={forma === 'PIX' ? 'Pagar com PIX' : 'Pagar com boleto'}
      subtitulo={`${cobranca.descricao} · ${formatarMoeda(cobranca.valor)} · vence ${formatarData(cobranca.dataVencimento)}`}
      tamanho="medio"
      onFechar={onFechar}
      rodape={rodape}
    >
      <div className={styles.corpo} aria-live="polite">
        {etapa.tipo === 'gerando' && <ModalAviso>Gerando {NOME_FORMA[forma]}…</ModalAviso>}

        {etapa.tipo === 'processando' && (
          <ModalAviso>
            O serviço de pagamento está preparando {NOME_FORMA[forma]}. Isso costuma levar poucos segundos…
          </ModalAviso>
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

        {etapa.tipo === 'pronto' && etapa.dados.forma === 'PIX' && (
          <>
            <ol className={styles.passos}>
              <li>Abra o app do seu banco e escolha pagar com PIX.</li>
              <li>Leia o QR Code ou use o PIX copia e cola.</li>
              <li>Confirme o pagamento. Esta tela atualiza sozinha quando ele for confirmado.</li>
            </ol>

            <div className={styles.qrCode}>
              <img
                src={`data:image/png;base64,${etapa.dados.pix.qrCodeBase64}`}
                width={220}
                height={220}
                alt="QR Code do PIX para pagar a cobrança"
              />
              {etapa.dados.pix.validade && (
                <span className={styles.validade}>Válido até {formatarValidade(etapa.dados.pix.validade)}</span>
              )}
            </div>

            <CampoCopiar
              id="pix-copia-e-cola"
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
              <li>Baixe o boleto em PDF ou copie o código de barras.</li>
              <li>Pague no app ou site do banco, em lotéricas ou correspondentes bancários.</li>
              <li>O pagamento é confirmado em até 3 dias úteis e aparece aqui em Pagamentos.</li>
            </ol>

            <div className={styles.boletoAcao}>
              <Button variant="primary" icon="download" onClick={() => void baixarPdf()} disabled={baixando}>
                {baixando ? 'Baixando…' : 'Baixar boleto (PDF)'}
              </Button>
              <span className={styles.validade}>Vencimento {formatarData(etapa.dados.vencimento)}</span>
              {erroDownload && <ModalAlerta>{erroDownload}</ModalAlerta>}
            </div>

            {/* O banco aceita só os números; a pontuação é só para leitura. */}
            <CampoCopiar
              id="boleto-linha-digitavel"
              rotulo="Código de barras (linha digitável)"
              valor={formatarLinhaDigitavel(etapa.dados.boleto.linhaDigitavel)}
              valorCopiado={etapa.dados.boleto.linhaDigitavel.replace(/\D/g, '')}
              copiado={copiado}
              onCopiar={copiar}
            />
          </>
        )}
      </div>
    </Modal>
  )
}

type CampoCopiarProps = {
  id: string
  rotulo: string
  /** O que aparece no campo. */
  valor: string
  /** O que vai para a área de transferência; sem ele, o próprio `valor`. */
  valorCopiado?: string
  copiado: 'sim' | 'falhou' | null
  onCopiar: (codigo: string) => Promise<void>
}

/** Código só de leitura com o botão "Copiar" ao lado. */
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
