import type { ReactNode } from 'react'
import { Icon } from '../../ui/Icon/Icon'
import { useAbrirAjuda } from '../AjudaContexto'
import styles from './IndicadorAjuda.module.css'

/**
 * Atalho discreto para a Central de ajuda, perto de onde a dúvida costuma
 * surgir (ex.: "Nota errada? Fale com a escola"). Fora do PainelLayout não
 * há Central para abrir e o indicador não aparece.
 */
export function IndicadorAjuda({ children }: { children: ReactNode }) {
  const abrirAjuda = useAbrirAjuda()
  if (!abrirAjuda) return null
  return (
    <button type="button" className={styles.indicador} onClick={abrirAjuda}>
      <Icon name="help" size={14} />
      <span>{children}</span>
    </button>
  )
}
