import { NavLink } from 'react-router'
import { Icon, type IconName } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import styles from './SecretariaNav.module.css'

// Itens sem `to` ainda não têm página e aparecem desabilitados.
const itens: { label: string; icon: IconName; to?: string }[] = [
  { label: 'Início', icon: 'home' },
  { label: 'Alunos', icon: 'users', to: paths.secretaria.alunos },
  { label: 'Professores', icon: 'graduationCap', to: paths.secretaria.professores },
  // Também fica ativo na alocação, que é subrota do Acadêmico.
  { label: 'Acadêmico', icon: 'book', to: paths.secretaria.academico },
  { label: 'Matrículas', icon: 'check', to: paths.secretaria.matriculas },
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
