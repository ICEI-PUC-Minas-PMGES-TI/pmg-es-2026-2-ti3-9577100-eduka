import { NavLink } from 'react-router'
import { Icon, type IconName } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import styles from './ProfessorNav.module.css'

// Itens sem `to` ainda não têm página e aparecem desabilitados.
const itens: { label: string; icon: IconName; to?: string }[] = [
  { label: 'Início', icon: 'home' },
  { label: 'Minhas turmas', icon: 'users', to: paths.professor.turmas },
  { label: 'Avaliações', icon: 'calendar', to: paths.professor.avaliacoes },
  { label: 'Notas', icon: 'check', to: paths.professor.notas },
]

export function ProfessorNav() {
  return (
    <nav className={styles.nav} aria-label="Navegação do professor">
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
