import type { ReactNode } from 'react'
import { Icon, type IconName } from '../Icon/Icon'
import styles from './Badge.module.css'

type BadgeProps = {
  tone: 'success' | 'danger' | 'neutral'
  icon?: IconName
  children: ReactNode
}

export function Badge({ tone, icon, children }: BadgeProps) {
  return (
    <span className={`${styles.badge} ${styles[tone]}`}>
      {icon && <Icon name={icon} size={12} strokeWidth={2.5} />}
      {children}
    </span>
  )
}
