import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.secretaria.inicio },
  { label: 'Alunos', icon: 'users', to: paths.secretaria.alunos },
  { label: 'Professores', icon: 'graduationCap', to: paths.secretaria.professores },
  // Também fica ativo na alocação, que é subrota do Acadêmico.
  { label: 'Acadêmico', icon: 'book', to: paths.secretaria.academico },
  { label: 'Matrículas', icon: 'check', to: paths.secretaria.matriculas },
]

type SecretariaLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function SecretariaLayout(props: SecretariaLayoutProps) {
  return <PainelLayout contexto="Secretaria" menu={MENU} menuRotulo="Navegação da secretaria" {...props} />
}
