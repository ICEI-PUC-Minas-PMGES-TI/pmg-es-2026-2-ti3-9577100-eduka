import { NavLink } from 'react-router'
import { Icon, type IconName } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import styles from './AlunoNav.module.css'

type NavItem = {
  label: string
  icon: IconName
  to?: string
}

const itens: NavItem[] = [
  { label: 'Boletim', icon: 'book', to: paths.aluno.boletim },
  { label: 'Provas e tarefas', icon: 'calendar', to: paths.aluno.avaliacoes },
  { label: 'Pagamentos', icon: 'card' },
  { label: 'Meus dados', icon: 'user', to: paths.aluno.meusDados },
]

export function AlunoNav() {
  return (
    <nav className={styles.nav} aria-label="Navegação do portal do aluno">
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
