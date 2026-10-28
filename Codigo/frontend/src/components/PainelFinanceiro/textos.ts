import type { DashboardFinanceiro } from '../../types/dashboard'
import { plural } from '../../utils/formatters'

// Textos dos cards do resumo financeiro, comuns ao Início do FINANCEIRO e ao do MASTER.

/** Linha de baixo do card de atraso: a inadimplência, ou que nada venceu. */
export function descricaoInadimplencia(emAtraso: DashboardFinanceiro['emAtraso']): string {
  if (emAtraso.inadimplencia === null) return 'Nada venceu ainda'
  return `${emAtraso.inadimplencia}% de inadimplência`
}

/** "3 cobranças", para a linha de baixo dos cards de total. */
export function quantasCobrancas(quantidade: number): string {
  return `${quantidade} ${plural(quantidade, 'cobrança', 'cobranças')}`
}
