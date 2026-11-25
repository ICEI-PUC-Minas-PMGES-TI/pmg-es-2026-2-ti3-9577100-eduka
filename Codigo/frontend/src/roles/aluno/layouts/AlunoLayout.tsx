import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/layout/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.aluno.inicio },
  { label: 'Boletim', icon: 'book', to: paths.aluno.boletim },
  { label: 'Pagamentos', icon: 'card', to: paths.aluno.pagamentos, modulo: 'GESTAO_FINANCEIRA' },
  { label: 'Meus dados', icon: 'user', to: paths.aluno.meusDados },
]

// O contato da escola e o suporte do Eduka vêm da própria Central de ajuda;
// aqui ficam só as orientações do portal.
const AJUDA = (
  <>
    <p>
      No <strong>Boletim</strong> ficam suas notas e faltas; em <strong>Pagamentos</strong>, as mensalidades e os
      comprovantes.
    </p>
    <p>
      Viu algum dado seu errado? Em <strong>Meus dados</strong>, use “Pedir correção de dados” e a secretaria corrige.
    </p>
  </>
)

type AlunoLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function AlunoLayout(props: AlunoLayoutProps) {
  return <PainelLayout contexto="Aluno" menu={MENU} menuRotulo="Navegação do portal do aluno" ajuda={AJUDA} {...props} />
}
