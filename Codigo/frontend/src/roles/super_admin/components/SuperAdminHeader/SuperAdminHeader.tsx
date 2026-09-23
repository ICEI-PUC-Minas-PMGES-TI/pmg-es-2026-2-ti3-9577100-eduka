import iconeEduka from '../../../../assets/eduka-icone.svg'
import { Icon } from '../../../../components/Icon/Icon'
import { iniciais } from '../../../../utils/formatters'
import styles from './SuperAdminHeader.module.css'

type SuperAdminHeaderProps = {
  busca: string
  onBuscaChange: (valor: string) => void
  usuario: { nome: string; perfil: string }
  onSair: () => void
}

export function SuperAdminHeader({ busca, onBuscaChange, usuario, onSair }: SuperAdminHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.brand}>
        <img src={iconeEduka} alt="" className={styles.logoIcon} width={32} height={32} />
        <span className={styles.logo}>eduka</span>
        <span className={styles.tag}>Super Admin</span>
      </div>

      <label className={styles.search}>
        <Icon name="search" size={16} />
        <input
          type="search"
          placeholder="Buscar escola, CNPJ ou responsável"
          aria-label="Buscar escola, CNPJ ou responsável"
          value={busca}
          onChange={(e) => onBuscaChange(e.target.value)}
        />
      </label>

      <div className={styles.user}>
        <span className={styles.avatar} aria-hidden="true">
          {iniciais(usuario.nome)}
        </span>
        <div className={styles.userInfo}>
          <strong>{usuario.nome}</strong>
          <span>{usuario.perfil}</span>
        </div>
        <button type="button" className={styles.sair} onClick={onSair} aria-label="Sair" title="Sair">
          <Icon name="logout" size={16} />
        </button>
      </div>
    </header>
  )
}
