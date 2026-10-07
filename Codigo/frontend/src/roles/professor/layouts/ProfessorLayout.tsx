import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.professor.inicio },
  { label: 'Minhas turmas', icon: 'users', to: paths.professor.turmas },
  { label: 'Avaliações', icon: 'calendar', to: paths.professor.avaliacoes },
  { label: 'Notas', icon: 'check', to: paths.professor.notas },
]

type ProfessorLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function ProfessorLayout(props: ProfessorLayoutProps) {
  return <PainelLayout contexto="Professor" menu={MENU} menuRotulo="Navegação do professor" {...props} />
}
