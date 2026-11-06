import type { DashboardFinanceiro } from '../../types/dashboard'
import { plural } from '../../utils/formatters'

// Textos do financeiro, comuns ao Início do FINANCEIRO, ao do MASTER e aos relatórios.

export const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
const MESES_POR_EXTENSO = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
]

/** "2026-10" -> rótulo curto ("out") e por extenso ("outubro de 2026"). */
export function nomeDoMes(mes: string): { curto: string; extenso: string } {
  const [ano, numero] = mes.split('-').map(Number)
  return { curto: MESES[numero - 1], extenso: `${MESES_POR_EXTENSO[numero - 1]} de ${ano}` }
}

/**
 * Rótulos dos meses de um gráfico: com mais de um ano na tela, o mês curto leva
 * o ano junto ("out/25").
 */
export function rotuladorDeMeses(meses: string[]): (mes: string) => { rotulo: string; titulo: string } {
  const variosAnos = new Set(meses.map((mes) => mes.slice(0, 4))).size > 1
  return (mes) => {
    const nome = nomeDoMes(mes)
    return { rotulo: variosAnos ? `${nome.curto}/${mes.slice(2, 4)}` : nome.curto, titulo: nome.extenso }
  }
}

/** Maior período das consultas financeiras, como o backend aceita (PeriodoFinanceiro). */
const MAXIMO_DE_MESES = 24

/** O que há de errado com o período, para mostrar antes de consultar; null se ele vale. */
export function erroDoPeriodo({ de, ate }: { de: string; ate: string }): string | null {
  const [anoDe, mesDe] = de.split('-').map(Number)
  const [anoAte, mesAte] = ate.split('-').map(Number)
  // Conta os meses de calendário que o período toca, com os das pontas.
  const meses = (anoAte - anoDe) * 12 + (mesAte - mesDe) + 1
  return meses > MAXIMO_DE_MESES ? `Escolha um período de até ${MAXIMO_DE_MESES} meses.` : null
}

/** Linha de baixo do card de atraso: a inadimplência, ou que nada venceu. */
export function descricaoInadimplencia(emAtraso: DashboardFinanceiro['emAtraso']): string {
  if (emAtraso.inadimplencia === null) return 'Nada venceu ainda'
  return `${emAtraso.inadimplencia}% de inadimplência`
}

/** "3 cobranças", para a linha de baixo dos cards de total. */
export function quantasCobrancas(quantidade: number): string {
  return `${quantidade} ${plural(quantidade, 'cobrança', 'cobranças')}`
}
