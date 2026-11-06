import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

// Menu da gestão (IU05 e IU23). Alunos, matrículas, cobranças e despesas são
// as telas da secretaria e do financeiro, abertas dentro da área do MASTER.
const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.master.inicio },
  { label: 'Alunos e turmas', icon: 'graduationCap', to: paths.master.alunos },
  { label: 'Cobranças', icon: 'card', to: paths.master.cobrancas },
  { label: 'Despesas', icon: 'dollarSign', to: paths.master.despesas },
  { label: 'Relatórios', icon: 'barChart', to: paths.master.relatorios },
  // Em breve: a gestão dos usuários da escola (RF07) ainda não tem tela nem API.
  { label: 'Equipe', icon: 'users' },
  { label: 'Recebimentos', icon: 'settings', to: paths.master.recebimentos },
]

type MasterLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  /** Botão principal da tela, ao lado de "Ajuda" e "Sair". */
  acao?: ReactNode
  children: ReactNode
}

export function MasterLayout(props: MasterLayoutProps) {
  return <PainelLayout contexto="Gestão da escola" menu={MENU} menuRotulo="Navegação da gestão" {...props} />
}
