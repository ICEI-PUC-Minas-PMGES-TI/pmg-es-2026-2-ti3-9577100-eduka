import type { ReactNode } from 'react'
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
}

export function StatCard({ label, value, descricao, destaque = false, alerta = false }: StatCardProps) {
  return (
    <div className={`${styles.card} ${destaque ? styles.destaque : ''}`}>
      <span className={styles.label}>{label}</span>
      <strong className={`${styles.value} ${alerta ? styles.alerta : ''}`}>{value}</strong>
      {descricao && <span className={styles.descricao}>{descricao}</span>}
    </div>
  )
}
