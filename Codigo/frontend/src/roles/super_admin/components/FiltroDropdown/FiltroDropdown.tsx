import { Select, type OpcaoSelect } from '../../../../components/Select/Select'
import styles from './FiltroDropdown.module.css'

type FiltroDropdownProps<T extends string> = {
  opcoes: OpcaoSelect<T>[]
  valor: T
  /** Valor que significa "sem filtro"; qualquer outro deixa o botão destacado. */
  valorPadrao: T
  onChange: (valor: T) => void
  'aria-label': string
}

/** Dropdown compacto da barra de filtros da tabela de escolas. */
export function FiltroDropdown<T extends string>({
  opcoes,
  valor,
  valorPadrao,
  onChange,
  'aria-label': ariaLabel,
}: FiltroDropdownProps<T>) {
  return (
    <Select
      opcoes={opcoes}
      valor={valor}
      onChange={onChange}
      aria-label={ariaLabel}
      className={`${styles.gatilho} ${valor !== valorPadrao ? styles.filtrando : ''}`}
    />
  )
}
