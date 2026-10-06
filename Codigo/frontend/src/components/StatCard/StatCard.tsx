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
  /** Número em verde, para métricas positivas/sucesso (ex: frequência). */
  sucesso?: boolean
}

export function StatCard({
  label,
  value,
  descricao,
  destaque = false,
  alerta = false,
  sucesso = false,
}: StatCardProps) {
  const valueClasses = [
    styles.value,
    alerta && styles.alerta,
    sucesso && styles.sucesso,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={`${styles.card} ${destaque ? styles.destaque : ''}`}>
      <span className={styles.label}>{label}</span>
      <strong className={valueClasses}>{value}</strong>
      {descricao && <span className={styles.descricao}>{descricao}</span>}
    </div>
  )
}
