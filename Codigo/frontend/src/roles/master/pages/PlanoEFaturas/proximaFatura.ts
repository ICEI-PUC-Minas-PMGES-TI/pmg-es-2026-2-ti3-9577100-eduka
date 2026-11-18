import type { FaturaPlataforma } from '../../../../types/escola/faturaPlataforma'

/** A fatura em aberto que vence (ou venceu) primeiro: a "próxima" do destaque. */
export function proximaFatura(faturas: FaturaPlataforma[]): FaturaPlataforma | null {
  const abertas = faturas.filter((f) => f.status === 'PENDENTE' || f.status === 'EM_ATRASO')
  return abertas.sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento))[0] ?? null
}

/** Faturas anteriores (IU24-A, campo 5): só pagas ou canceladas, as mais recentes primeiro. */
export function faturasAnteriores(faturas: FaturaPlataforma[]): FaturaPlataforma[] {
  return faturas
    .filter((f) => f.status === 'PAGA' || f.status === 'CANCELADA')
    .sort(
      (a, b) => b.competencia.localeCompare(a.competencia) || b.dataVencimento.localeCompare(a.dataVencimento),
    )
}

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
