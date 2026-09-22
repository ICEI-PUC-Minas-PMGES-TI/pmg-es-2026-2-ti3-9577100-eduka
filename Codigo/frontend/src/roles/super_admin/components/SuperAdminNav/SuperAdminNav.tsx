import { NavLink } from 'react-router'
import { Icon, type IconName } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import styles from './SuperAdminNav.module.css'

// Itens sem `to` ainda não têm página e aparecem desabilitados.
const itens: { label: string; icon: IconName; to?: string }[] = [
  { label: 'Escolas', icon: 'home', to: paths.superAdmin.escolas },
  { label: 'Usuários', icon: 'users' },
  { label: 'Assinaturas', icon: 'card' },
  { label: 'Configurações', icon: 'settings' },
]

export function SuperAdminNav() {
  return (
    <nav className={styles.nav} aria-label="Navegação do super admin">
      {itens.map((item) =>
        item.to ? (
          <NavLink
            key={item.label}
            to={item.to}
            className={({ isActive }) => `${styles.item} ${isActive ? styles.active : ''}`}
          >
            <Icon name={item.icon} size={16} />
            <span>{item.label}</span>
          </NavLink>
        ) : (
          <button
            key={item.label}
            type="button"
            className={styles.item}
            disabled
            title="Em breve"
          >
            <Icon name={item.icon} size={16} />
            <span>{item.label}</span>
          </button>
        ),
      )}
    </nav>
  )
}
