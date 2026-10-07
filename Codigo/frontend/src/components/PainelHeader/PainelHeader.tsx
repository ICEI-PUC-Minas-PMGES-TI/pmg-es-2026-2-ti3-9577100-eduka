import type { ReactNode } from 'react'
import iconeEduka from '../../assets/eduka-icone.svg'
import { Icon } from '../Icon/Icon'
import styles from './PainelHeader.module.css'

type PainelHeaderProps = {
  titulo: string
  subtitulo?: ReactNode
  /** Área do painel na linha da marca, ex.: "Secretaria", "Gestão da escola". */
  contexto: string
  /** Nome da escola na linha da marca; a API ainda não expõe para a secretaria. */
  escola?: string
  onSair: () => void
}

/** Cabeçalho dos painéis da escola (master e secretaria). */
export function PainelHeader({ titulo, subtitulo, contexto, escola, onSair }: PainelHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.texto}>
        <div className={styles.brand}>
          <img src={iconeEduka} alt="" className={styles.logoIcon} width={26} height={26} />
          <span className={styles.logo}>eduka</span>
          <span className={styles.contexto}>
            {escola ? ` · ${escola}` : ''} · {contexto}
          </span>
        </div>
        <h1 className={styles.title}>{titulo}</h1>
        {subtitulo && <p className={styles.subtitle}>{subtitulo}</p>}
      </div>

      <div className={styles.acoes}>
        <button type="button" className={styles.pilula} disabled title="Em breve">
          <Icon name="help" size={17} />
          Ajuda
        </button>
        <button type="button" className={styles.pilula} onClick={onSair}>
          <Icon name="logout" size={17} />
          Sair
        </button>
      </div>
    </header>
  )
}
