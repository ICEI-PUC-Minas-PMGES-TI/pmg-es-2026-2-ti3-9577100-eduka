import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/layout/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.professor.inicio },
  { label: 'Minhas turmas', icon: 'users', to: paths.professor.turmas },
  { label: 'Chamada', icon: 'calendar', to: paths.professor.chamada },
  { label: 'Avaliações', icon: 'list', to: paths.professor.avaliacoes },
  { label: 'Meu perfil', icon: 'user', to: paths.professor.meuPerfil },
]

type ProfessorLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function ProfessorLayout(props: ProfessorLayoutProps) {
  return <PainelLayout contexto="Professor" menu={MENU} menuRotulo="Navegação do professor" {...props} />
}
