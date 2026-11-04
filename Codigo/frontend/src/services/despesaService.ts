import type { CategoriaDespesa, Despesa, NovaDespesaRequest, SituacaoDespesa } from '../types/despesa'
import { apiRequest } from './api'

// O backend já limita despesas e categorias à escola de quem está logado.

/** Intervalo de vencimento, AAAA-MM-DD, com as duas pontas. */
export type PeriodoDespesas = { vencimentoDe: string; vencimentoAte: string }

/** As que vencem no período, ordenadas pelo vencimento. */
export function listarDespesas(periodo: PeriodoDespesas, signal?: AbortSignal): Promise<Despesa[]> {
  return apiRequest<Despesa[]>(`/despesas?${new URLSearchParams(periodo)}`, { signal })
}

export function listarCategoriasDespesa(signal?: AbortSignal): Promise<CategoriaDespesa[]> {
  return apiRequest<CategoriaDespesa[]>('/categorias-despesa', { signal })
}

/** 409 se a escola já tem categoria com o mesmo nome (sem diferenciar maiúsculas). */
export function criarCategoriaDespesa(nome: string): Promise<CategoriaDespesa> {
  return apiRequest<CategoriaDespesa>('/categorias-despesa', { method: 'POST', body: JSON.stringify({ nome }) })
}

/** 404 se a categoria não é da escola. */
export function criarDespesa(dados: NovaDespesaRequest): Promise<Despesa> {
  return apiRequest<Despesa>('/despesas', { method: 'POST', body: JSON.stringify(dados) })
}

/**
 * A_PAGAR passa para PAGA ou CANCELADA; PAGA volta para A_PAGAR. 400 se a
 * despesa não pode ir para essa situação.
 */
export function mudarSituacaoDespesa(id: string, situacao: SituacaoDespesa): Promise<Despesa> {
  return apiRequest<Despesa>(`/despesas/${id}`, { method: 'PATCH', body: JSON.stringify({ situacao }) })
}
