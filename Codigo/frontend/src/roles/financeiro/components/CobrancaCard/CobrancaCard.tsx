import { Badge } from '../../../../components/Badge/Badge'
import { ROTULOS_STATUS_COBRANCA, type Cobranca, type StatusCobranca } from '../../../../types/cobranca'
import { formatarDiaMes, formatarMoeda } from '../../../../utils/formatters'
import styles from './CobrancaCard.module.css'

type CobrancaCardProps = {
  cobranca: Cobranca
  selecionada: boolean
  onClick: () => void
}

function obterTomBadge(status: StatusCobranca): 'primary' | 'danger' | 'success' | 'neutral' {
  switch (status) {
    case 'PENDENTE':
      return 'primary'
    case 'EM_ATRASO':
      return 'danger'
    case 'PAGA':
      return 'success'
    case 'CANCELADA':
      return 'neutral'
  }
}

export function CobrancaCard({ cobranca, selecionada, onClick }: CobrancaCardProps) {
  return (
    <button
      type="button"
      className={`${styles.card} ${selecionada ? styles.selecionada : ''}`}
      onClick={onClick}
      aria-pressed={selecionada}
    >
      <div className={styles.infoEsquerda}>
        <span className={styles.nomeAluno}>{cobranca.alunoNome ?? 'Aluno sem nome'}</span>
        <span className={styles.descricao}>
          {cobranca.descricao} · vence {formatarDiaMes(cobranca.dataVencimento)}
        </span>
      </div>

      <div className={styles.infoDireita}>
        <span className={styles.valor}>{formatarMoeda(cobranca.valor)}</span>
        <Badge tone={obterTomBadge(cobranca.status)}>
          {ROTULOS_STATUS_COBRANCA[cobranca.status]}
        </Badge>
      </div>
    </button>
  )
}

