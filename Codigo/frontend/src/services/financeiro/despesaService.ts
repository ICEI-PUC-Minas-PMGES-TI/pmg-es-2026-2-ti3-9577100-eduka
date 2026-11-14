import type { PaginaComResumo } from '../../hooks/usePaginacao'
import type { CategoriaDespesa, Despesa, NovaDespesaRequest, ResumoDespesas, SituacaoDespesa } from '../../types/financeiro/despesa'
import { apiRequest } from '../api'

// O backend já limita despesas e categorias à escola de quem está logado.

/** Intervalo de vencimento, AAAA-MM-DD, com as duas pontas. */
export type PeriodoDespesas = { vencimentoDe: string; vencimentoAte: string }

export type ConsultaDespesas = PeriodoDespesas & {
  pagina: number
  tamanho: number
  /** Uma ou mais; sem nenhuma, vêm todas, inclusive as canceladas. */
  situacoes: SituacaoDespesa[]
}

/**
 * Uma página das que vencem no período, ordenadas pelo vencimento, com
 * quantidade e soma de cada situação no período (sem o filtro de situação).
 */
export function listarPaginaDeDespesas(
  consulta: ConsultaDespesas,
  signal?: AbortSignal,
): Promise<PaginaComResumo<Despesa, ResumoDespesas>> {
  const params = new URLSearchParams({
    pagina: String(consulta.pagina),
    tamanho: String(consulta.tamanho),
    vencimentoDe: consulta.vencimentoDe,
    vencimentoAte: consulta.vencimentoAte,
  })
  for (const situacao of consulta.situacoes) params.append('situacao', situacao)
  return apiRequest(`/despesas/pagina?${params}`, { signal })
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
