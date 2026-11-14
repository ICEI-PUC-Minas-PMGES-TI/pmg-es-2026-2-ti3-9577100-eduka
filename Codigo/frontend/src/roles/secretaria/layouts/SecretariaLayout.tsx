import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/layout/PainelLayout/PainelLayout'
import { useAreaFinanceiro, useAreaMaster } from '../../../routes/areas'
import { paths } from '../../../routes/paths'
import { FinanceiroLayout } from '../../financeiro/layouts/FinanceiroLayout'
import { MasterLayout } from '../../master/layouts/MasterLayout'

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

/**
 * Na área do MASTER (/master/alunos, ...), a mesma tela vem com o menu da
 * gestão; na do FINANCEIRO (/financeiro/alunos), com o menu do financeiro.
 */
export function SecretariaLayout(props: SecretariaLayoutProps) {
  const areaMaster = useAreaMaster()
  const areaFinanceiro = useAreaFinanceiro()
  if (areaMaster) return <MasterLayout {...props} />
  if (areaFinanceiro) return <FinanceiroLayout {...props} />
  return <PainelLayout contexto="Secretaria" menu={MENU} menuRotulo="Navegação da secretaria" {...props} />
}
