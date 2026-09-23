import { useId, type ReactNode } from 'react'
import styles from './Campo.module.css'

export type PropsControle = {
  id: string
  className: string
  required: boolean
  'aria-invalid': boolean
  'aria-describedby': string | undefined
}

type CampoProps = {
  label: string
  erro?: string
  /** Texto de apoio exibido abaixo do controle. */
  dica?: string
  obrigatorio?: boolean
  /** Ocupa a linha inteira do grid. */
  largo?: boolean
  className?: string
  /** Recebe id, classe e atributos de acessibilidade para repassar ao controle. */
  children: (props: PropsControle) => ReactNode
}

/** Rótulo + controle (input/select) + mensagem de erro, com a ligação de acessibilidade feita. */
export function Campo({ label, erro, dica, obrigatorio = false, largo = false, className, children }: CampoProps) {
  const id = useId()
  const erroId = `${id}-erro`
  const dicaId = `${id}-dica`
  const descritoPor = [dica && dicaId, erro && erroId].filter(Boolean).join(' ') || undefined
  const classes = [styles.field, largo && styles.fieldWide, className].filter(Boolean).join(' ')

  return (
    <div className={classes}>
      <label className={styles.label} htmlFor={id}>
        {label}
        {obrigatorio && (
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children({
        id,
        className: styles.control,
        required: obrigatorio,
        'aria-invalid': Boolean(erro),
        'aria-describedby': descritoPor,
      })}
      {dica && (
        <span id={dicaId} className={styles.dica}>
          {dica}
        </span>
      )}
      {erro && (
        <span id={erroId} className={styles.error}>
          {erro}
        </span>
      )}
    </div>
  )
}
