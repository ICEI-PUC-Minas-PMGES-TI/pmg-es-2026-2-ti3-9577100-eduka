import styles from './Switch.module.css'

type SwitchProps = {
  name: string
  checked: boolean
  onChange: (checked: boolean) => void
  /** Texto ao lado da chave para cada estado, ex.: "Ativa" / "Suspensa". */
  rotuloLigado: string
  rotuloDesligado: string
  'aria-labelledby'?: string
  disabled?: boolean
}

export function Switch({
  name,
  checked,
  onChange,
  rotuloLigado,
  rotuloDesligado,
  disabled,
  ...aria
}: SwitchProps) {
  return (
    <label className={styles.switch}>
      <input
        type="checkbox"
        role="switch"
        name={name}
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        {...aria}
      />
      <span className={styles.track} aria-hidden="true" />
      <span className={checked ? styles.ligado : styles.desligado}>
        {checked ? rotuloLigado : rotuloDesligado}
      </span>
    </label>
  )
}
