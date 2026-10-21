import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.master.inicio },
  { label: 'Recebimentos', icon: 'card', to: paths.master.recebimentos },
]

type MasterLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function MasterLayout(props: MasterLayoutProps) {
  return <PainelLayout contexto="Gestão da escola" menu={MENU} menuRotulo="Navegação da gestão" {...props} />
}
