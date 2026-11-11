import { useCallback, useEffect, useRef, useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { Icon } from '../../../../components/Icon/Icon'
import { Modal, ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { ApiError } from '../../../../services/api'
import {
  baixarBoletoPdf,
  buscarComprovante,
  buscarMinhaCobranca,
  pagarCobranca,
} from '../../../../services/pagamentoAlunoService'
import {
  atalhosSandboxHabilitados,
  mensagemErroSandbox,
  pagarNoSandboxComoAluno,
} from '../../../../services/sandboxService'
import type {
  CobrancaDoHistorico,
  DadosBoleto,
  DadosPix,
  FormaPagamento,
} from '../../../../types/pagamentoAluno'
import { salvarArquivo } from '../../../../utils/arquivos'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { formatarLinhaDigitavel } from '../../formatacao'
import styles from './ModalPagamento.module.css'

/** Com o PIX na tela, de quanto em quanto tempo a tela pergunta se o pagamento caiu. */
const INTERVALO_CONFIRMACAO_MS = 5000
/** O boleto compensa em dias úteis: com ele na tela, a consulta é bem mais espaçada. */
const INTERVALO_BOLETO_MS = 30000
/** Enquanto o provedor gera o PIX ou o boleto, a consulta é mais curta. */
const INTERVALO_PROCESSANDO_MS = 3000
/** Quantas vezes a tela pede de novo enquanto o provedor não termina antes de desistir. */
const MAX_PEDIDOS = 5

const NOME_FORMA: Record<FormaPagamento, string> = { PIX: 'o PIX', BOLETO: 'o boleto' }

const OUTRA_FORMA: Record<FormaPagamento, FormaPagamento> = { PIX: 'BOLETO', BOLETO: 'PIX' }

/** Comando da escolha (IU17-A): muda conforme a forma. */
const ROTULO_GERAR: Record<FormaPagamento, string> = { PIX: 'Pagar com PIX', BOLETO: 'Gerar boleto' }

type Pronto = { forma: 'PIX'; pix: DadosPix } | { forma: 'BOLETO'; boleto: DadosBoleto; vencimento: string }

type Etapa =
  /** O aluno escolhe PIX ou boleto e clica em Pagar. Nada foi pedido ao provedor ainda. */
  | { tipo: 'escolhendo' }
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

const OPCOES_FORMA: { forma: FormaPagamento; titulo: string; descricao: string; icone: 'card' | 'fileText' }[] = [
  {
    forma: 'PIX',
    titulo: 'PIX',
    descricao: 'QR Code ou copia e cola. Confirmação na hora.',
    icone: 'card',
  },
  {
    forma: 'BOLETO',
    titulo: 'Boleto',
    descricao: 'PDF e código de barras. Confirmação em até 3 dias úteis.',
    icone: 'fileText',
  },
]

type ModalPagamentoProps = {
  cobranca: CobrancaDoHistorico
  /** Pagamento confirmado: a lista recarrega. */
  onPago: () => void
  onFechar: () => void
}

/**
 * Pagar uma cobrança sem sair do Eduka: o aluno escolhe PIX ou boleto e clica
 * em Pagar. PIX: QR Code e copia e cola, e a tela fica consultando a cobrança
 * até o provedor confirmar o pagamento. Boleto: linha digitável para copiar e
 * o PDF oficial para baixar; a compensação leva dias úteis, então não há
 * espera na tela. Tudo vem pela API do Eduka: nenhum link leva à página do
 * provedor. Fechar não cancela nada: o mesmo PIX ou boleto volta ao abrir de
 * novo, e trocar a forma altera a mesma cobrança.
 */
export function ModalPagamento({ cobranca, onPago, onFechar }: ModalPagamentoProps) {
  const [etapa, setEtapa] = useState<Etapa>({ tipo: 'escolhendo' })
  const [forma, setForma] = useState<FormaPagamento>('PIX')
  const [copiado, setCopiado] = useState<'sim' | 'falhou' | null>(null)
  const [verificando, setVerificando] = useState(false)
  const [baixando, setBaixando] = useState(false)
  const [erroDownload, setErroDownload] = useState<string | null>(null)
  const [pagandoNoSandbox, setPagandoNoSandbox] = useState(false)
  const [pagoNoSandbox, setPagoNoSandbox] = useState(false)
  const [erroSandbox, setErroSandbox] = useState<string | null>(null)
  /** Escola que recebe o dinheiro, quando o backend informa. */
  const [recebedor, setRecebedor] = useState<string | null>(null)
  const [pixVencido, setPixVencido] = useState(false)
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

  const gerar = useCallback((forma: FormaPagamento) => {
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
        if (dados.recebedorNome) setRecebedor(dados.recebedorNome)
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
  }, [cobranca.id, explicarRecusa])

  /** Botão "Pagar": só aqui o PIX ou o boleto é pedido ao provedor. */
  function pagar() {
    pedidos.current = 0
    gerar(forma)
  }

  function tentarDeNovo() {
    pedidos.current = 0
    gerar(forma)
  }

  function limparEtapaAnterior() {
    setCopiado(null)
    setErroDownload(null)
    setErroSandbox(null)
    setPagoNoSandbox(false)
    setPixVencido(false)
  }

  /** Depois de uma falha, volta à escolha. O próximo Pagar troca a forma na mesma cobrança. */
  function trocarForma() {
    limparEtapaAnterior()
    setEtapa({ tipo: 'escolhendo' })
  }

  /** "Trocar para boleto" / "Trocar para PIX": altera a forma da mesma cobrança no provedor, sem criar outra. */
  function trocarPara(outra: FormaPagamento) {
    limparEtapaAnterior()
    setForma(outra)
    pedidos.current = 0
    gerar(outra)
  }

  /** QR Code vencido: pedir de novo gera um novo para a mesma cobrança. */
  function gerarNovoPix() {
    setPixVencido(false)
    pedidos.current = 0
    gerar('PIX')
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
          gerar(forma)
        }
      } catch {
        // Falha de rede numa consulta: a próxima tenta de novo.
      } finally {
        consultando.current = false
      }
    },
    [cobranca.id, forma, gerar],
  )

  // Com o PIX ou o boleto na tela, a situação se atualiza sozinha quando o aviso do provedor chega.
  // O boleto compensa em dias úteis, então a consulta é mais espaçada (a não ser que tenha sido pago no Sandbox).
  const aguardaConfirmacao = etapa.tipo === 'pronto'
  const consultaRapida = etapa.tipo === 'pronto' && (etapa.dados.forma === 'PIX' || pagoNoSandbox)

  useEffect(() => {
    if (!aguardaConfirmacao && etapa.tipo !== 'processando') return
    const tipo = etapa.tipo
    const intervalo = !aguardaConfirmacao
      ? INTERVALO_PROCESSANDO_MS
      : consultaRapida
        ? INTERVALO_CONFIRMACAO_MS
        : INTERVALO_BOLETO_MS
    const timer = window.setInterval(() => void consultar(tipo), intervalo)
    return () => window.clearInterval(timer)
  }, [etapa.tipo, aguardaConfirmacao, consultaRapida, consultar])

  // O QR Code tem prazo: quando vence, a tela troca o código por um aviso para gerar outro.
  const validadePix = etapa.tipo === 'pronto' && etapa.dados.forma === 'PIX' ? etapa.dados.pix.validade : null
  useEffect(() => {
    if (!validadePix) return
    // Já vencido ao chegar: o timer de zero marca logo em seguida.
    const restante = Math.max(0, Date.parse(validadePix) - Date.now())
    const timer = window.setTimeout(() => setPixVencido(true), restante)
    return () => window.clearTimeout(timer)
  }, [validadePix])

  const buscarComprovanteDaCobranca = useCallback(
    () => buscarComprovante(`/portal/aluno/cobrancas/${cobranca.id}/comprovante`),
    [cobranca.id],
  )

  async function verificarAgora() {
    setVerificando(true)
    await consultar(etapa.tipo)
    if (montado.current) setVerificando(false)
  }

  /**
   * Ambiente de teste: paga no Sandbox como se o aluno tivesse pago no banco.
   * O backend já grava o pagamento; a consulta logo em seguida mostra "pago".
   */
  async function pagarNoSandbox() {
    setPagandoNoSandbox(true)
    setErroSandbox(null)
    try {
      await pagarNoSandboxComoAluno(cobranca.id)
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

  // Confirmado: a modal passa a mostrar o comprovante (IU17-D).
  if (etapa.tipo === 'pago') {
    return (
      <ModalComprovante
        buscar={buscarComprovanteDaCobranca}
        onFechar={onFechar}
        aviso={
          <div className={styles.pago}>
            <span className={styles.pagoIcone}>
              <Icon name="check" size={28} strokeWidth={3} />
            </span>
            <strong>Pagamento confirmado!</strong>
            <span>{cobranca.descricao} está paga. O comprovante também vai para o e-mail cadastrado.</span>
          </div>
        }
      />
    )
  }

  const rodape =
    etapa.tipo === 'escolhendo' ? (
      <>
        <Button variant="outline" onClick={onFechar}>
          Cancelar
        </Button>
        <Button variant="primary" icon={forma === 'PIX' ? 'card' : 'fileText'} onClick={pagar}>
          {ROTULO_GERAR[forma]}
        </Button>
      </>
    ) : (
      <>
        {etapa.tipo === 'pronto' && (
          <Button variant="outline" onClick={() => trocarPara(OUTRA_FORMA[forma])}>
            {OUTRA_FORMA[forma] === 'PIX' ? 'Trocar para PIX' : 'Trocar para boleto'}
          </Button>
        )}
        {etapa.tipo === 'erro' && (
          <Button variant="outline" onClick={trocarForma}>
            Trocar forma de pagamento
          </Button>
        )}
        {etapa.tipo === 'erro' && (
          <Button variant="outline" icon="arrowRight" onClick={tentarDeNovo}>
            Tentar de novo
          </Button>
        )}
        {consultaRapida && (
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
      titulo={etapa.tipo === 'escolhendo' ? 'Pagar cobrança' : forma === 'PIX' ? 'Pagar com PIX' : 'Pagar com boleto'}
      subtitulo={`${cobranca.descricao} · ${formatarMoeda(cobranca.valor)} · vence ${formatarData(cobranca.dataVencimento)}`}
      tamanho="medio"
      onFechar={onFechar}
      rodape={rodape}
    >
      <div className={styles.corpo} aria-live="polite">
        {etapa.tipo === 'escolhendo' && (
          <fieldset className={styles.formas}>
            <legend className={styles.rotulo}>Como você quer pagar?</legend>
            {OPCOES_FORMA.map((opcao) => (
              <label
                key={opcao.forma}
                className={`${styles.forma} ${forma === opcao.forma ? styles.formaEscolhida : ''}`}
              >
                <input
                  type="radio"
                  name="forma-pagamento"
                  value={opcao.forma}
                  checked={forma === opcao.forma}
                  onChange={() => setForma(opcao.forma)}
                  className={styles.formaRadio}
                />
                <span className={styles.formaIcone} aria-hidden="true">
                  <Icon name={opcao.icone} size={20} />
                </span>
                <span className={styles.formaTexto}>
                  <strong>{opcao.titulo}</strong>
                  <span>{opcao.descricao}</span>
                </span>
              </label>
            ))}
            <p className={styles.valorDestino}>
              Valor a pagar: <strong>{formatarMoeda(cobranca.valor)}</strong>
            </p>
          </fieldset>
        )}

        {etapa.tipo === 'pronto' && (
          <div className={styles.situacao}>
            <span>
              Valor: <strong>{formatarMoeda(cobranca.valor)}</strong>
              {recebedor && (
                <>
                  {' '}
                  para <strong>{recebedor}</strong>
                </>
              )}
            </span>
            <Badge tone="warning">Aguardando pagamento</Badge>
          </div>
        )}

        {etapa.tipo === 'gerando' && <ModalAviso>Gerando {NOME_FORMA[forma]}…</ModalAviso>}

        {etapa.tipo === 'processando' && (
          <ModalAviso>
            O serviço de pagamento está preparando {NOME_FORMA[forma]}. Isso costuma levar poucos segundos…
          </ModalAviso>
        )}

        {etapa.tipo === 'erro' && <ModalAlerta>{etapa.mensagem}</ModalAlerta>}

        {etapa.tipo === 'indisponivel' && <ModalAlerta>{etapa.mensagem}</ModalAlerta>}

        {etapa.tipo === 'pronto' && etapa.dados.forma === 'PIX' && (
          <>
            <ol className={styles.passos}>
              <li>Abra o app do seu banco e escolha pagar com PIX.</li>
              <li>Leia o QR Code ou use o PIX copia e cola.</li>
              <li>Confirme o pagamento. Esta tela atualiza sozinha quando ele for confirmado.</li>
            </ol>

            {pixVencido ? (
              <div className={styles.boletoAcao}>
                <ModalAlerta>Este QR Code venceu. Gere um novo para pagar.</ModalAlerta>
                <Button variant="primary" icon="card" onClick={gerarNovoPix}>
                  Gerar novo QR Code
                </Button>
              </div>
            ) : (
              <>
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
              <span className={styles.validade}>
                Vencimento {formatarData(etapa.dados.vencimento)}. Depois dessa data a cobrança fica em atraso, mas
                continua podendo ser paga por aqui.
              </span>
              {erroDownload && <ModalAlerta>{erroDownload}</ModalAlerta>}
            </div>

            {/* O banco aceita só os números; a pontuação é só para leitura. */}
            <CampoCopiar
              id="boleto-linha-digitavel"
              rotulo="Código de barras (linha digitável)"
              valor={formatarLinhaDigitavel(etapa.dados.boleto.linhaDigitavel)}
              valorCopiado={etapa.dados.boleto.linhaDigitavel.replace(/\D/g, '')}
              rotuloBotao="Copiar linha"
              copiado={copiado}
              onCopiar={copiar}
            />
          </>
        )}

        {etapa.tipo === 'pronto' && atalhosSandboxHabilitados && (
          <div className={styles.sandbox}>
            <span className={styles.sandboxSelo}>Ambiente de teste</span>
            <p>
              Ninguém paga de verdade aqui. Clique em Pagar para simular no Asaas o pagamento{' '}
              {forma === 'PIX' ? 'do PIX' : 'do boleto'}, como se tivesse pago no banco.
            </p>
            {pagoNoSandbox ? (
              <span className={styles.validade}>Pagamento enviado ao Asaas. Aguardando a confirmação…</span>
            ) : (
              <Button
                variant="accent"
                icon="check"
                onClick={() => void pagarNoSandbox()}
                disabled={pagandoNoSandbox}
              >
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
  /** O que aparece no campo. */
  valor: string
  /** O que vai para a área de transferência; sem ele, o próprio `valor`. */
  valorCopiado?: string
  /** Texto do botão; o padrão é "Copiar código". */
  rotuloBotao?: string
  copiado: 'sim' | 'falhou' | null
  onCopiar: (codigo: string) => Promise<void>
}

/** Código só de leitura com o botão "Copiar" ao lado. */
function CampoCopiar({
  id,
  rotulo,
  valor,
  valorCopiado,
  rotuloBotao = 'Copiar código',
  copiado,
  onCopiar,
}: CampoCopiarProps) {
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
          {copiado === 'sim' ? 'Copiado!' : rotuloBotao}
        </Button>
      </div>
      {copiado === 'falhou' && (
        <span className={styles.dica}>Não foi possível copiar. Selecione o código acima e copie manualmente.</span>
      )}
    </div>
  )
}
