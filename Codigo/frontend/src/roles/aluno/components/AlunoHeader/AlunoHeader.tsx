import type { ReactNode } from 'react'
import iconeEduka from '../../../../assets/eduka-icone.svg'
import { Icon } from '../../../../components/Icon/Icon'
import styles from './AlunoHeader.module.css'

type AlunoHeaderProps = {
  titulo: string
  subtitulo?: ReactNode
  escola?: string
  onSair: () => void
  onAjuda?: () => void
}

export function AlunoHeader({ titulo, subtitulo, escola, onSair, onAjuda }: AlunoHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.texto}>
        <div className={styles.brand}>
          <img src={iconeEduka} alt="" className={styles.logoIcon} width={26} height={26} />
          <span className={styles.logo}>eduka</span>
          <span className={styles.contexto}>
            {escola ? ` · ${escola}` : ' · [NOME DA ESCOLA]'} · Aluno
          </span>
        </div>
        <h1 className={styles.title}>{titulo}</h1>
        {subtitulo && <p className={styles.subtitle}>{subtitulo}</p>}
      </div>

      <div className={styles.acoes}>
        <button
          type="button"
          className={styles.pilula}
          onClick={onAjuda}
          title="Ajuda e orientações"
        >
          <Icon name="help" size={17} />
          Ajuda
        </button>
        <button
          type="button"
          className={styles.pilula}
          onClick={onSair}
          title="Sair do sistema"
        >
          <Icon name="logout" size={17} />
          Sair
        </button>
      </div>
    </header>
  )
}
