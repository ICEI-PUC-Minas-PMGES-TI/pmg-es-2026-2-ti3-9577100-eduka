import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

// Início e Pagamentos chegam em sprints futuras.
const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home' },
  { label: 'Boletim', icon: 'book', to: paths.aluno.boletim },
  { label: 'Pagamentos', icon: 'card' },
  { label: 'Meus dados', icon: 'user', to: paths.aluno.meusDados },
]

const AJUDA = (
  <p>
    Entre em contato com a secretaria da sua escola pelos canais de atendimento ou procure a coordenação
    pedagógica.
  </p>
)

type AlunoLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function AlunoLayout(props: AlunoLayoutProps) {
  return <PainelLayout contexto="Aluno" menu={MENU} menuRotulo="Navegação do portal do aluno" ajuda={AJUDA} {...props} />
}
