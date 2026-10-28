import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { Modal } from '../../../../components/Modal/Modal'
import type { Cobranca, DadosRegistroPagamento } from '../../../../types/cobranca'
import { formatarMoeda } from '../../../../utils/formatters'
import styles from './ModalRegistrarPagamento.module.css'

type ModalRegistrarPagamentoProps = {
  cobranca: Cobranca
  onFechar: () => void
  onConfirmar: (dados: DadosRegistroPagamento) => Promise<void>
}

const FORMAS_PAGAMENTO = [
  'PIX direto',
  'Dinheiro',
  'Transferência bancária',
  'Cartão',
  'Outro',
]

function formatarDataBr(isoDate: string): string {
  if (!isoDate) return ''
  const partes = isoDate.split('-')
  if (partes.length === 3) return `${partes[2]}/${partes[1]}/${partes[0]}`
  return isoDate
}

function obterHojeIso(): string {
  return new Date().toISOString().split('T')[0]
}

export function ModalRegistrarPagamento({
  cobranca,
  onFechar,
  onConfirmar,
}: ModalRegistrarPagamentoProps) {
  const [etapa, setEtapa] = useState<'formulario' | 'confirmacao'>('formulario')
  const [dataPagamento, setDataPagamento] = useState(obterHojeIso)
  const [formaPagamento, setFormaPagamento] = useState('PIX direto')
  const [observacoes, setObservacoes] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  function handleAvancarParaConfirmacao(e: FormEvent) {
    e.preventDefault()
    setErro(null)

    if (!dataPagamento) {
      setErro('Informe a data em que o pagamento foi realizado.')
      return
    }

    setEtapa('confirmacao')
  }

  async function handleConfirmarFinal() {
    setErro(null)
    setSalvando(true)
    try {
      await onConfirmar({
        dataPagamento,
        formaPagamento,
        observacoes: observacoes.trim() || undefined,
      })
      onFechar()
    } catch {
      setErro('Não foi possível registrar o pagamento. Tente novamente.')
    } finally {
      setSalvando(false)
    }
  }

  const tituloModal =
    etapa === 'formulario' ? 'Registrar pagamento' : 'Confirmar registro de pagamento'

  const subtituloModal =
    etapa === 'formulario'
      ? `Cobrança de ${cobranca.alunoNome}`
      : 'Confirme os dados antes de dar baixa no sistema'

  return (
    <Modal
      titulo={tituloModal}
      subtitulo={subtituloModal}
      tamanho="medio"
      ocupado={salvando}
      onFechar={onFechar}
      rodape={
        <div className={styles.rodapeBotoes}>
          {etapa === 'formulario' ? (
            <>
              <Button variant="outline" type="button" onClick={onFechar} disabled={salvando}>
                Cancelar
              </Button>
              <Button
                variant="primary"
                type="button"
                onClick={handleAvancarParaConfirmacao}
                disabled={salvando}
              >
                Avançar para confirmação →
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="outline"
                type="button"
                onClick={() => setEtapa('formulario')}
                disabled={salvando}
              >
                ← Voltar e alterar
              </Button>
              <Button
                variant="primary"
                type="button"
                onClick={handleConfirmarFinal}
                disabled={salvando}
              >
                {salvando ? 'Salvando…' : 'Confirmar e salvar'}
              </Button>
            </>
          )}
        </div>
      }
    >
      {etapa === 'formulario' ? (
        <form onSubmit={handleAvancarParaConfirmacao} className={styles.container}>
          {/* Resumo da Cobrança */}
          <div className={styles.resumoCard}>
            <div className={styles.resumoTopo}>
              <h3 className={styles.alunoNome}>{cobranca.alunoNome}</h3>
              <span className={styles.resumoValor}>{formatarMoeda(cobranca.valor)}</span>
            </div>
            <div className={styles.resumoDetalhes}>
              <span className={styles.resumoItem}>
                Descrição: <strong>{cobranca.descricao}</strong>
              </span>
              <span className={styles.resumoItem}>
                Vencimento: <strong>{formatarDataBr(cobranca.dataVencimento)}</strong>
              </span>
              <span className={styles.resumoItem}>
                Responsável: <strong>{cobranca.responsavelNome}</strong>
              </span>
            </div>
          </div>

          {/* Data do Pagamento */}
          <div className={styles.campo}>
            <label className={styles.label} htmlFor="dataPagamento">
              Data do pagamento *
            </label>
            <input
              id="dataPagamento"
              type="date"
              className={styles.input}
              value={dataPagamento}
              onChange={(e) => setDataPagamento(e.target.value)}
              required
            />
          </div>

          {/* Forma de Pagamento */}
          <div className={styles.campo}>
            <span className={styles.label}>Forma de pagamento *</span>
            <div className={styles.formasGrid}>
              {FORMAS_PAGAMENTO.map((forma) => {
                const ativo = formaPagamento === forma
                return (
                  <button
                    key={forma}
                    type="button"
                    className={`${styles.formaBtn} ${ativo ? styles.formaBtnAtivo : ''}`}
                    onClick={() => setFormaPagamento(forma)}
                  >
                    {forma}
                  </button>
                )
              })}
            </div>
          </div>

          {/* Observações */}
          <div className={styles.campo}>
            <label className={styles.label} htmlFor="observacoes">
              Observações (opcional)
            </label>
            <input
              id="observacoes"
              type="text"
              className={styles.input}
              placeholder="Ex.: Comprovante entregue na secretaria, dinheiro em mãos..."
              value={observacoes}
              onChange={(e) => setObservacoes(e.target.value)}
            />
          </div>

          {erro && <p className={styles.erro}>{erro}</p>}
        </form>
      ) : (
        <div className={styles.confirmacaoContainer}>
          {/* Banner de Aviso */}
          <div className={styles.alertaConfirmacao}>
            <Icon name="alert" size={20} className={styles.alertaIcone} />
            <div className={styles.alertaTexto}>
              <strong>Atenção à baixa manual</strong>
              Ao confirmar, a cobrança será marcada como <strong>PAGA</strong> e esta ação será
              registrada no histórico financeiro da escola.
            </div>
          </div>

          {/* Card de Conferência dos Dados */}
          <div className={styles.conferenciaCard}>
            <div className={styles.conferenciaLinha}>
              <span className={styles.conferenciaRotulo}>Aluno</span>
              <span className={styles.conferenciaValor}>{cobranca.alunoNome}</span>
            </div>

            <div className={styles.conferenciaLinha}>
              <span className={styles.conferenciaRotulo}>Valor recebido</span>
              <span className={`${styles.conferenciaValor} ${styles.valorDestaque}`}>
                {formatarMoeda(cobranca.valor)}
              </span>
            </div>

            <div className={styles.conferenciaLinha}>
              <span className={styles.conferenciaRotulo}>Data do pagamento</span>
              <span className={styles.conferenciaValor}>{formatarDataBr(dataPagamento)}</span>
            </div>

            <div className={styles.conferenciaLinha}>
              <span className={styles.conferenciaRotulo}>Forma de pagamento</span>
              <span className={styles.conferenciaValor}>{formaPagamento}</span>
            </div>

            <div className={styles.conferenciaLinha}>
              <span className={styles.conferenciaRotulo}>Referente a</span>
              <span className={styles.conferenciaValor}>{cobranca.descricao}</span>
            </div>

            {observacoes.trim() && (
              <div className={styles.conferenciaLinha}>
                <span className={styles.conferenciaRotulo}>Observações</span>
                <span className={styles.conferenciaValor}>{observacoes.trim()}</span>
              </div>
            )}
          </div>

          {erro && <p className={styles.erro}>{erro}</p>}
        </div>
      )}
    </Modal>
  )
}

