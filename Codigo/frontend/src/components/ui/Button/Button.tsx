import type { ButtonHTMLAttributes, Ref } from 'react'
import { Icon, type IconName } from '../Icon/Icon'
import styles from './Button.module.css'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  /**
   * danger: ação destrutiva já confirmada (o "Excluir" do diálogo).
   * dangerOutline: atalho de excluir na linha da tabela, discreto até o hover.
   */
  variant?: 'primary' | 'accent' | 'outline' | 'danger' | 'dangerOutline'
  size?: 'sm' | 'md'
  icon?: IconName
  /** Para quem ancora um popover no botão (ex.: MenuBotao); no React 19 o ref chega como prop. */
  ref?: Ref<HTMLButtonElement>
}

export function Button({
  variant = 'outline',
  size = 'md',
  icon,
  className,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  // Só com ícone (ex.: excluir na linha da tabela): fica compacto.
  const classes = [styles.button, styles[variant], styles[size], !children && styles.soIcone, className]
    .filter(Boolean)
    .join(' ')

  return (
    <button type={type} className={classes} {...props}>
      {icon && <Icon name={icon} size={size === 'sm' ? 14 : 16} />}
      {children}
    </button>
  )
}
