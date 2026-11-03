import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'

/** A fatura em aberto que vence (ou venceu) primeiro: a "próxima" do destaque. */
export function proximaFatura(faturas: FaturaPlataforma[]): FaturaPlataforma | null {
  const abertas = faturas.filter((f) => f.status === 'PENDENTE' || f.status === 'EM_ATRASO')
  return abertas.sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento))[0] ?? null
}
