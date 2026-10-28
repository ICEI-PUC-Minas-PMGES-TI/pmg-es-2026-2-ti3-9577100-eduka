import type { CategoriaDespesa, Despesa, NovaDespesaRequest, SituacaoDespesa } from '../types/despesa'
import { apiRequest } from './api'

// O backend já limita despesas e categorias à escola de quem está logado.

/** Ordenadas pelo vencimento. */
export function listarDespesas(signal?: AbortSignal): Promise<Despesa[]> {
  return apiRequest<Despesa[]>('/despesas', { signal })
}

export function listarCategoriasDespesa(signal?: AbortSignal): Promise<CategoriaDespesa[]> {
  return apiRequest<CategoriaDespesa[]>('/categorias-despesa', { signal })
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
