import type { ReactNode } from 'react'
import styles from './StatCard.module.css'

type StatCardProps = {
  label: string
  value: number | string
  badge?: ReactNode
}

export function StatCard({ label, value, badge }: StatCardProps) {
  return (
    <div className={styles.card}>
      <span className={styles.label}>{label}</span>
      <div className={styles.valueRow}>
        <strong className={styles.value}>{value}</strong>
        {badge}
      </div>
    </div>
  )
}
