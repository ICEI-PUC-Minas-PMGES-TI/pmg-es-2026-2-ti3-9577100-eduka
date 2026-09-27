import type { ReactNode } from 'react'
import iconeEduka from '../../../../assets/eduka-icone.svg'
import { Icon } from '../../../../components/Icon/Icon'
import styles from './SuperAdminHeader.module.css'

type SuperAdminHeaderProps = {
  titulo: string
  subtitulo?: ReactNode
  acao?: ReactNode
  voltar?: ReactNode
  /** Login de quem está no console, exibido na linha da marca. */
  usuario?: string
  onSair: () => void
}

export function SuperAdminHeader({ titulo, subtitulo, acao, voltar, usuario, onSair }: SuperAdminHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.texto}>
        {voltar ?? (
          <div className={styles.brand}>
            <img src={iconeEduka} alt="" className={styles.logoIcon} width={26} height={26} />
            <span className={styles.logo}>eduka</span>
            <span className={styles.contexto}>
              · Super Console{usuario ? ` · ${usuario}` : ''}
            </span>
          </div>
        )}
        <h1 className={styles.title}>{titulo}</h1>
        {subtitulo && <p className={styles.subtitle}>{subtitulo}</p>}
      </div>

      <div className={styles.acoes}>
        {acao}
        <button type="button" className={styles.sair} onClick={onSair}>
          <Icon name="logout" size={17} />
          Sair
        </button>
      </div>
    </header>
  )
}
