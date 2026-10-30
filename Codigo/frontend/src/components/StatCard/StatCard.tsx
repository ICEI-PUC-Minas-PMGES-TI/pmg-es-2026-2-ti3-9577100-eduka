import { Children, type CSSProperties, type ReactNode } from 'react'
import styles from './StatCard.module.css'

type StatCardProps = {
  label: string
  value: number | string
  /** Linha pequena abaixo do número. */
  descricao?: ReactNode
  /** Card lavanda com rótulo roxo, para o número principal da tela. */
  destaque?: boolean
  /** Número em vermelho, para o que pede atenção. */
  alerta?: boolean
  /** Número em âmbar, para pendência que não é erro. */
  atencao?: boolean
}

export function StatCard({ label, value, descricao, destaque = false, alerta = false, atencao = false }: StatCardProps) {
  return (
    <div className={`${styles.card} ${destaque ? styles.destaque : ''}`}>
      <span className={styles.label}>{label}</span>
      <strong
        className={`${styles.value} ${alerta ? styles.alerta : atencao ? styles.atencao : ''}`}
        // O CSS usa a quantidade de caracteres para o valor longo caber no card.
        style={{ '--caracteres': String(value).length } as CSSProperties}
      >
        {value}
      </strong>
      {descricao && <div className={styles.descricao}>{descricao}</div>}
    </div>
  )
}

const COLUNAS: Record<number, string | undefined> = { 2: styles.duas, 3: styles.tres, 4: styles.quatro }

/**
 * Faixa de StatCards no topo de uma tela (resumo de listagens e dashboards).
 * Uma coluna por card, até quatro; empilha em tela estreita.
 */
export function StatCards({ rotulo, children }: { rotulo: string; children: ReactNode }) {
  const quantidade = Math.min(Children.toArray(children).length, 4)
  return (
    <section className={`${styles.faixa} ${COLUNAS[quantidade] ?? ''}`} aria-label={rotulo}>
      {children}
    </section>
  )
}
