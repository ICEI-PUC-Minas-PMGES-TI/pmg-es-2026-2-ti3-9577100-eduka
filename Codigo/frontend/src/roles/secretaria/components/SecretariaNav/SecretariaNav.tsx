import { NavLink } from 'react-router'
import { Icon, type IconName } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import styles from './SecretariaNav.module.css'

// Itens sem `to` ainda não têm página e aparecem desabilitados.
const itens: { label: string; icon: IconName; to?: string }[] = [
  { label: 'Início', icon: 'home' },
  { label: 'Alunos', icon: 'users' },
  { label: 'Professores', icon: 'graduationCap', to: paths.secretaria.professores },
  { label: 'Acadêmico', icon: 'book' },
  { label: 'Matrículas', icon: 'check' },
]

export function SecretariaNav() {
  return (
    <nav className={styles.nav} aria-label="Navegação da secretaria">
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
