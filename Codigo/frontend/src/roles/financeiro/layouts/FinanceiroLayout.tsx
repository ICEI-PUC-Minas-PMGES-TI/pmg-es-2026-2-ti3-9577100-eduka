import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home' },
  { label: 'Cobranças', icon: 'card', to: paths.financeiro.cobrancas },
  { label: 'Despesas', icon: 'dollarSign' },
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
            filtrar por situação, visualizar boletos bancários e registrar pagamentos de mensalidades
            e taxas.
          </p>
          <p style={{ marginTop: '8px' }}>
            Para adicionar uma nova mensalidade ou taxa, utilize o botão <strong>+ Nova cobrança</strong>.
          </p>
        </div>
      }
    >
      {children}
    </PainelLayout>
  )
}

