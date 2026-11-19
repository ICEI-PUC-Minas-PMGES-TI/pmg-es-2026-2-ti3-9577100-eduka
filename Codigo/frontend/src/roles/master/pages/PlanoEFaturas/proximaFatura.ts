import type { FaturaPlataforma } from '../../../../types/escola/faturaPlataforma'

/** "2026-11" -> "novembro/2026". */
export function mesPorExtenso(competencia: string): string {
  const [ano, mes] = competencia.split('-').map(Number)
  if (!ano || !mes) return competencia
  const nome = new Date(ano, mes - 1, 1).toLocaleDateString('pt-BR', { month: 'long' })
  return `${nome}/${ano}`
}

/**
 * Referência da fatura: "Plano Avançado · novembro/2026". A descrição lançada
 * pelo Super Admin já costuma trazer o mês; nas que não trazem, ele vem da competência.
 */
export function referenciaDaFatura(fatura: FaturaPlataforma): string {
  const [ano] = fatura.competencia.split('-')
  return ano && fatura.descricao.includes(ano)
    ? fatura.descricao
    : `${fatura.descricao} · ${mesPorExtenso(fatura.competencia)}`
}
