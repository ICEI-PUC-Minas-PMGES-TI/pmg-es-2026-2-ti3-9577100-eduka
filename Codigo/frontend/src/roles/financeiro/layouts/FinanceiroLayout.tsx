import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.financeiro.inicio },
  { label: 'Cobranças', icon: 'card', to: paths.financeiro.cobrancas },
  { label: 'Despesas', icon: 'dollarSign', to: paths.financeiro.despesas },
  { label: 'Relatórios', icon: 'barChart' },
  { label: 'Alunos', icon: 'users' },
]

type FinanceiroLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  acao?: ReactNode
  children: ReactNode
}

export function FinanceiroLayout({ titulo, subtitulo, acao, children }: FinanceiroLayoutProps) {
  return (
    <PainelLayout
      contexto="[NOME DA ESCOLA] · Financeiro"
      titulo={titulo}
      subtitulo={subtitulo}
      acao={acao}
      menu={MENU}
      menuRotulo="Navegação do financeiro"
      ajuda={
        <div>
          <p>
            No módulo financeiro, você pode consultar o status das cobranças emitidas pela escola,
            acompanhar e lançar despesas mensais, visualizar boletos bancários e gerenciar o fluxo de caixa.
          </p>
          <p style={{ marginTop: '8px' }}>
            Utilize a tela de <strong>Despesas</strong> para lançar contas, salários, materiais e consertos da escola.
          </p>
        </div>
      }
    >
      {children}
    </PainelLayout>
  )
}
