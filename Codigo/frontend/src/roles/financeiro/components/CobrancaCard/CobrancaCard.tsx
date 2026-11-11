import type { ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Icon } from '../../../../components/Icon/Icon'
import { NivelRisco } from '../../../../components/RiscoAtraso/RiscoAtraso'
import { ROTULOS_STATUS_COBRANCA, type Cobranca, type StatusCobranca } from '../../../../types/cobranca'
import type { RiscoAtraso } from '../../../../types/riscoAtraso'
import { formatarDiaMes, formatarMoeda } from '../../../../utils/formatters'
import styles from './CobrancaCard.module.css'

type CobrancaCardProps = {
  cobranca: Cobranca
  aberta: boolean
  onAlternar: () => void
  /** Risco de atraso do aluno, mostrado com o motivo abaixo da descrição. */
  risco?: RiscoAtraso
  /** Detalhes mostrados abaixo do cabeçalho enquanto o card está aberto. */
  children?: ReactNode
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

/** Linha da lista de cobranças; a seta abre os detalhes logo abaixo. */
export function CobrancaCard({ cobranca, aberta, onAlternar, risco, children }: CobrancaCardProps) {
  const idDetalhes = `cobranca-detalhes-${cobranca.id}`

  return (
    <article className={`${styles.card} ${aberta ? styles.aberta : ''}`}>
      <button
        type="button"
        className={styles.cabecalho}
        onClick={onAlternar}
        aria-expanded={aberta}
        aria-controls={idDetalhes}
      >
        <div className={styles.infoEsquerda}>
          <span className={styles.nomeAluno}>{cobranca.alunoNome ?? 'Aluno sem nome'}</span>
          <span className={styles.descricao}>
            {cobranca.descricao} · vence {formatarDiaMes(cobranca.dataVencimento)}
          </span>
          {risco && (
            <span className={styles.risco}>
              <NivelRisco nivel={risco.nivel} />
              {/* Numa linha só; o motivo inteiro aparece no title. */}
              <span className={styles.motivoRisco} title={risco.motivo}>
                {risco.motivo}
              </span>
            </span>
          )}
        </div>

        <div className={styles.infoDireita}>
          <span className={styles.valor}>{formatarMoeda(cobranca.valor)}</span>
          {cobranca.pagaAposCancelamento ? (
            <Badge tone="warning" icon="alert">
              Paga após cancelar
            </Badge>
          ) : (
            <Badge tone={obterTomBadge(cobranca.status)}>
              {ROTULOS_STATUS_COBRANCA[cobranca.status]}
            </Badge>
          )}
          <span className={styles.seta} aria-hidden="true">
            <Icon name="chevronDown" size={18} className={aberta ? styles.setaAberta : undefined} />
          </span>
        </div>
      </button>

      {aberta && (
        <div id={idDetalhes} className={styles.detalhes}>
          {children}
        </div>
      )}
    </article>
  )
}
