import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/layout/PainelLayout/PainelLayout'
import { useAreaMaster } from '../../../routes/areas'
import { paths } from '../../../routes/paths'
import { MasterLayout } from '../../master/layouts/MasterLayout'

const MENU: ItemMenu[] = [
  { label: 'Início', icon: 'home', to: paths.financeiro.inicio },
  { label: 'Cobranças', icon: 'card', to: paths.financeiro.cobrancas },
  { label: 'Despesas', icon: 'dollarSign', to: paths.financeiro.despesas },
  { label: 'Relatórios', icon: 'barChart', to: paths.financeiro.relatorios },
  { label: 'Alunos', icon: 'users', to: paths.financeiro.alunos },
]

type FinanceiroLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  acao?: ReactNode
  children: ReactNode
}

/** Na área do MASTER (/master/cobrancas, ...), a mesma tela vem com o menu da gestão. */
export function FinanceiroLayout({ titulo, subtitulo, acao, children }: FinanceiroLayoutProps) {
  const areaMaster = useAreaMaster()
  if (areaMaster) {
    return (
      <MasterLayout titulo={titulo} subtitulo={subtitulo} acao={acao}>
        {children}
      </MasterLayout>
    )
  }
  return (
    <PainelLayout
      contexto="Financeiro"
      titulo={titulo}
      subtitulo={subtitulo}
      acao={acao}
      menu={MENU}
      menuRotulo="Navegação do financeiro"
      ajuda={
        <div>
          <p>
            No módulo financeiro, você pode consultar o status das cobranças emitidas pela escola,
            acompanhar e lançar despesas mensais, acompanhar o andamento dos pagamentos pelo Asaas e o fluxo de caixa.
          </p>
          <p style={{ marginTop: '8px' }}>
            Utilize a tela de <strong>Despesas</strong> para lançar contas, salários, materiais e consertos da escola.
          </p>
          <p style={{ marginTop: '8px' }}>
            Em <strong>Relatórios</strong>, escolha o período, confira os números e exporte em CSV.
          </p>
        </div>
      }
    >
      {children}
    </PainelLayout>
  )
}
