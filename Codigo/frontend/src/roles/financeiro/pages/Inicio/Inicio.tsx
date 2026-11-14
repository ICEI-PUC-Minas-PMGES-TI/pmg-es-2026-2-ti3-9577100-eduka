import { useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { Atalhos, Colunas, EstadoDashboard } from '../../../../components/layout/Dashboard/Dashboard'
import {
  EntrouESaiu,
  UltimosPagamentos,
  VencemNaSemana,
} from '../../../../components/financeiro/PainelFinanceiro/PainelFinanceiro'
import { AlunosEmRisco } from '../../../../components/financeiro/RiscoAtraso/RiscoAtraso'
import { descricaoInadimplencia, quantasCobrancas } from '../../../../components/financeiro/PainelFinanceiro/textos'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths } from '../../../../routes/paths'
import { obterDashboardFinanceiro } from '../../../../services/dashboard/dashboardService'
import type { DashboardFinanceiro } from '../../../../types/dashboard/dashboard'
import { formatarDiaMes, formatarMoeda, plural } from '../../../../utils/formatters'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'

/** Cobranças já filtradas em "Em atraso". */
const COBRANCAS_EM_ATRASO = `${paths.financeiro.cobrancas}?status=EM_ATRASO`

/** Início do FINANCEIRO: recebido, em aberto, atraso, despesas e as movimentações recentes. */
export function Inicio() {
  const { dados, carregando, erro, recarregar } = useCarregamento<DashboardFinanceiro | null>(
    obterDashboardFinanceiro,
    null,
    'Não foi possível encontrar os dados da escola.',
  )

  return (
    <FinanceiroLayout titulo="Início" subtitulo="O dinheiro da escola: o que entrou, o que falta receber e o que vence.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Resumo dados={dados} />}
    </FinanceiroLayout>
  )
}

function Resumo({ dados }: { dados: DashboardFinanceiro }) {
  const navigate = useNavigate()
  const { recebidoNoMes, faltaReceber, emAtraso, despesasAPagar } = dados

  return (
    <>
      <StatCards rotulo="Resumo financeiro">
        <StatCard
          label="Recebido este mês"
          value={formatarMoeda(recebidoNoMes.valor)}
          descricao={quantasCobrancas(recebidoNoMes.quantidade)}
          destaque
        />
        <StatCard
          label="Falta receber"
          value={formatarMoeda(faltaReceber.valor)}
          descricao={`${quantasCobrancas(faltaReceber.quantidade)} em aberto`}
        />
        <StatCard
          label="Alunos em atraso"
          value={emAtraso.alunos}
          descricao={
            emAtraso.cobrancas > 0
              ? `${formatarMoeda(emAtraso.valor)} · ${descricaoInadimplencia(emAtraso)}`
              : descricaoInadimplencia(emAtraso)
          }
          alerta={emAtraso.alunos > 0}
        />
        <StatCard
          label="Despesas a pagar"
          value={formatarMoeda(despesasAPagar.valor)}
          descricao={
            despesasAPagar.quantidade === 0
              ? 'Nada a pagar este mês'
              : `${despesasAPagar.quantidade} ${plural(despesasAPagar.quantidade, 'despesa', 'despesas')} este mês` +
                (despesasAPagar.proximoVencimento ? ` · próxima em ${formatarDiaMes(despesasAPagar.proximoVencimento)}` : '')
          }
        />
      </StatCards>

      <Atalhos
        titulo="Atalhos do dia"
        horizontal
        itens={[
          { rotulo: 'Nova cobrança', icon: 'plus', onClick: () => navigate(paths.financeiro.novaCobranca) },
          { rotulo: 'Ver cobranças em atraso', icon: 'alert', onClick: () => navigate(COBRANCAS_EM_ATRASO) },
          { rotulo: 'Lançar uma despesa', icon: 'dollarSign', onClick: () => navigate(paths.financeiro.despesas) },
        ]}
      />

      <EntrouESaiu meses={dados.ultimosMeses} />

      <Colunas esticar>
        <VencemNaSemana
          cobrancas={dados.vencemNaSemana}
          totalEmAtraso={emAtraso.cobrancas}
          acao={(aba) => (
            <Button
              size="sm"
              icon="arrowRight"
              onClick={() => navigate(aba === 'EM_ATRASO' ? COBRANCAS_EM_ATRASO : paths.financeiro.cobrancas)}
            >
              Ver cobranças
            </Button>
          )}
        />
        <UltimosPagamentos pagamentos={dados.ultimosPagamentos} />
      </Colunas>

      {/* O atalho "Ver cobranças em atraso" já fica acima: o painel não repete o botão. */}
      <AlunosEmRisco />
    </>
  )
}
