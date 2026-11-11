import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import type { CobrancaDoHistorico } from '../../../../types/pagamentoAluno'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { prazoDoVencimento, TOM_STATUS_COBRANCA } from '../../formatacao'
import styles from './ProximaMensalidade.module.css'

type ProximaMensalidadeProps = {
  cobranca: CobrancaDoHistorico
  /** Abre o modal de pagamento, onde o aluno escolhe PIX ou boleto. */
  onPagar: (cobranca: CobrancaDoHistorico) => void
  rotuloBotao?: string
}

/** Cartão de destaque da cobrança em aberto que vence primeiro (Início e Pagamentos). */
export function ProximaMensalidade({ cobranca, onPagar, rotuloBotao = 'Pagar' }: ProximaMensalidadeProps) {
  const atrasada = cobranca.status === 'EM_ATRASO'
  return (
    <section className={`${styles.proxima} ${atrasada ? styles.atrasada : ''}`} aria-labelledby="titulo-proxima">
      <div className={styles.info}>
        <span id="titulo-proxima" className={styles.rotulo}>
          {atrasada ? 'Cobrança em atraso' : 'Próxima mensalidade'}
        </span>
        <strong className={styles.valor}>{formatarMoeda(cobranca.valor)}</strong>
        <span className={styles.descricao}>{cobranca.descricao}</span>
        <span className={styles.prazo}>
          <Badge tone={TOM_STATUS_COBRANCA[cobranca.status]}>{prazoDoVencimento(cobranca.dataVencimento)}</Badge>
          <span className={styles.secundario}>Vencimento {formatarData(cobranca.dataVencimento)}</span>
        </span>
      </div>
      <div className={styles.acoes}>
        <Button
          variant="primary"
          icon="card"
          onClick={() => onPagar(cobranca)}
          aria-label={`Pagar ${cobranca.descricao} com PIX ou boleto`}
        >
          {rotuloBotao}
        </Button>
      </div>
    </section>
  )
}
